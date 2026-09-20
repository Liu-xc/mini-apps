package com.leo.wardrobe.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recapStore by preferencesDataStore("wardrobe_recap_prefs")

/** 「好久没穿」提醒设置（it-018 阶段C）：独立 DataStore，不进 wardrobe.json */
data class RecapReminderPrefs(
    val enabled: Boolean = false,
    val days: Int = 90,
    /** 上次提醒过的单品：候选未用尽前不重复推 */
    val lastNotifiedItemId: String? = null,
)

class RecapPrefsStore(private val context: Context) {

    private val keyEnabled = booleanPreferencesKey("reminder_enabled")
    private val keyDays = intPreferencesKey("reminder_days")
    private val keyLast = stringPreferencesKey("last_notified_item_id")

    val flow: Flow<RecapReminderPrefs> = context.recapStore.data.map { p ->
        RecapReminderPrefs(
            enabled = p[keyEnabled] ?: false,
            days = p[keyDays] ?: 90,
            lastNotifiedItemId = p[keyLast],
        )
    }

    suspend fun snapshot(): RecapReminderPrefs = flow.first()

    suspend fun set(enabled: Boolean, days: Int) {
        context.recapStore.edit {
            it[keyEnabled] = enabled
            it[keyDays] = days
        }
    }

    suspend fun setLastNotified(itemId: String) {
        context.recapStore.edit { it[keyLast] = itemId }
    }
}
