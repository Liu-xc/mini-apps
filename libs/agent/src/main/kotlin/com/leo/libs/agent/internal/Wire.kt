package com.leo.libs.agent.internal

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ChatCompletion
import com.leo.libs.agent.Message
import com.leo.libs.agent.Part
import com.leo.libs.agent.Role
import com.leo.libs.agent.ToolCall
import com.leo.libs.agent.ToolSpec
import com.leo.libs.agent.Usage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 未知字段一律忽略不崩（island M0 教训：解析收紧但容错） */
internal val wireJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

// ---------- 请求 ----------

@Serializable
internal data class WireChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    val tools: List<WireTool>? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

@Serializable
internal data class WireMessage(
    val role: String,
    /** null = 不带 content（assistant tool_calls 回喂场景）；字符串或 parts 数组两种形态 */
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class WireTool(
    val type: String = "function",
    val function: WireFunctionDef,
)

@Serializable
internal data class WireFunctionDef(val name: String, val description: String, val parameters: JsonElement)

@Serializable
internal data class WireToolCall(
    val id: String,
    val type: String = "function",
    val function: WireFunctionCall,
)

@Serializable
internal data class WireFunctionCall(val name: String, val arguments: String)

// ---------- 非流式响应 ----------

@Serializable
internal data class WireChatResponse(
    val id: String? = null,
    val choices: List<WireChoice> = emptyList(),
    val usage: WireUsage? = null,
)

@Serializable
internal data class WireChoice(
    val index: Int = 0,
    val message: WireMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class WireUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
) {
    fun toDomain() = Usage(promptTokens, completionTokens, totalTokens)
}

// ---------- 流式 chunk ----------

@Serializable
internal data class WireStreamChunk(
    val choices: List<WireStreamChoice> = emptyList(),
    val usage: WireUsage? = null,
)

@Serializable
internal data class WireStreamChoice(
    val index: Int = 0,
    val delta: WireDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class WireDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireDeltaToolCall>? = null,
)

@Serializable
internal data class WireDeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: WireDeltaFunction? = null,
)

@Serializable
internal data class WireDeltaFunction(val name: String? = null, val arguments: String? = null)

// ---------- 映射 ----------

internal fun Message.toWire(): WireMessage {
    val content: JsonElement? = when {
        parts.isEmpty() -> null
        parts.all { it is Part.Text } -> JsonPrimitive(text)
        else -> buildJsonArray {
            parts.forEach { part ->
                add(
                    when (part) {
                        is Part.Text -> buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        }
                        is Part.ImageUrl -> buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", part.url) })
                        }
                    }
                )
            }
        }
    }
    return WireMessage(
        role = role.toWire(),
        content = content,
        toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map {
            WireToolCall(it.id, function = WireFunctionCall(it.name, it.argumentsJson))
        },
        toolCallId = toolCallId,
    )
}

internal fun Role.toWire(): String = when (this) {
    Role.System -> "system"
    Role.User -> "user"
    Role.Assistant -> "assistant"
    Role.Tool -> "tool"
}

internal fun roleFromWire(raw: String): Role = when (raw) {
    "system" -> Role.System
    "assistant" -> Role.Assistant
    "tool" -> Role.Tool
    else -> Role.User
}

internal fun WireMessage.toDomain(): Message = Message(
    role = roleFromWire(role),
    parts = content.toParts(),
    toolCalls = toolCalls.orEmpty().map { ToolCall(it.id, it.function.name, it.function.arguments) },
    toolCallId = toolCallId,
)

internal fun JsonElement?.toParts(): List<Part> = when (this) {
    null -> emptyList()
    is JsonPrimitive -> listOf(Part.Text(content))
    is JsonArray -> map { el ->
        val obj = el.jsonObject
        if (obj.containsKey("image_url")) {
            Part.ImageUrl(obj.getValue("image_url").jsonObject["url"]?.jsonPrimitive?.contentOrNull.orEmpty())
        } else {
            Part.Text(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }
    else -> emptyList()
}

internal fun ToolSpec.toWire() = WireTool(function = WireFunctionDef(name, description, parametersSchema))

internal fun WireChatResponse.toCompletion(): ChatCompletion {
    val choice = choices.firstOrNull() ?: throw AgentError.Provider(-1, "响应无 choices")
    val message = choice.message?.toDomain() ?: throw AgentError.Provider(-1, "响应无 message")
    return ChatCompletion(message, choice.finishReason, usage?.toDomain() ?: Usage())
}
