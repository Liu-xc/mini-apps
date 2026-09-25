package com.leo.libs.agent.usage

import com.leo.libs.agent.Usage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 用量记账（it-002 §4，架构 §9）：按 厂商×模型 累计；SDK 不内置价格表。
 */
interface UsageLedger {
    suspend fun record(presetId: String, model: String, usage: Usage)
    suspend fun totals(): Map<Pair<String, String>, Usage>
}

class InMemoryUsageLedger : UsageLedger {
    private val map = LinkedHashMap<String, Usage>()

    override suspend fun record(presetId: String, model: String, usage: Usage) {
        val key = key(presetId, model)
        map[key] = (map[key] ?: Usage()) + usage
    }

    override suspend fun totals(): Map<Pair<String, String>, Usage> =
        map.entries.associate { (k, v) -> unkey(k) to v }

    companion object {
        internal fun key(presetId: String, model: String) = "$presetId|$model"
        internal fun unkey(key: String): Pair<String, String> {
            val i = key.indexOf('|')
            return if (i < 0) key to "" else key.substring(0, i) to key.substring(i + 1)
        }
    }
}

/** 文件实现：`<dir>/usage.json`，{ "preset|model": {prompt,completion,total} }，tmp → 原子落盘 */
class FileUsageLedger(private val file: File) : UsageLedger {

    private val mutex = Mutex()

    override suspend fun record(presetId: String, model: String, usage: Usage) = mutex.withLock {
        val map = readLocked().toMutableMap()
        val key = InMemoryUsageLedger.key(presetId, model)
        map[key] = (map[key] ?: Usage()) + usage
        writeLocked(map)
    }

    override suspend fun totals(): Map<Pair<String, String>, Usage> = mutex.withLock {
        readLocked().mapKeys { InMemoryUsageLedger.unkey(it.key) }
    }

    private fun readLocked(): Map<String, Usage> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(file.readText()).jsonObject
                .mapValues { (k, v) -> decodeUsage(v.jsonObject) }
        }.getOrElse { emptyMap() }
    }

    private fun writeLocked(map: Map<String, Usage>) {
        file.parentFile?.mkdirs()
        val text = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(map.mapValues { (_, u) ->
                JsonObject(
                    mapOf(
                        "prompt" to JsonPrimitive(u.promptTokens),
                        "completion" to JsonPrimitive(u.completionTokens),
                        "total" to JsonPrimitive(u.totalTokens),
                    )
                )
            }.toMap()),
        )
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    private fun decodeUsage(obj: JsonObject) = Usage(
        promptTokens = obj["prompt"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        completionTokens = obj["completion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        totalTokens = obj["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
    )
}
