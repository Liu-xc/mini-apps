package com.leo.libs.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 后端中立字段值（契约核心）：七种封闭值，任何表格型/文档型/对象型后端都能承载。
 * 这是「换后端不动 app 映射」的地基（libs/sync/specs/00-architecture.md §3.1）。
 */
@Serializable
sealed interface SyncValue {

    /** 多行文本；null = 清空 */
    @Serializable
    @SerialName("text")
    data class Text(val value: String?) : SyncValue

    @Serializable
    @SerialName("number")
    data class Number(val value: Double?) : SyncValue

    @Serializable
    @SerialName("bool")
    data class Bool(val value: Boolean) : SyncValue

    /** 日期时间，Unix 毫秒 */
    @Serializable
    @SerialName("instant")
    data class Instant(val millis: Long?) : SyncValue

    /** 单选/多选统一为选项集 */
    @Serializable
    @SerialName("options")
    data class Options(val values: List<String>) : SyncValue

    /** 复杂结构（id 数组、嵌套对象）序列化为 JSON 文本列 */
    @Serializable
    @SerialName("json")
    data class JsonText(val raw: String) : SyncValue

    /**
     * 附件：[ref] 对后端不透明（多维表格=file_token，对象存储=文件 URL）。
     * 上行前 app 以本地名占位（`local:` 前缀），引擎在 push 时经
     * [SyncSource.putAttachment] 换取真实 ref 后替换。
     */
    @Serializable
    @SerialName("attachment")
    data class Attachment(val ref: String) : SyncValue
}

/** 云端实体：id 为 app 侧稳定主键（UUID 或后端收编的记录 id） */
@Serializable
data class SyncEntity(
    val id: String,
    val fields: Map<String, SyncValue>,
    val updatedAt: Long,
)

@Serializable
data class SyncCollection(val name: String)

/** 附件占位 ref 的约定前缀（app 写本地文件名，push 时引擎换真实 ref） */
const val LOCAL_REF_PREFIX = "local:"
