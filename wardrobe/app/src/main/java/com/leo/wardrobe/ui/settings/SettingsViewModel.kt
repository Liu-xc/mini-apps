package com.leo.wardrobe.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ChatRequest
import com.leo.libs.agent.Message
import com.leo.libs.agent.ProviderPreset
import com.leo.libs.agent.Providers
import com.leo.libs.agent.maskApiKey
import com.leo.wardrobe.WardrobeApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置域 ViewModel（it-041 阶段 A，US-41a）：厂商/模型/自定义连接偏好 + Key 密文存取 + 连通性自检。
 * Key 本体永不出现在 UI（只给 mask）与日志；演示模式下 Key 输入禁用（AppContainer 注入内存实现）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container
    private val prefs = container.prefs
    private val keyStore = container.apiKeyStore

    /** W11 表单态（合并 DataStore 持久值） */
    data class ConnectionUi(
        val presetId: String = "glm",
        val model: String = "",
        val customBaseUrl: String = "",
        val customModel: String = "",
    )

    val connection: StateFlow<ConnectionUi> = combine(
        prefs.aiPresetId,
        prefs.aiModel,
        prefs.aiCustomBaseUrl,
        prefs.aiCustomModel,
    ) { presetId, model, baseUrl, customModel ->
        ConnectionUi(presetId, model, baseUrl, customModel)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUi())

    /** 当前厂商已存 Key 的 mask（如 sk-a***wxyz）；null = 未配置 */
    val keyMask: StateFlow<String?> = connection
        .flatMapLatest { ui -> flow { emit(keyStore.get(ui.presetId)?.let(::maskApiKey)) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 一等公民厂商 + 自定义（Providers.all 之外的第四项） */
    val presetOptions: List<ProviderPreset> get() = Providers.all

    sealed interface CheckState {
        data object Idle : CheckState
        data object Running : CheckState
        data class Success(val detail: String) : CheckState
        data class Failure(val message: String) : CheckState
    }

    private val _check = MutableStateFlow<CheckState>(CheckState.Idle)
    val check: StateFlow<CheckState> = _check.asStateFlow()

    /** it-043 O4（走查 C6）：最近一次自检（持久化，重进页面仍可见） */
    data class LastCheck(val ok: Boolean, val detail: String, val at: Long)

    val lastCheck: StateFlow<LastCheck?> = prefs.aiLastCheck
        .map { raw -> parseLastCheck(raw) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun parseLastCheck(raw: String?): LastCheck? {
        raw ?: return null
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3) return null
        val at = parts[2].toLongOrNull() ?: return null
        return LastCheck(ok = parts[0] == "OK", detail = parts[1], at = at)
    }

    private suspend fun persistLastCheck(ok: Boolean, detail: String) {
        val safe = detail.replace('|', '/').take(120)
        prefs.setAiLastCheck("${if (ok) "OK" else "ERR"}|$safe|${System.currentTimeMillis()}")
    }

    /** US-41d：累计用量（厂商×模型），进入设置页与每次对话后刷新 */
    private val _usage = MutableStateFlow<Map<Pair<String, String>, com.leo.libs.agent.Usage>>(emptyMap())
    val usage: StateFlow<Map<Pair<String, String>, com.leo.libs.agent.Usage>> = _usage.asStateFlow()

    init {
        refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch { _usage.value = container.agentUsage.totals() }
    }

    val isDemo: Boolean get() = container.isDemo

    /** 解析选中厂商为可实例化的 preset（自定义项就地构造；空 baseUrl 由调用方先拦） */
    fun resolvePreset(ui: ConnectionUi): ProviderPreset? =
        if (ui.presetId == CUSTOM_ID) {
            if (ui.customBaseUrl.isBlank() || ui.customModel.isBlank()) null
            else ProviderPreset.custom(CUSTOM_ID, "自定义", ui.customBaseUrl.trim(), listOf(ui.customModel.trim()))
        } else {
            presetOptions.find { it.id == ui.presetId }
        }

    /** 生效模型：自定义项即其模型；预设项选填（空 = preset 默认，如 GLM 旗舰档） */
    fun effectiveModel(ui: ConnectionUi): String? =
        if (ui.presetId == CUSTOM_ID) ui.customModel.ifBlank { null }?.trim()
        else resolvePreset(ui)?.let { ui.model.ifBlank { it.defaultModel } }

    /** 用量卡的厂商显示名 */
    fun presetLabel(presetId: String): String =
        if (presetId == CUSTOM_ID) "自定义"
        else presetOptions.find { it.id == presetId }?.displayName ?: presetId

    /**
     * 保存连接偏好（+ 可选新 Key）并跑连通性自检（1-token ping，US-A1）。
     * Key 空串 = 不改动已存 Key。
     */
    fun saveAndCheck(ui: ConnectionUi, keyInput: String) {
        viewModelScope.launch {
            _check.value = CheckState.Running
            runCatching {
                prefs.setAiConnection(ui.presetId, ui.model.trim())
                if (ui.presetId == CUSTOM_ID) prefs.setAiCustom(ui.customBaseUrl.trim(), ui.customModel.trim())
                keyInput.trim().takeIf { it.isNotEmpty() }?.let { keyStore.put(ui.presetId, it) }
            }.onFailure {
                _check.value = CheckState.Failure("保存失败：${it.message ?: "未知错误"}")
                return@launch
            }

            val preset = resolvePreset(ui)
            val model = effectiveModel(ui)
            if (preset == null || model == null) {
                _check.value = CheckState.Failure(
                    if (ui.presetId == CUSTOM_ID) "请先填写 Base URL 与模型名" else "请选择厂商与模型",
                )
                return@launch
            }
            try {
                container.chatModel(preset).complete(
                    ChatRequest(messages = listOf(Message.user("ping")), model = model, maxTokens = 8),
                )
                persistLastCheck(ok = true, detail = "连通正常 · $model")
                _check.value = CheckState.Success("连通正常 · $model")
            } catch (e: AgentError) {
                android.util.Log.e("Settings", "self-check AgentError", e)
                persistLastCheck(ok = false, detail = e.userMessage)
                _check.value = CheckState.Failure(e.userMessage)
            } catch (e: Exception) {
                android.util.Log.e("Settings", "self-check ${e::class.java.name}", e)
                val msg = e.message?.ifBlank { null } ?: "未知错误"
                persistLastCheck(ok = false, detail = msg)
                _check.value = CheckState.Failure(msg)
            }
        }
    }

    /** 清除当前厂商的 Key（US-41a；UI 侧带确认）；同时清掉自检状态（Key 已非同一个） */
    fun clearKey(presetId: String) {
        viewModelScope.launch {
            keyStore.delete(presetId)
            prefs.setAiLastCheck("")
            _check.value = CheckState.Idle
        }
    }

    /** 切换厂商（换厂商则清空模型选择——模型是厂商域的） */
    fun selectPreset(presetId: String) {
        viewModelScope.launch { prefs.setAiConnection(presetId, "") }
    }

    /** 预设项内选模型（空 = preset 默认模型） */
    fun selectModel(model: String) {
        viewModelScope.launch { prefs.setAiConnection(connection.value.presetId, model) }
    }

    /** 自定义厂商配置增量更新 */
    fun updateCustom(baseUrl: String? = null, model: String? = null) {
        val current = connection.value
        viewModelScope.launch {
            prefs.setAiCustom(
                baseUrl ?: current.customBaseUrl,
                model ?: current.customModel,
            )
        }
    }

    fun resetCheck() {
        _check.value = CheckState.Idle
    }

    companion object {
        const val CUSTOM_ID = "custom"
    }
}
