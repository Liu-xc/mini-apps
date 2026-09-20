package com.leo.libs.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 同步状态（UI 渲染依据） */
sealed interface SyncState {
    data object Disconnected : SyncState
    data object Idle : SyncState
    data class Working(val phase: Phase) : SyncState
    data class Done(val pulled: Int, val pushed: Int, val deleted: Int, val rejected: Int) : SyncState
    data class Failed(val error: SyncError, val retryable: Boolean) : SyncState

    enum class Phase { CONNECT, PUSH, PULL }
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
 * 冲突 = 记录级 LWW（比 updatedAt，几乎无并行编辑的前提）；删除 = 物理删除 + 对账传播。
 *
 * 失败语义：单条记录失败计入 PushSummary.failed / PullSummary.rejected，不中断批次；
 * 传输级失败（网络/鉴权）抛出并落入 state=Failed。
 */
class SyncEngine(
    private val source: SyncSource,
    private val adapters: List<CollectionAdapter>,
    private val queue: FilePendingOpQueue,
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Disconnected)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    var connected: Boolean = false
        private set

    /** 测试连通 + 确保集合结构；失败设 state=Failed 并抛出 */
    suspend fun connect(config: SyncConfig) {
        _state.value = SyncState.Working(SyncState.Phase.CONNECT)
        try {
            source.connect(config, adapters.map { it.collection })
            connected = true
            _state.value = SyncState.Idle
        } catch (e: Exception) {
            val err = e.toSyncError()
            _state.value = SyncState.Failed(err, retryable = err.isRetryable())
            throw e
        }
    }

    /**
     * 上行：待推队列 → 附件上传（换真实 ref，单条失败跳过不中断）→ 分批 push →
     * 成功确认（ack + onPushed），失败保留队列。
     */
    suspend fun push(): PushSummary = guard(SyncState.Phase.PUSH) {
        checkConnected()
        var pushed = 0
        val failed = mutableMapOf<String, SyncError>()
        for (adapter in adapters) {
            val ops = queue.all().filter { it.collection == adapter.collection.name }
            if (ops.isEmpty()) continue
            val upserts = ops.filterIsInstance<SyncOp.Upsert>()
            val removes = ops.filterIsInstance<SyncOp.Remove>()

            // 附件占位 → 真实 ref；单条失败记入 failed，不中断其余
            val resolved = mutableListOf<SyncEntity>()
            for (op in upserts) {
                val outcome = runCatching { resolveAttachments(adapter, op.entity) }
                outcome.fold(
                    onSuccess = { resolved += it },
                    onFailure = { failed[op.entityId] = it.toSyncError() },
                )
            }

            val appliedIds = mutableListOf<String>()
            val appliedEntities = mutableListOf<SyncEntity>()
            resolved.chunked(PUSH_BATCH).forEach { batch ->
                val result = source.push(adapter.collection, batch, emptyList())
                pushed += result.applied.size
                failed += result.failed
                appliedIds += result.applied
                appliedEntities += batch.filter { it.id in result.applied }
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
        PushSummary(pushed = pushed, failed = failed)
    }

    /**
     * 下行：全量拉取 + 对账。
     * upserts = 云端新增/较新（剔除本地待推且更新的）；
     * deletes = 本地存在、云端消失、且不在待推队列的（含他人删除传播）。
     * 待推实体与云端同 id 冲突时 LWW：本地赢→保留待推；云端赢→弃队列取云端。
     */
    suspend fun pull(): PullSummary = guard(SyncState.Phase.PULL) {
        checkConnected()
        var pulled = 0
        var deleted = 0
        val rejected = mutableListOf<Rejected>()
        val localVersions = adapters.associate { it.collection.name to it.localVersions() }

        for (adapter in adapters) {
            val remote = source.pull(adapter.collection)
            val local = localVersions.getValue(adapter.collection.name)

            val pendingById = queue.upsertsOf(adapter.collection.name).associateBy { it.entityId }
            val remoteById = remote.entities.associateBy { it.id }

            // 冲突裁决：待推 vs 云端（LWW：updatedAt 大者胜）
            val dropOps = mutableListOf<SyncOp>()
            val cloudWinIds = mutableSetOf<String>()
            for ((id, op) in pendingById) {
                val cloud = remoteById[id] ?: continue
                if (cloud.updatedAt > op.entity.updatedAt) {
                    dropOps += op
                    cloudWinIds += id
                }
            }
            if (dropOps.isNotEmpty()) queue.acknowledge(dropOps)

            // 云端待应用：新实体 / 云端较新 / 冲突中云端胜出
            val pendingIds = pendingById.keys
            val upserts = remote.entities.filter { e ->
                e.id in cloudWinIds ||
                    (e.id !in pendingIds && (local[e.id] == null || e.updatedAt > local.getValue(e.id)))
            }
            // 删除对账：本地有、云端无、且不是待推的新建
            val deletes = (local.keys - remoteById.keys - pendingIds).toList()

            rejected += adapter.applyCloud(upserts, deletes)
            pulled += upserts.size
            deleted += deletes.size
        }
        PullSummary(pulled = pulled, deleted = deleted, rejected = rejected)
    }

    /** push/pull 正常结束落 Done、异常落 Failed 再抛出 */
    private suspend fun <T> guard(phase: SyncState.Phase, block: suspend () -> T): T = try {
        _state.value = SyncState.Working(phase)
        val result = block()
        _state.value = when (result) {
            is PushSummary -> SyncState.Done(pulled = 0, pushed = result.pushed, deleted = 0, rejected = result.failed.size)
            is PullSummary -> SyncState.Done(result.pulled, 0, result.deleted, result.rejected.size)
            else -> SyncState.Done(0, 0, 0, 0)
        }
        result
    } catch (e: Exception) {
        val err = e.toSyncError()
        _state.value = SyncState.Failed(err, retryable = err.isRetryable())
        throw e
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

    private fun Throwable.toSyncError(): SyncError = when (this) {
        is SyncException -> error
        else -> SyncError.Network(message ?: "网络错误", this)
    }

    private fun SyncError.isRetryable(): Boolean =
        this is SyncError.Network || this is SyncError.RateLimited

    companion object {
        const val PUSH_BATCH = 100
    }
}
