package com.leo.libs.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 连接配置：凭证即身份（it-002 调研结论）。params 的键由各后端自定义
 * （feishu-bitable: appId/appSecret/appToken）。
 */
data class SyncConfig(
    val backend: String,
    val params: Map<String, String>,
    val readOnly: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 二维码/链接分发 payload：
         * `{"v":1,"backend":"feishu-bitable","ro":false,"params":{...}}`
         */
        fun parseSharePayload(text: String): SyncConfig? = runCatching {
            val obj = json.parseToJsonElement(text.trim()).jsonObject
            val version = obj["v"]?.jsonPrimitive?.intOrNull ?: error("缺少 v")
            check(version == 1) { "不支持的 payload 版本: $version" }
            val backend = obj["backend"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("缺少 backend")
            val params = (obj["params"] as? JsonObject)
                ?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content }
                ?: emptyMap()
            SyncConfig(
                backend = backend,
                params = params,
                readOnly = obj["ro"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }.getOrNull()

        fun encodeSharePayload(config: SyncConfig): String = JsonObject(
            buildMap {
                put("v", JsonPrimitive(1))
                put("backend", JsonPrimitive(config.backend))
                put("ro", JsonPrimitive(config.readOnly))
                put("params", JsonObject(config.params.mapValues { (_, v) -> JsonPrimitive(v) }))
            },
        ).toString()
    }
}
