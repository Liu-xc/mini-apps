package com.leo.wardrobe.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.libs.agent.AgentConfig
import com.leo.libs.agent.AgentError
import com.leo.libs.agent.AgentEvent
import com.leo.libs.agent.AgentRunner
import com.leo.libs.agent.Message
import com.leo.libs.agent.ProviderPreset
import com.leo.libs.agent.Providers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.leo.wardrobe.WardrobeApp

/**
 * 对话域 ViewModel（it-041 阶段 B，US-41b/c）：AgentRunner 驱动流式对话，
 * FileSessionStore 为历史 SSOT（用户消息发送即入会话，runner 持久化产出，杀进程可续）。
 * 演示模式挂离线 FakeChatModel（红线②：零外呼）。
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container
    private val prefs = container.prefs
    private val session = container.agentSession

    /** 单会话（提案待确认项 4 默认采纳：全局一条对话线） */
    private val sessionId = "wardrobe-chat"

    /** 当前回合进行中的工具条（完成后并入历史由 messages 渲染） */
    data class ToolNotice(val name: String, val detail: String, val ok: Boolean)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<AgentError?>(null)
    val error: StateFlow<AgentError?> = _error.asStateFlow()

    private val _toolNotices = MutableStateFlow<List<ToolNotice>>(emptyList())
    val toolNotices: StateFlow<List<ToolNotice>> = _toolNotices.asStateFlow()

    private var lastUserText = ""
    private var job: Job? = null

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch { _messages.value = session.messages(sessionId) }
    }

    /** 发送（US-41b）：用户消息即刻入会话（调用方契约），再进 loop */
    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _running.value) return
        lastUserText = text
        _error.value = null
        viewModelScope.launch {
            session.append(sessionId, Message.user(text))
            refresh()
            runLoop()
        }
    }

    /** 失败重试（US-41b）：历史已含该用户消息，直接续跑不重复追加 */
    fun retry() {
        if (_running.value || lastUserText.isEmpty()) return
        _error.value = null
        viewModelScope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun runLoop() {
        job?.cancel()
        _running.value = true
        _streaming.value = ""
        _thinking.value = false
        _toolNotices.value = emptyList()

        val presetId = prefs.aiPresetId.first()
        val modelSel = prefs.aiModel.first()
        val customBase = prefs.aiCustomBaseUrl.first()
        val customModel = prefs.aiCustomModel.first()
        val preset: ProviderPreset? = if (presetId == "custom") {
            if (customBase.isBlank() || customModel.isBlank()) null
            else ProviderPreset.custom("custom", "自定义", customBase.trim(), listOf(customModel.trim()))
        } else {
            Providers.all.find { it.id == presetId }
        }
        val model = if (preset == null) null else if (presetId == "custom") {
            customModel.trim()
        } else {
            modelSel.ifBlank { preset.defaultModel }
        }
        if (preset == null || model == null) {
            _error.value = AgentError.Auth("请先在设置里配置模型连接（厂商与模型）")
            _running.value = false
            return
        }

        val personId = prefs.currentPersonId.first()
        val runner = AgentRunner(
            model = container.chatModel(preset), // 演示模式由组合根注入离线 FakeChatModel
            config = AgentConfig(systemPrompt = SYSTEM_PROMPT, maxSteps = 8, model = model),
            tools = wardrobeTools(
                data = { container.repository.data.value },
                personId = { personId },
            ),
            session = session,
            sessionId = sessionId,
        )

        try {
            runner.run(session.messages(sessionId)).collect { ev ->
                when (ev) {
                    is AgentEvent.StepStarted -> {}
                    is AgentEvent.StepFinished -> {}
                    is AgentEvent.TextDelta -> {
                        _thinking.value = false
                        _streaming.update { it + ev.text }
                    }
                    is AgentEvent.ThinkingDelta -> _thinking.value = true
                    is AgentEvent.ToolRequested -> _toolNotices.update {
                        it + ToolNotice(ev.call.name, "查询中…", ok = true)
                    }
                    is AgentEvent.ToolFinished -> _toolNotices.update { list ->
                        list.mapIndexed { i, n ->
                            if (i == list.lastIndex) n.copy(
                                detail = ev.result.asText().take(80),
                                ok = ev.result is com.leo.libs.agent.tool.ToolResult.Ok,
                            ) else n
                        }
                    }
                    is AgentEvent.Completed -> {
                        container.agentUsage.record(preset.id, model, ev.usage)
                        _streaming.value = ""
                        _thinking.value = false
                    }
                    is AgentEvent.Failed -> {
                        _error.value = ev.error
                        _streaming.value = ""
                        _thinking.value = false
                    }
                }
            }
        } catch (e: CancellationException) {
            _streaming.value = "" // 半截消息不留脏状态（US-A2/41b）
            throw e
        } finally {
            _running.value = false
            _toolNotices.value = emptyList()
            refresh() // assistant / tool 结果已由 runner 持久化
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是「衣橱顾问」，服务于用户的个人衣橱应用（中文回复）。" +
                "回答前先用工具查询真实数据，引用具体单品名称，不要编造衣橱里不存在的衣物。" +
                "建议给出 1~3 套可执行的搭配思路并说明理由；数据为只读，你不能修改衣橱。" +
                "回复简洁口语化，不用 markdown 标题。"
    }
}
