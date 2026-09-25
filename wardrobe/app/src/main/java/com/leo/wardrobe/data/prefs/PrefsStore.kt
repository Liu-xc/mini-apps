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
class PrefsStore(private val context: Context) {

    private val keyPerson = stringPreferencesKey("current_person")
    private val keyPersonNote = stringPreferencesKey("person_note")
    private val keyExportSelections = stringSetPreferencesKey("export_selections")
    private val keyCustomPrompt = stringPreferencesKey("custom_prompt")
    private val keyCoachSlots = booleanPreferencesKey("coach_slots_shown")

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

    /** 导出面板五维选择（it-011 O8：记住上次），存储格式 "key=value" */
    val exportSelections: Flow<Map<String, String>> = context.store.data.map { prefs ->
        prefs[keyExportSelections].orEmpty()
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0] to it[1] }
    }

    suspend fun saveExportSelections(selections: Map<String, String>) {
        context.store.edit { prefs ->
            prefs[keyExportSelections] = selections.entries
                .filter { it.value.isNotBlank() }
                .map { (k, v) -> "$k=$v" }
                .toSet()
        }
    }

    /** it-012：DataStore 首发射完成标志——修 exportSelections 恢复竞态（首帧空值不再吞掉记忆） */
    val exportSelectionsReady: Flow<Boolean> = context.store.data.map { true }

    /** W1 格位滑动 coach 动画（it-011 O6）：仅首次进入演示一次 */
    val coachSlotsShown: Flow<Boolean> = context.store.data.map { it[keyCoachSlots] ?: false }

    suspend fun markCoachSlotsShown() {
        context.store.edit { it[keyCoachSlots] = true }
    }

    // it-041 US-41a：AI 模型连接偏好（Key 本体走 KeystoreApiKeyStore，不进 DataStore）

    private val keyAiPreset = stringPreferencesKey("ai_preset")
    private val keyAiModel = stringPreferencesKey("ai_model")
    private val keyAiCustomBaseUrl = stringPreferencesKey("ai_custom_base_url")
    private val keyAiCustomModel = stringPreferencesKey("ai_custom_model")

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
}
