package com.leo.libs.agent

import com.leo.libs.agent.session.ContextPolicy
import com.leo.libs.agent.session.DefaultContextPolicy
import com.leo.libs.agent.session.SessionStore
import com.leo.libs.agent.tool.ToolRegistry
import com.leo.libs.agent.tool.ToolResult
import com.leo.libs.agent.tool.toolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Agent loop 配置（it-002 §2） */
data class AgentConfig(
    val systemPrompt: String = "",
    /** 工具调用轮次上限（含首轮）；耗尽后去 tools 追问一轮强制收尾（提案待确认项 1，按推荐策略实现） */
    val maxSteps: Int = 8,
    val temperature: Double? = null,
    /** 覆盖 preset 默认模型 */
    val model: String? = null,
) {
    init {
        require(maxSteps >= 1) { "maxSteps 必须 ≥1" }
    }
}

/**
 * Agent 事件（ADR-005：只增不改名）。终态两种：[AgentEvent.Completed] 或 [AgentEvent.Failed]；
 * Failed 后 Flow 正常结束（错误作为事件下发，collector 无需 try/catch AgentError）。
 */
sealed interface AgentEvent {
    data object StepStarted : AgentEvent
    data class TextDelta(val text: String) : AgentEvent

    /** 推理增量，仅供 UI 展示，不回喂历史 */
    data class ThinkingDelta(val text: String) : AgentEvent

    data class ToolRequested(val call: ToolCall) : AgentEvent
    data class ToolFinished(val call: ToolCall, val result: ToolResult) : AgentEvent
    data object StepFinished : AgentEvent

    /** [usage] 为全程各步累计 */
    data class Completed(val message: Message, val usage: Usage) : AgentEvent
    data class Failed(val error: AgentError) : AgentEvent
}

/**
 * 多步工具调用循环（it-002 §2）：
 * stream 一步 → finish_reason=tool_calls 则逐个执行回喂 → 直到文本回答 / maxSteps 熔断收尾。
 * - 工具异常、未知工具、参数解析失败 → [ToolResult.error] 回喂模型自纠，不进 [AgentEvent.Failed]；
 * - 传输层 [AgentError] → [AgentEvent.Failed] 后结束，已产出历史不回滚（session 已持久化，可续跑）；
 * - 取消 = 协程取消，半截文本不落会话；
 * - [SessionStore] 提供时，每条产出消息（assistant / tool 结果 / 收尾提示）立即 append。
 *   **调用方契约**：用户输入在发送时由 app 自行 append 进会话，`run(store.messages(id))` 即可续跑——
 *   runner 不重复持久化入参 history。
 */
class AgentRunner(
    private val model: ChatModel,
    private val config: AgentConfig = AgentConfig(),
    private val tools: ToolRegistry = toolRegistry {},
    private val session: SessionStore? = null,
    private val sessionId: String? = null,
    private val contextPolicy: ContextPolicy = DefaultContextPolicy(),
) {
    init {
        require(session == null || !sessionId.isNullOrBlank()) { "提供 SessionStore 时必须给 sessionId" }
    }

    fun run(history: List<Message>): Flow<AgentEvent> = flow {
        val system = config.systemPrompt.ifBlank { null }?.let(Message::system)
        val messages = ArrayList<Message>()
        system?.let(messages::add)
        // 历史里游离的 system 丢弃（常驻位只留给 config 的那条），破损头部由 policy 清理
        messages += contextPolicy.trim(history.filter { it.role != Role.System }, system)

        var totalUsage = Usage()
        var steps = 0
        var closing = false

        while (true) {
            emit(AgentEvent.StepStarted)
            val request = ChatRequest(
                messages = messages.toList(),
                model = config.model,
                tools = if (closing) emptyList() else tools.specs,
                temperature = config.temperature,
            )
            var assistant: Message? = null
            var finish: String? = null
            var stepUsage = Usage()
            try {
                model.stream(request).collect { ev ->
                    when (ev) {
                        is ChatEvent.TextDelta -> emit(AgentEvent.TextDelta(ev.text))
                        is ChatEvent.ThinkingDelta -> emit(AgentEvent.ThinkingDelta(ev.text))
                        is ChatEvent.ToolCallDelta -> Unit // 分片已由传输层装配进 Completed.message
                        is ChatEvent.Completed -> {
                            assistant = ev.completion.message
                            finish = ev.completion.finishReason
                            stepUsage = ev.completion.usage
                        }
                    }
                }
            } catch (e: AgentError) {
                emit(AgentEvent.Failed(e))
                return@flow
            }
            val msg = assistant ?: run {
                emit(AgentEvent.Failed(AgentError.Provider(-1, "模型流未返回终态消息")))
                return@flow
            }

            totalUsage += stepUsage
            steps++
            messages += msg
            persist(msg)

            val hasCalls = finish == "tool_calls" && msg.toolCalls.isNotEmpty()
            if (!hasCalls || closing) {
                // 正常文本回答；或收尾轮仍回 tool_calls（模型不守规矩）→ 就地截断
                emit(AgentEvent.StepFinished)
                emit(AgentEvent.Completed(msg, totalUsage))
                return@flow
            }

            for (call in msg.toolCalls) {
                emit(AgentEvent.ToolRequested(call))
                val result = tools.execute(call.name, call.argumentsJson)
                emit(AgentEvent.ToolFinished(call, result))
                val toolMsg = Message.toolResult(call.id, result.asText())
                messages += toolMsg
                persist(toolMsg)
            }
            emit(AgentEvent.StepFinished)

            if (steps >= config.maxSteps) {
                // maxSteps 熔断：工具结果已回喂，追加收尾提示并以不带 tools 的请求强制作答
                val closingPrompt = Message.user(CLOSING_PROMPT)
                messages += closingPrompt
                persist(closingPrompt)
                closing = true
            }
        }
    }

    private suspend fun persist(message: Message) {
        // it-043 补遗：落盘盖时间戳（旧字段缺省 0），会话重启后 UI 可显示消息时间
        val stamped = if (message.createdAt == 0L) message.copy(createdAt = System.currentTimeMillis()) else message
        session?.append(sessionId!!, stamped)
    }

    private companion object {
        const val CLOSING_PROMPT = "请基于以上信息直接给出最终回答，不要调用任何工具。"
    }
}
