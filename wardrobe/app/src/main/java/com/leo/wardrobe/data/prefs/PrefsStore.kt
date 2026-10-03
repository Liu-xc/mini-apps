package com.leo.wardrobe.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leo.wardrobe.domain.model.WardrobeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("wardrobe_prefs")

/**
 * 轻偏好：当前角色 + 各槽位组合记忆（US-06），按 personId 隔离。
 * it-011：导出维度选择记忆 + 格位滑动 coach 首演标记。
 */
class PrefsStore(private val context: Context, private val aiNamespace: String = "") {

    private val keyPerson = stringPreferencesKey("current_person")
    private val keyPersonNote = stringPreferencesKey("person_note")
    private val keyExportSelections = stringSetPreferencesKey("export_selections")
    private val keyCustomPrompt = stringPreferencesKey("custom_prompt")
    private val keyCoachSlots = booleanPreferencesKey("coach_slots_shown")

    // it-070 US-61：外观主题三态（system|light|dark，默认跟随系统）
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    private fun slotKey(personId: String) = stringSetPreferencesKey("slots_$personId")

    val currentPersonId: Flow<String?> = context.store.data.map { it[keyPerson] }

    suspend fun setCurrentPerson(id: String) {
        context.store.edit { it[keyPerson] = id }
    }

    /** 生图文案的「人物描述」（it-002）：一次输入，全局记住 */
    val personNote: Flow<String> = context.store.data.map { it[keyPersonNote] ?: "" }

    suspend fun setPersonNote(note: String) {
        context.store.edit { it[keyPersonNote] = note }
    }

    /** 导出的「自定义要求」（it-013）：自由追加的 prompt，记住上次 */
    val customPrompt: Flow<String> = context.store.data.map { it[keyCustomPrompt] ?: "" }

    suspend fun setCustomPrompt(prompt: String) {
        context.store.edit { it[keyCustomPrompt] = prompt }
    }

    /** 该角色各槽位选中：Map<品类.name, itemId>（存储格式 "TOP=itemId"） */
    fun slotSelections(personId: String): Flow<Map<String, String>> =
        context.store.data.map { prefs ->
            prefs[slotKey(personId)].orEmpty()
                .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0] to it[1] }
        }

    suspend fun saveSlotSelection(personId: String, category: WardrobeCategory, itemId: String?) {
        context.store.edit { prefs ->
            val key = slotKey(personId)
            val current = prefs[key].orEmpty()
                .filterNot { it.startsWith("${category.name}=") }
                .toMutableSet()
            if (itemId != null) current += "${category.name}=$itemId"
            prefs[key] = current
        }
    }

    /** it-070 US-61：外观主题模式（"system"|"light"|"dark"，未知值一律回落 system） */
    val themeMode: Flow<String> = context.store.data.map { it[keyThemeMode] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.store.edit { it[keyThemeMode] = mode }
    }

    // it-081：exportSelections 三成员已删——it-061 修3 后 UI（ExportSheet）本地持有选择，
    // AppViewModel 死 API 同批清除；旧 DataStore key 残留无害

    /** W1 格位滑动 coach 动画（it-011 O6）：仅首次进入演示一次 */
    val coachSlotsShown: Flow<Boolean> = context.store.data.map { it[keyCoachSlots] ?: false }

    suspend fun markCoachSlotsShown() {
        context.store.edit { it[keyCoachSlots] = true }
    }

    // it-041 US-41a：AI 模型连接偏好（Key 本体走 KeystoreApiKeyStore，不进 DataStore）

    private fun aiKey(name: String) = stringPreferencesKey("${aiNamespace}ai_$name")
    private val keyAiPreset = aiKey("preset")
    private val keyAiModel = aiKey("model")
    private val keyAiCustomBaseUrl = aiKey("custom_base_url")
    private val keyAiCustomModel = aiKey("custom_model")

    /** 选中的厂商 preset id（Providers.all 或 "custom"） */
    val aiPresetId: Flow<String> = context.store.data.map { it[keyAiPreset] ?: "glm" }

    /** 选中厂商下的模型名（空 = 用 preset 默认模型） */
    val aiModel: Flow<String> = context.store.data.map { it[keyAiModel] ?: "" }

    suspend fun setAiConnection(presetId: String, model: String) {
        context.store.edit {
            it[keyAiPreset] = presetId
            it[keyAiModel] = model
        }
    }

    /** 自定义厂商配置（baseUrl 形如 https://…/v1，模型名必填） */
    val aiCustomBaseUrl: Flow<String> = context.store.data.map { it[keyAiCustomBaseUrl] ?: "" }
    val aiCustomModel: Flow<String> = context.store.data.map { it[keyAiCustomModel] ?: "" }

    suspend fun setAiCustom(baseUrl: String, model: String) {
        context.store.edit {
            it[keyAiCustomBaseUrl] = baseUrl
            it[keyAiCustomModel] = model
        }
    }

    // it-043 O4（走查 C6）：最近一次自检结果持久化——「OK|model|epochMs」或「ERR|message|epochMs」

    private val keyAiLastCheck = aiKey("last_check")

    val aiLastCheck: Flow<String?> = context.store.data.map { it[keyAiLastCheck] }

    suspend fun setAiLastCheck(value: String) {
        context.store.edit { it[keyAiLastCheck] = value }
    }

    // it-077 US-64c：生图轨连接偏好（与聊天轨同命名空间前缀隔离；Key 本体仍走 KeystoreApiKeyStore，
    // 同一厂商 id 一把 Key 双轨共用）。默认硅基流动（免费 Kolors 可先跑通链路）。

    private val keyAiImagePreset = aiKey("image_preset")
    private val keyAiImageModel = aiKey("image_model")
    private val keyAiImageCustomBaseUrl = aiKey("image_custom_base_url")
    private val keyAiImageCustomModel = aiKey("image_custom_model")

    /** 高级面板上次取值（JSON：{modelId: {paramKey: value}}，按模型记忆——拍板①） */
    private val keyAiImageLastParams = aiKey("image_last_params")

    val aiImagePresetId: Flow<String> = context.store.data.map { it[keyAiImagePreset] ?: "siliconflow" }

    /** 选中的生图模型 id（空 = preset 首个生图模型） */
    val aiImageModel: Flow<String> = context.store.data.map { it[keyAiImageModel] ?: "" }

    suspend fun setAiImageConnection(presetId: String, model: String) {
        context.store.edit {
            it[keyAiImagePreset] = presetId
            it[keyAiImageModel] = model
        }
    }

    val aiImageCustomBaseUrl: Flow<String> = context.store.data.map { it[keyAiImageCustomBaseUrl] ?: "" }
    val aiImageCustomModel: Flow<String> = context.store.data.map { it[keyAiImageCustomModel] ?: "" }

    suspend fun setAiImageCustom(baseUrl: String, model: String) {
        context.store.edit {
            it[keyAiImageCustomBaseUrl] = baseUrl
            it[keyAiImageCustomModel] = model
        }
    }

    val aiImageLastParams: Flow<String> = context.store.data.map { it[keyAiImageLastParams] ?: "{}" }

    suspend fun setAiImageLastParams(json: String) {
        context.store.edit { it[keyAiImageLastParams] = json }
    }
}
