package com.leo.libs.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 待推操作：app 在本地写成功后经 writeHook 登记。同一 (collection, entityId)
 * 的新操作覆盖旧操作（轻同步下的队列合并语义）：
 * Upsert→Upsert 取新；Upsert→Remove 变 Remove；Remove→Upsert 复活为 Upsert。
 */
@Serializable
sealed interface SyncOp {
    val collection: String
    val entityId: String

    @Serializable
    data class Upsert(
        override val collection: String,
        override val entityId: String,
        val entity: SyncEntity,
    ) : SyncOp

    @Serializable
    data class Remove(
        override val collection: String,
        override val entityId: String,
    ) : SyncOp
}

interface PendingOpQueue {
    suspend fun enqueue(op: SyncOp)
    suspend fun all(): List<SyncOp>
    suspend fun upsertsOf(collection: String): List<SyncOp.Upsert>
    /** 移除指定操作（成功确认 / 冲突放弃）；按 (collection, entityId) 匹配 */
    suspend fun acknowledge(ops: List<SyncOp>)
    suspend fun clear()
}

/** JSON 文件持久化实现：原子写（tmp→rename），崩溃安全 */
class FilePendingOpQueue(private val file: File) : PendingOpQueue {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SyncOp.serializer())
    private val mutex = Mutex()

    private suspend fun <R> locked(block: () -> R): R = mutex.withLock {
        withContext(Dispatchers.IO) { block() }
    }

    private fun readDisk(): List<SyncOp> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() ?: emptyList()

    private fun writeDisk(ops: List<SyncOp>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, ops))
        if (!tmp.renameTo(file)) java.io.IOException("pending queue 原子替换失败").let { throw it }
    }

    private suspend fun mutate(transform: (List<SyncOp>) -> List<SyncOp>) = locked {
        val next = transform(readDisk())
        writeDisk(next)
        next
    }

    override suspend fun enqueue(op: SyncOp) {
        mutate { ops ->
            val rest = ops.filterNot { it.collection == op.collection && it.entityId == op.entityId }
            rest + op
        }
    }

    override suspend fun all(): List<SyncOp> = locked { readDisk() }

    override suspend fun upsertsOf(collection: String): List<SyncOp.Upsert> =
        all().filterIsInstance<SyncOp.Upsert>().filter { it.collection == collection }

    override suspend fun acknowledge(ops: List<SyncOp>) {
        if (ops.isEmpty()) return
        val keys = ops.map { it.collection to it.entityId }.toSet()
        mutate { list -> list.filterNot { (it.collection to it.entityId) in keys } }
    }

    override suspend fun clear() {
        mutate { emptyList() }
    }
}
