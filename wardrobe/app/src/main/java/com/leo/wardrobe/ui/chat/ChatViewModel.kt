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
import com.leo.libs.agent.Role
import com.leo.wardrobe.data.chat.ChatSessionSummary
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.currentPersonOrFirst
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.leo.wardrobe.WardrobeApp
import java.io.File

/**
 * 对话域 ViewModel（it-041 阶段 B，US-41b/c）：AgentRunner 驱动流式对话，
 * FileSessionStore 为历史 SSOT（用户消息发送即入会话，runner 持久化产出，杀进程可续）。
 * Mock 连接只在用户主动保存隔离的测试 Key 后外呼；未配置时由列表/详情只读门禁拦截。
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container
    private val prefs = container.prefs
    private val session = container.agentSession

    /** it-054：卡片只从当前角色的真实衣橱单品解析，不接受模型提供的外部图片。 */
    val recommendationItems: StateFlow<List<Item>> =
        combine(container.repository.data, prefs.currentPersonId) { data, savedId ->
            data.currentPersonOrFirst(savedId)?.let { data.itemsOf(it.id) }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** it-075：穿搭结果卡同源——当前角色的穿搭实时快照（行点击进 W7）。 */
    val chatOutfits: StateFlow<List<com.leo.wardrobe.domain.model.Outfit>> =
        combine(container.repository.data, prefs.currentPersonId) { data, savedId ->
            data.currentPersonOrFirst(savedId)?.let { data.outfitsOf(it.id) }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** it-071：不再主线程 stat——文件缺失交给 Coil 兜底（与 AppViewModel.imageFileOf 同口径）。 */
    fun imageFileOf(name: String): File? = container.imageStore.file(name)

    /** it-050：当前打开的会话由 W13 路由提供；目录负责历史列表与旧会话迁移。 */
    private var sessionId = ""

    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChatSessionSummary>> = _sessions.asStateFlow()

    private val _canChat = MutableStateFlow(false)
    val canChat: StateFlow<Boolean> = _canChat.asStateFlow()

    private val _cacheHit = MutableStateFlow(false)
    val cacheHit: StateFlow<Boolean> = _cacheHit.asStateFlow()

    /** 当前回合进行中的工具条（完成后并入历史由 messages 渲染）；
     *  it-075：[card] 为工具结果的结构化卡片（无 payload 的工具为 null）。 */
    data class ToolNotice(
        val name: String,
        val detail: String,
        val ok: Boolean,
        val card: ToolResultCard? = null,
    )

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
        refreshSessions()
    }

    private fun refresh() {
        if (sessionId.isBlank()) return
        viewModelScope.launch { _messages.value = session.messages(sessionId) }
    }

    /** W12 每次显示时调用：Key 保存/清除后无需杀进程即可刷新可写状态。 */
    fun refreshSessions() {
        viewModelScope.launch {
            _sessions.value = container.chatSessions.list()
            _canChat.value = resolveConnection() != null
        }
    }

    fun open(sessionId: String) {
        this.sessionId = sessionId
        _messages.value = emptyList()
        _error.value = null
        _streaming.value = ""
        _thinking.value = false
        _toolNotices.value = emptyList()
        lastUserText = ""
        _cacheHit.value = false
        viewModelScope.launch {
            container.chatSessions.ensure(sessionId)
            _messages.value = session.messages(sessionId)
            _canChat.value = resolveConnection() != null
            _sessions.value = container.chatSessions.list()
        }
    }

    fun createSession(onCreated: (String) -> Unit) {
        if (!_canChat.value) return
        viewModelScope.launch {
            onCreated(container.chatSessions.create().id)
            _sessions.value = container.chatSessions.list()
        }
    }

    /** 发送（US-41b）：用户消息即刻入会话（调用方契约），再进 loop */
    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || isBusy() || !_canChat.value || sessionId.isBlank()) return
        lastUserText = text
        _error.value = null
        job = viewModelScope.launch {
            // Key 可能在 W11 被清除后返回；提交消息前再次检查，防止会话留下无响应的用户消息。
            if (resolveConnection() == null) {
                _canChat.value = false
                return@launch
            }
            // it-043 补遗：用户消息盖时间戳（回复侧由 AgentRunner 落盘时盖章）
            session.append(sessionId, Message.user(text).copy(createdAt = System.currentTimeMillis()))
            refresh()
            container.chatSessions.refresh(sessionId, session.messages(sessionId))
            _sessions.value = container.chatSessions.list()
            runLoop()
        }
    }

    /**
     * it-043 补遗（走查 C11）：重新生成——保留最后一轮的用户提问，
     * 丢弃其后的全部产出（assistant/tool），重建会话后续跑。
     * 无需 SDK 尾删接口：clear + 重放保留段（app 侧幂等重建）。
     */
    fun regenerate() {
        if (isBusy() || !_canChat.value || sessionId.isBlank()) return
        job = viewModelScope.launch {
            val msgs = session.messages(sessionId)
            val lastUserIdx = msgs.indexOfLast { it.role == Role.User }
            if (lastUserIdx < 0) return@launch
            session.clear(sessionId)
            msgs.subList(0, lastUserIdx + 1).forEach { session.append(sessionId, it) }
            lastUserText = msgs[lastUserIdx].text
            _error.value = null
            refresh()
            runLoop()
        }
    }

    /** 失败重试（US-41b）：历史已含该用户消息，直接续跑不重复追加 */
    fun retry() {
        if (isBusy() || !_canChat.value || lastUserText.isEmpty() || sessionId.isBlank()) return
        _error.value = null
        job = viewModelScope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
    }

    fun clearError() {
        _error.value = null
    }

    /** it-044 O6（走查 C11）：复制回复到剪贴板；toast 由屏幕层走全局 snackbar */
    fun copyReply(text: String) {
        container.share.copyText(text)
    }

    private suspend fun runLoop() {
        _running.value = true
        _cacheHit.value = false
        _streaming.value = ""
        _thinking.value = false
        _toolNotices.value = emptyList()

        val connection = resolveConnection()
        if (connection == null) {
            _error.value = AgentError.Auth("请先在设置里配置模型连接（厂商与模型）")
            _running.value = false
            return
        }
        val (preset, model) = connection

        // 与 AppViewModel.currentPerson 使用相同的回退规则；新装 Mock 的首个角色尚未写入偏好时也能查到衣橱。
        val personId = container.repository.data.value
            .currentPersonOrFirst(prefs.currentPersonId.first())
            ?.id
        if (personId == null) {
            val localReply = Message.assistant(
                "你的衣橱还没有角色。先到衣橱页选择或创建角色，再来问我搭配吧。",
            ).copy(createdAt = System.currentTimeMillis())
            session.append(sessionId, localReply)
            val updatedMessages = session.messages(sessionId)
            _messages.value = updatedMessages
            container.chatSessions.refresh(sessionId, updatedMessages)
            _sessions.value = container.chatSessions.list()
            _running.value = false
            return
        }
        val chatModel = container.chatModel(preset)
        val mockCachedModel = chatModel as? com.leo.wardrobe.data.mock.CachedMockChatModel
        mockCachedModel?.resetRunStats()
        var cacheCommitted = false
        val runner = AgentRunner(
            model = chatModel,
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
                                // it-075：live 卡片与历史回放同一解析入口（工具完成即出卡）
                                card = (ev.result as? com.leo.libs.agent.tool.ToolResult.Ok)
                                    ?.let { parseToolResultCard(it.payload) },
                            ) else n
                        }
                    }
                    is AgentEvent.Completed -> {
                        mockCachedModel?.commitPending()
                        cacheCommitted = true
                        container.agentUsage.record(preset.id, model, ev.usage)
                        _cacheHit.value = mockCachedModel?.hadCacheHit == true
                        _streaming.value = ""
                        _thinking.value = false
                    }
                    is AgentEvent.Failed -> {
                        mockCachedModel?.discardPending()
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
            if (!cacheCommitted) mockCachedModel?.discardPending()
            _running.value = false
            _toolNotices.value = emptyList()
            refresh() // assistant / tool 结果已由 runner 持久化
            if (sessionId.isNotBlank()) {
                container.chatSessions.refresh(sessionId, session.messages(sessionId))
                _sessions.value = container.chatSessions.list()
            }
        }
    }

    private fun isBusy(): Boolean = _running.value || job?.isActive == true

    /** 返回可运行连接；Key 缺失即 null，保证未配置场景不会写入会话。 */
    private suspend fun resolveConnection(): Pair<ProviderPreset, String>? {
        val presetId = prefs.aiPresetId.first()
        val modelSel = prefs.aiModel.first()
        val customBase = prefs.aiCustomBaseUrl.first()
        val customModel = prefs.aiCustomModel.first()
        val preset: ProviderPreset? = if (presetId == "custom") {
            if (customBase.isBlank() || customModel.isBlank()) null
            else ProviderPreset.custom("custom", "自定义", customBase.trim(), listOf(customModel.trim()))
        } else Providers.all.find { it.id == presetId }
        val model = if (preset == null) null else if (presetId == "custom") customModel.trim()
        else modelSel.ifBlank { preset.defaultModel }
        val hasKey = preset?.let { container.apiKeyStore.get(it.id)?.isNotBlank() == true } == true
        return if (preset != null && !model.isNullOrBlank() && hasKey) preset to model else null
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是「衣橱顾问」，服务于用户的个人衣橱应用（中文回复）。" +
                "按问题调用必要的只读工具并引用具体单品名称，不要编造衣橱里不存在的衣物；同一查询条件只查一次。" +
                "建议给出 1~3 套可执行的搭配思路并说明理由；数据为只读，你不能修改衣橱。" +
                "请使用 Markdown 回复：每套方案以「## 第一套 · 场景」或「## 第二套 · 场景」开头，" +
                "用「- 品类：单品原名」列出单品，品类只能使用上装/外套/下装/连衣裙/鞋/包/帽子/其他配饰，" +
                "单品名称必须原样来自 search_items 的结果；随后用「**适合**：…」和「**理由**：…」说明。" +
                "不要输出表格或代码块，不要给衣橱里不存在的单品配图，不要把 Markdown 符号写成解释。"
    }
}
