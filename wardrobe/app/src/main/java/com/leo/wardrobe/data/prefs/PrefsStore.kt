package com.leo.wardrobe.data.prefs

import android.content.Context
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
 */
class PrefsStore(private val context: Context) {

    private val keyPerson = stringPreferencesKey("current_person")
    private val keyPersonNote = stringPreferencesKey("person_note")

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
}
