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
        val encoded = when (byKey[key]?.type) {
            ParamType.BOOL -> when (value.lowercase()) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> return@mapNotNull null
            }
            ParamType.INT -> value.toLongOrNull()?.let { JsonPrimitive(it) } ?: return@mapNotNull null
            ParamType.FLOAT -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { JsonPrimitive(it) }
                ?: return@mapNotNull null
            ParamType.ENUM, ParamType.TEXT, null -> JsonPrimitive(value)
        }
        key to encoded
    }.toMap()
}
