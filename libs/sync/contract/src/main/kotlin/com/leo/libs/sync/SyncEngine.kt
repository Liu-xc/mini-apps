package com.leo.libs.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 同步状态（UI 渲染依据） */
sealed interface SyncState {
    data object Disconnected : SyncState
    data object Idle : SyncState
    data class Working(val phase: Phase, val progress: Int, val total: Int) : SyncState
    data class Done(val at: Long, val pulled: Int, val pushed: Int, val rejected: Int) : SyncState
    data class Failed(val error: SyncError, val retryable: Boolean) : SyncState

    enum class Phase { CONNECT, PUSH, PULL }
}

data class SyncPolicy(
    val pushBatchSize: Int = 100,
    /** 冲突裁决：本地待推 vs 云端更新同时存在时谁赢（默认 LWW：比 updatedAt） */
    val conflict: ConflictPolicy = ConflictPolicy.LastWriteWins,
)

interface ConflictPolicy {
    /** 返回应保留的一方（赢者将被推送/保留，输者被放弃） */
    fun resolve(localPending: SyncEntity, remote: SyncEntity): SyncEntity

    object LastWriteWins : ConflictPolicy {
        override fun resolve(localPending: SyncEntity, remote: SyncEntity): SyncEntity =
            if (remote.updatedAt > localPending.updatedAt) remote else localPending
    }
}

data class PullSummary(
    val pulled: Int = 0,
    val deleted: Int = 0,
    val rejected: List<Rejected> = emptyList(),
)

data class PushSummary(
    val pushed: Int = 0,
    val failed: Map<String, SyncError> = emptyMap(),
)

/**
 * 轻同步引擎（后端无关，契约层核心）：
 * 云端为正本、本地为缓存；push = 待推队列上行；pull = 全量拉取 + 对账；
 * 冲突 = 记录级 LWW；删除 = 物理删除 + 对账传播。实时推送不在 v1 范围。
 */
