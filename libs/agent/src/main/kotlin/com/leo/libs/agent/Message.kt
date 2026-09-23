package com.leo.libs.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    @SerialName("system") System,
    @SerialName("user") User,
    @SerialName("assistant") Assistant,
    @SerialName("tool") Tool,
}

/** 消息内容片段；纯文本序列化为字符串，带图时序列化为 parts 数组（OpenAI 视觉格式） */
@Serializable
sealed class Part {
    @Serializable @SerialName("text") data class Text(val text: String) : Part()
    @Serializable @SerialName("image_url") data class ImageUrl(val url: String) : Part()
}

/** 模型发起的一次工具调用（assistant 消息携带） */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    /** JSON 字符串，形如 {"canteen":"第一食堂"}；空参约定为 "{}" */
    val argumentsJson: String,
)

@Serializable
data class Message(
    val role: Role,
    val parts: List<Part> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    /** role=tool 时对应的调用 id */
    val toolCallId: String? = null,
) {
    /** 纯文本视图（拼接全部 Text 片段） */
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString("") { it.text }

    companion object {
        fun system(text: String) = Message(Role.System, listOf(Part.Text(text)))
        fun user(text: String) = Message(Role.User, listOf(Part.Text(text)))
        fun user(text: String, imageUrls: List<String>) =
            Message(Role.User, listOf(Part.Text(text)) + imageUrls.map { Part.ImageUrl(it) })
        fun assistant(text: String) = Message(Role.Assistant, listOf(Part.Text(text)))
        fun assistantToolCalls(toolCalls: List<ToolCall>) =
            Message(Role.Assistant, toolCalls = toolCalls)
        fun toolResult(callId: String, text: String) =
            Message(Role.Tool, listOf(Part.Text(text)), toolCallId = callId)
    }
}
