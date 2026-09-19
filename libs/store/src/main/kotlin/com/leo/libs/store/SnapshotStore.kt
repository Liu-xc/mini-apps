package com.leo.libs.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * 快照式 JSON 持久化（mini-apps store SDK）：
 * 原子写（tmp→rename）+ 上一成功版本 .bak + 三级恢复（主文件 → bak → default）+ 逐版本迁移链。
 *
 * 语义与 wardrobe it-001 的 JsonFileStore 完全一致（specs 见 libs/store/specs/00-architecture.md），
 * 通用化为任意快照根类型。仅依赖 JVM（java.io），Android API 26+ 可直接使用。
 */
class SnapshotStore<T : Any>(
    private val dir: File,
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    private val versionOf: (T) -> Int = { 1 },
    private val migrations: List<Migration<T>> = emptyList(),
    private val expectedVersion: Int = 1,
) {

    data class Migration<T>(val fromVersion: Int, val transform: (T) -> T)

    /** 载入结果：数据来自哪里、是否经过了迁移 */
    sealed interface LoadOutcome<out T> {
        data class Loaded<T>(
            val data: T,
            val source: Source,
            val migratedFrom: Int? = null,
        ) : LoadOutcome<T>

        enum class Source { MAIN, BAK }
        data object DefaultUsed : LoadOutcome<Nothing>
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val file: File get() = File(dir, fileName)
    val bakFile: File get() = File(dir, "$fileName.bak")
    private val tmpFile: File get() = File(dir, "$fileName.tmp")

    private val mutex = Mutex()

    /** 载入快照（同步读；rename 原子性保证与并发 commit 不冲突）；主文件与 bak 均不可读时返回 [default] */
    fun load(): T = loadDetailed().let { outcome ->
        when (outcome) {
            is LoadOutcome.Loaded -> outcome.data
            LoadOutcome.DefaultUsed -> default()
        }
    }

    fun loadDetailed(): LoadOutcome<T> {
        dir.mkdirs()
        return read(file)?.let { LoadOutcome.Loaded(it.data, LoadOutcome.Source.MAIN, it.migratedFrom) }
            ?: read(bakFile)?.let { LoadOutcome.Loaded(it.data, LoadOutcome.Source.BAK, it.migratedFrom) }
            ?: LoadOutcome.DefaultUsed
    }

    /** 原子提交：tmp→rename；成功后保证 bak 存在（上一成功版本或本版本） */
    suspend fun commit(next: T) {
        mutex.withLock { writeAtomic(next) }
    }

    /** 解码外部字节（备份导入用），不落盘 */
    fun decode(bytes: ByteArray): T? = runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }
        .getOrNull()
        ?.let(::migrate)

    private suspend fun writeAtomic(next: T) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(serializer, next)
        dir.mkdirs()
        tmpFile.writeText(payload)
        if (file.exists()) file.copyTo(bakFile, overwrite = true)
        if (!tmpFile.renameTo(file)) throw IOException("$fileName 原子替换失败")
        // 首次提交也补一份 bak，保证任意时刻都有可恢复副本
        if (!bakFile.exists()) file.copyTo(bakFile, overwrite = true)
    }

    private class ReadResult<T>(val data: T, val migratedFrom: Int?)

    private fun read(f: File): ReadResult<T>? {
        val raw = runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull() ?: return null
        val before = versionOf(raw)
        val migrated = migrate(raw)
        return ReadResult(migrated, if (versionOf(migrated) != before) before else null)
    }

    /** 逐版本迁移：从数据当前版本沿链升到 [expectedVersion]；高于时原样返回（前向兼容，配合 ignoreUnknownKeys） */
    private fun migrate(data: T): T {
        var current = data
        var version = versionOf(current)
        if (version >= expectedVersion) return current
        while (version < expectedVersion) {
            val step = migrations.firstOrNull { it.fromVersion == version } ?: return current
            current = step.transform(current)
            val next = versionOf(current)
            if (next <= version) return current // 防御：迁移未推进版本则终止，避免死循环
            version = next
        }
        return current
    }
}
