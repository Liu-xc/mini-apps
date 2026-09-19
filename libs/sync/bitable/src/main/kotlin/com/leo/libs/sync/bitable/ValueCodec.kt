package com.leo.libs.sync.bitable

import com.leo.libs.sync.SyncValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * SyncValue ↔ 多维表格字段 JSON 的双向转换。
 * 解码按表的字段类型注册表驱动（日期→Instant、数字→Number…），
 * 未知/公式等字段一律折叠为 JsonText（app 映射忽略即可，不炸同步）。
 */
object ValueCodec {

    // 多维表格字段类型（官方固定值）
    const val TYPE_TEXT = 1
    const val TYPE_NUMBER = 2
    const val TYPE_SINGLE_SELECT = 3
    const val TYPE_MULTI_SELECT = 4
    const val TYPE_DATE = 5
    const val TYPE_CHECKBOX = 7
    const val TYPE_ATTACHMENT = 11

    /** 由 SyncValue 推断建表/补列的字段类型（Options 一律建多选，附件占位也按附件列） */
    fun inferFieldType(value: SyncValue): Int = when (value) {
        is SyncValue.Text, is SyncValue.JsonText -> TYPE_TEXT
        is SyncValue.Number -> TYPE_NUMBER
        is SyncValue.Bool -> TYPE_CHECKBOX
        is SyncValue.Instant -> TYPE_DATE
        is SyncValue.Options -> TYPE_MULTI_SELECT
        is SyncValue.Attachment -> TYPE_ATTACHMENT
    }

    /** 编码为记录 fields 的 JSON 值 */
    fun encode(value: SyncValue): JsonElement = when (value) {
        is SyncValue.Text -> JsonPrimitive(value.value ?: "")
        is SyncValue.Number -> value.value?.let { JsonPrimitive(it) } ?: JsonNull
        is SyncValue.Bool -> JsonPrimitive(value.value)
        is SyncValue.Instant -> value.millis?.let { JsonPrimitive(it) } ?: JsonNull
        is SyncValue.Options -> JsonArray(value.values.map { JsonPrimitive(it) })
        is SyncValue.JsonText -> JsonPrimitive(value.raw)
        is SyncValue.Attachment ->
            if (value.ref.startsWith(LOCAL_REF_PREFIX)) JsonArray(emptyList())
            else JsonArray(listOf(JsonObject(mapOf("file_token" to JsonPrimitive(value.ref)))))
    }

    /** 按字段类型解码；无法识别的形态折叠为 JsonText */
    fun decode(fieldType: Int?, element: JsonElement): SyncValue = when (element) {
        is JsonNull -> nullableOf(fieldType)
        is JsonPrimitive -> decodePrimitive(fieldType, element)
        is JsonArray -> decodeArray(fieldType, element)
        is JsonObject -> decodeObject(element)
    }

    private fun nullableOf(fieldType: Int?): SyncValue = when (fieldType) {
        TYPE_NUMBER -> SyncValue.Number(null)
        TYPE_DATE -> SyncValue.Instant(null)
        else -> SyncValue.Text(null)
    }

    private fun decodePrimitive(fieldType: Int?, p: JsonPrimitive): SyncValue = when (fieldType) {
        TYPE_NUMBER -> SyncValue.Number(p.doubleOrNull)
        TYPE_DATE -> SyncValue.Instant(p.longOrNull)
        TYPE_TEXT -> SyncValue.Text(p.content)
        TYPE_SINGLE_SELECT -> SyncValue.Options(listOf(p.content))
        TYPE_MULTI_SELECT -> SyncValue.Options(listOf(p.content))
        TYPE_CHECKBOX -> SyncValue.Bool(p.booleanOrNull ?: false)
        else -> when {
            p.booleanOrNull != null -> SyncValue.Bool(p.booleanOrNull!!)
            p.isString -> SyncValue.Text(p.content)
            else -> SyncValue.JsonText(p.content)
        }
    }

    private fun decodeArray(fieldType: Int?, a: JsonArray): SyncValue {
        val first = a.firstOrNull() ?: return if (fieldType == TYPE_ATTACHMENT) {
            SyncValue.Attachment("")
        } else {
            SyncValue.Options(emptyList())
        }
        return when (first) {
            // 附件列：[{file_token, name, size, url?...}] 取首个 token
            is JsonObject -> decodeObject(first)
            // 多选列：["a","b"]；文本段：[{type:text,text:..}] 已被上分支吃掉
            is JsonPrimitive -> SyncValue.Options(a.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
            else -> SyncValue.JsonText(a.toString())
        }
    }

    private fun decodeObject(o: JsonObject): SyncValue {
        val token = o["file_token"]?.jsonPrimitive?.contentOrNull
        return if (token != null) {
            SyncValue.Attachment(
                ref = token,
                meta = com.leo.libs.sync.AttachmentMeta(
                    name = o["name"]?.jsonPrimitive?.contentOrNull,
                    sizeBytes = o["size"]?.jsonPrimitive?.longOrNull,
                    mimeType = o["mimeType"]?.jsonPrimitive?.contentOrNull,
                ),
            )
        } else {
            SyncValue.JsonText(o.toString())
        }
    }

    /** 文本列的段数组拼接（[{type:"text",text:"a"},...] → "a…"） */
    fun joinTextSegments(a: JsonArray): String =
        a.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("")

    const val LOCAL_REF_PREFIX = "local:"
}
