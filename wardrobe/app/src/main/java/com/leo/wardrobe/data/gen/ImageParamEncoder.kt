package com.leo.wardrobe.data.gen

import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ParamType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Keep declared boolean and numeric model parameters as JSON primitives of the declared type. */
internal fun encodeImageParams(
    specs: List<ImageParamSpec>,
    values: Map<String, String>,
): Map<String, JsonElement> {
    val byKey = specs.associateBy(ImageParamSpec::key)
    return values.mapNotNull { (key, value) ->
        val spec = byKey[key]
        val encoded: JsonPrimitive? = when (spec?.type) {
            ParamType.BOOL -> when (value.lowercase()) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> null
            }
            ParamType.INT -> value.toLongOrNull()?.let { JsonPrimitive(it) }
            ParamType.FLOAT -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { JsonPrimitive(it) }
            ParamType.ENUM, ParamType.TEXT, null -> JsonPrimitive(value)
        }
        // it-082：INT 值低于声明 min → 省略字段交服务端自选（如 Kolors seed=-1 表随机，
        // SiliconFlow 要求 ≥0，原样发送即 400 code 20015）；未声明 min 的模型（DashScope 系
        // -1=官方随机语义）不受影响，原样透传
        val intBelowMin = spec?.type == ParamType.INT && spec.min != null &&
            encoded?.longOrNull != null && encoded.longOrNull!! < spec.min!!
        when {
            intBelowMin -> null
            encoded != null -> key to encoded
            spec != null -> key to spec.default
            else -> null
        }
    }.toMap()
}
