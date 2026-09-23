package com.leo.libs.agent.internal

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ChatCompletion
import com.leo.libs.agent.ChatEvent
import com.leo.libs.agent.Message
import com.leo.libs.agent.Part
import com.leo.libs.agent.Quirks
import com.leo.libs.agent.Role
import com.leo.libs.agent.ToolCall
import com.leo.libs.agent.Usage

/** SSE 行解码：只关心 data 载荷；注释/event:/id:/retry: 忽略；空行触发派发；多行 data 按 SSE 规范拼接 */
internal class SseDecoder {
    private val data = StringBuilder()

    /** 一行输入 → 事件结束时返回 data 载荷，否则 null */
    fun onLine(raw: String): String? {
        val line = raw.trimEnd('\r')
        return when {
            line.isEmpty() -> take()
            line.startsWith(":") -> null
            line.startsWith("data:") -> {
                data.append(line.removePrefix("data:").removePrefix(" "))
                null
            }
            else -> null
        }
    }

    /** 流结束但无空行收尾时兜底派发 */
    fun flush(): String? = take()

    private fun take(): String? =
        if (data.isEmpty()) null else data.toString().also { data.setLength(0) }
}

/**
 * 把 wire chunk 流装配成领域事件：文本/推理增量直通，工具调用分片按 index 聚合，
 * 终态产出 Completed（推理内容不回喂进消息——OpenAI 回喂契约）。
 */
internal class StreamAssembler(private val quirks: Quirks) {
    private val text = StringBuilder()
    private val toolCalls = ToolCallAccumulator()
    private var finishReason: String? = null
    private var usage: Usage? = null
    private var sawDelta = false

    /** 容错：未知/损坏载荷返回空事件不崩（island M0 教训） */
    fun feed(payload: String): List<ChatEvent> {
        val chunk = try {
            wireJson.decodeFromString(WireStreamChunk.serializer(), payload)
        } catch (_: Exception) {
            return emptyList()
        }
        val events = mutableListOf<ChatEvent>()
        for (choice in chunk.choices) {
            val delta = choice.delta ?: continue
            sawDelta = true
            delta.content?.let {
                text.append(it)
                events += ChatEvent.TextDelta(it)
            }
            if (quirks.reasoningField) {
                delta.reasoningContent?.let { events += ChatEvent.ThinkingDelta(it) }
            }
            delta.toolCalls?.let { calls ->
                toolCalls.feed(calls)
                calls.forEach { tc ->
                    events += ChatEvent.ToolCallDelta(tc.index, tc.id, tc.function?.name, tc.function?.arguments.orEmpty())
                }
            }
            choice.finishReason?.let { finishReason = it }
        }
        chunk.usage?.let { usage = it.toDomain() }
        return events
    }

    fun completed(): ChatEvent.Completed {
        if (!sawDelta && finishReason == null) {
            throw AgentError.Provider(-1, "流式响应中断且无内容")
        }
        val message = Message(
            role = Role.Assistant,
            parts = if (text.isEmpty()) emptyList() else listOf(Part.Text(text.toString())),
            toolCalls = toolCalls.build(),
        )
        return ChatEvent.Completed(ChatCompletion(message, finishReason, usage ?: Usage()))
    }
}

/** 工具调用分片聚合：id/name 只在首片出现，arguments 跨片拼接 */
internal class ToolCallAccumulator {
    private val order = mutableListOf<Int>()
    private val ids = mutableMapOf<Int, String>()
    private val names = mutableMapOf<Int, String>()
    private val args = mutableMapOf<Int, StringBuilder>()

    fun feed(calls: List<WireDeltaToolCall>) {
        for (call in calls) {
            val i = call.index
            if (i !in args) {
                order += i
                args[i] = StringBuilder()
            }
            call.id?.let { ids[i] = it }
            call.function?.name?.let { names[i] = it }
            call.function?.arguments?.let { args.getValue(i).append(it) }
        }
    }

    fun build(): List<ToolCall> = if (order.isEmpty()) emptyList() else order.map { i ->
        ToolCall(
            id = ids[i] ?: "call_$i",
            name = names[i].orEmpty(),
            argumentsJson = args.getValue(i).toString().ifBlank { "{}" },
        )
    }
}