class SyncEngine(
    private val source: SyncSource,
    private val adapters: List<CollectionAdapter>,
    private val queue: PendingOpQueue,
    private val policy: SyncPolicy = SyncPolicy(),
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Disconnected)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    var connected: Boolean = false
        private set

    suspend fun connect(config: SyncConfig): Result<Unit> = runCatchingState {
        _state.value = SyncState.Working(SyncState.Phase.CONNECT, 0, adapters.size)
        source.connect(config, adapters.map { it.collection })
        connected = true
        _state.value = SyncState.Idle
    }
    /**
     * 上行：待推队列 → 附件上传（换真实 ref）→ 分批 push →
     * 成功确认（ack + onPushed），失败保留队列。
     */
    suspend fun push(): PushSummary {
        checkConnected()
        var pushed = 0
        val failed = mutableMapOf<String, SyncError>()
        for (adapter in adapters) {
            val ops = queue.all().filter { it.collection == adapter.collection.name }
            if (ops.isEmpty()) continue
            val upserts = ops.filterIsInstance<SyncOp.Upsert>()
            val removes = ops.filterIsInstance<SyncOp.Remove>()

            // 附件占位 → 真实 ref
            val resolved = upserts.map { op -> resolveAttachments(adapter, op.entity) }

            _state.value = SyncState.Working(SyncState.Phase.PUSH, 0, resolved.size + removes.size)
            val appliedIds = mutableListOf<String>()
            val appliedEntities = mutableListOf<SyncEntity>()
            resolved.chunked(policy.pushBatchSize).forEach { batch ->
                val result = source.push(adapter.collection, batch, emptyList())
                pushed += result.applied.size
                failed += result.failed
                appliedIds += result.applied
                appliedEntities += batch.filter { it.id in result.applied }
                _state.value = SyncState.Working(SyncState.Phase.PUSH, appliedIds.size, resolved.size + removes.size)
            }
            if (removes.isNotEmpty()) {
                val result = source.push(adapter.collection, emptyList(), removes.map { it.entityId })
                pushed += result.applied.size
                failed += result.failed
                appliedIds += result.applied
            }
            queue.acknowledge(ops.filter { it.entityId in appliedIds })
            if (appliedEntities.isNotEmpty()) adapter.onPushed(appliedEntities)
        }
        return PushSummary(pushed = pushed, failed = failed)
    }

    /**
     * 下行：全量拉取 + 对账。
     * upserts = 云端新增/较新（剔除本地待推且更 old 的）；
     * deletes = 本地存在、云端消失、且不在待推队列的（含他人删除传播）。
     * 待推实体与云端同 id 冲突时按 [ConflictPolicy] 裁决：本地赢→保留待推；云端赢→弃队列取云端。
     */
    suspend fun pull(): PullSummary {
        checkConnected()
        var pulled = 0
        var deleted = 0
        val rejected = mutableListOf<Rejected>()
        for ((index, adapter) in adapters.withIndex()) {
            _state.value = SyncState.Working(SyncState.Phase.PULL, index, adapters.size)
            val remote = source.pull(adapter.collection)
            rejected += remote.rejected.map { Rejected(it.recordId, it.reason) }

            val pendingById = queue.upsertsOf(adapter.collection.name).associateBy { it.entityId }
            val remoteById = remote.entities.associateBy { it.id }

            // 冲突裁决：待推 vs 云端
            val dropOps = mutableListOf<SyncOp>()
            val conflictCloudWins = mutableListOf<SyncEntity>()
            for ((id, op) in pendingById) {
                val cloud = remoteById[id] ?: continue
                val winner = policy.conflict.resolve(op.entity, cloud)
                if (winner === cloud) {
                    dropOps += op
                    conflictCloudWins += cloud
                }
            }
            if (dropOps.isNotEmpty()) queue.acknowledge(dropOps)

            // 云端待应用：新实体 / 云端较新 / 冲突中云端胜出
            val cloudWinIds = conflictCloudWins.map { it.id }.toSet()
            val pendingIds = pendingById.keys
            val upserts = remote.entities.filter { e ->
                e.id in cloudWinIds ||
                    (e.id !in pendingIds && run {
                        val local = adapter.localUpdatedAt(e.id)
                        local == null || e.updatedAt > local
                    })
            }
            // 删除对账：本地有、云端无、且不是待推的新建
            val remoteIds = remoteById.keys
            val deletes = (adapter.localIds() - remoteIds - pendingIds).toList()

            val applyRejected = adapter.applyCloud(upserts, deletes)
            rejected += applyRejected
            pulled += upserts.size
            deleted += deletes.size
        }
        return PullSummary(pulled = pulled, deleted = deleted, rejected = rejected)
    }

    private suspend fun resolveAttachments(
        adapter: CollectionAdapter,
        entity: SyncEntity,
    ): SyncEntity {
        val pending = adapter.attachmentsFor(entity)
        if (pending.isEmpty()) return entity
        var fields = entity.fields
        for (slot in pending) {
            val existing = fields[slot.fieldName] as? SyncValue.Attachment
            val alreadyUploaded = existing != null && !existing.ref.startsWith(LOCAL_REF_PREFIX)
            if (alreadyUploaded) continue
            val bytes = runCatching { slot.bytes() }.getOrNull() ?: continue
            val attachment = source.putAttachment(slot.fileName, bytes)
            fields = fields + (slot.fieldName to attachment)
        }
        return entity.copy(fields = fields)
    }

    private fun checkConnected() {
        check(connected) { "尚未 connect；请先 connect(config)" }
    }

    private suspend fun runCatchingState(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: SyncException) {
        _state.value = SyncState.Failed(e.error, retryable = e.error.isRetryable())
        Result.failure(e)
    } catch (e: Exception) {
        val err = SyncError.Network(e.message ?: "网络错误", e)
        _state.value = SyncState.Failed(err, retryable = true)
        Result.failure(e)
    }

    private fun SyncError.isRetryable(): Boolean =
        this is SyncError.Network || this is SyncError.RateLimited

    companion object {
        const val LOCAL_REF_PREFIX = "local:"
    }
}
