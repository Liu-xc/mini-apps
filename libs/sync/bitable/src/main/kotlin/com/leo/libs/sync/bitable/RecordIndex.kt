package com.leo.libs.sync.bitable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * entityId ↔ recordId 持久映射（适配器私有，不外泄到契约层）。
 * 家人在飞书 UI 手建的行没有 id 列 → 首次 pull 时收编 recordId 为实体 id 并登记于此。
 */
class RecordIndex(private val file: File?) {

    private val mutex = Mutex()
    // collection → (entityId → recordId)
    private val map: MutableMap<String, MutableMap<String, String>> = load()

    fun recordIdOf(collection: String, entityId: String): String? =
        map[collection]?.get(entityId)

    suspend fun put(collection: String, entityId: String, recordId: String) {
        mutex.withLock {
            map.getOrPut(collection) { mutableMapOf() }[entityId] = recordId
            persist()
        }
    }

    private fun load(): MutableMap<String, MutableMap<String, String>> {
        val f = file ?: return mutableMapOf()
        val parsed = runCatching { Json.parseToJsonElement(f.readText()).jsonObject }.getOrNull()
            ?: return mutableMapOf()
        return parsed.entries.associate { (collection, inner) ->
            val innerMap = (inner as? JsonObject)?.entries?.associate { (id, rid) ->
                id to ((rid as? JsonPrimitive)?.content ?: rid.toString())
            } ?: emptyMap()
            collection to innerMap.toMutableMap()
        }.toMutableMap()
    }

    private suspend fun persist() {
        val f = file ?: return
        withContext(Dispatchers.IO) {
            f.parentFile?.mkdirs()
            val obj = JsonObject(
                map.mapValues { (_, inner) -> JsonObject(inner.mapValues { (_, rid) -> JsonPrimitive(rid) }) },
            )
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(f)) throw java.io.IOException("record index 原子替换失败")
        }
    }
}
