package com.leo.libs.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/** 工具定义（parameters 为 JSON Schema，透传厂商） */
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersSchema: JsonElement,
)

data class ChatRequest(
    val messages: List<Message>,
    /** 覆盖 preset 默认模型（默认取 preset.models 首个） */
    val model: String? = null,
    val tools: List<ToolSpec> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

data class ChatCompletion(
    val message: Message,
    val finishReason: String?,
    val usage: Usage,
)

/**
 * 传输层事件流。终态只有两种：发出 [ChatEvent.Completed] 后正常结束；
 * 或 Flow 以异常失败（[AgentError] / CancellationException）。
 */
sealed interface ChatEvent {
    data class TextDelta(val text: String) : ChatEvent

    /** 推理过程增量（厂商 reasoning_content 字段）；仅供 UI 展示，不回喂进历史 */
    data class ThinkingDelta(val text: String) : ChatEvent

    /** 工具调用流式分片：id/name 只在首片出现，arguments 为增量拼接 */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String,
    ) : ChatEvent

    data class Completed(val completion: ChatCompletion) : ChatEvent
}

/**
 * 模型传输抽象（ADR-002）：唯一实现为 OpenAI 兼容协议，厂商差异全部在 [preset] 数据里。
 */
interface ChatModel {
    val preset: ProviderPreset
    suspend fun complete(request: ChatRequest): ChatCompletion
    fun stream(request: ChatRequest): Flow<ChatEvent>
}
