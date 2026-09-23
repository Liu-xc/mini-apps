package com.leo.libs.agent.testing

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ChatCompletion
import com.leo.libs.agent.ChatEvent
import com.leo.libs.agent.ChatModel
import com.leo.libs.agent.ChatRequest
import com.leo.libs.agent.Message
import com.leo.libs.agent.Part
import com.leo.libs.agent.ProviderPreset
import com.leo.libs.agent.Role
import com.leo.libs.agent.ToolCall
import com.leo.libs.agent.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 脚本回合 */
sealed class FakeTurn {
    class Text(val text: String, val chunkSize: Int = 4) : FakeTurn()
    class ToolCalls(val calls: List<ToolCall>, val finishText: String = "") : FakeTurn()
    class Failure(val error: AgentError) : FakeTurn()
}

fun fakeText(text: String, chunkSize: Int = 4) = FakeTurn.Text(text, chunkSize)
fun fakeToolCalls(vararg calls: ToolCall, finishText: String = "") = FakeTurn.ToolCalls(calls.toList(), finishText)
fun fakeFailing(error: AgentError) = FakeTurn.Failure(error)

/**
 * 脚本化模型：按序弹出回合，请求记录到 [requests] 供断言。
 * 「CI 永不打真 API」的关键件（spec §10），也可用作 app 演示模式的离线模型。
 */
class FakeChatModel(turns: List<FakeTurn>) : ChatModel {

    override val preset: ProviderPreset =
        ProviderPreset.custom("fake", "Fake", "http://localhost", listOf("fake-model"))

    val requests = mutableListOf<ChatRequest>()

    private val queue = ArrayDeque(turns)

    private fun next(request: ChatRequest): FakeTurn {
        requests += request
        return queue.removeFirstOrNull()
            ?: throw AgentError.Provider(-1, "FakeChatModel 脚本已耗尽（第 ${requests.size} 次请求无回合可用）")
    }

    override suspend fun complete(request: ChatRequest): ChatCompletion = when (val turn = next(request)) {
        is FakeTurn.Text -> ChatCompletion(Message.assistant(turn.text), "stop", Usage())
        is FakeTurn.ToolCalls -> ChatCompletion(
            Message(Role.Assistant, toolCalls = turn.calls),
            "tool_calls",
            Usage(),
        )
        is FakeTurn.Failure -> throw turn.error
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        when (val turn = next(request)) {
            is FakeTurn.Text -> {
                turn.text.chunked(turn.chunkSize).forEach { emit(ChatEvent.TextDelta(it)) }
                emit(ChatEvent.Completed(ChatCompletion(Message.assistant(turn.text), "stop", Usage())))
            }
            is FakeTurn.ToolCalls -> {
                turn.calls.forEachIndexed { index, call ->
                    emit(ChatEvent.ToolCallDelta(index, call.id, call.name, ""))
                    emit(ChatEvent.ToolCallDelta(index, null, null, call.argumentsJson))
                }
                val message = Message(
                    Role.Assistant,
                    if (turn.finishText.isEmpty()) emptyList() else listOf(Part.Text(turn.finishText)),
                    toolCalls = turn.calls,
                )
                emit(ChatEvent.Completed(ChatCompletion(message, "tool_calls", Usage())))
            }
            is FakeTurn.Failure -> throw turn.error
        }
    }
}
