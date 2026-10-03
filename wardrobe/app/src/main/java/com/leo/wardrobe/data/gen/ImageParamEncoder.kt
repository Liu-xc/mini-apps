package com.leo.wardrobe.data.gen

import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ParamType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
        // it-081/A-13：非法值回退声明默认值，不再静默丢参——缺 size 等必填参数
        // 只会在服务端变 400 才暴露；未声明的 key（spec=null）维持原样字符串透传
        when {
            encoded != null -> key to encoded
            spec != null -> key to spec.default
            else -> null
        }
    }.toMap()
}
