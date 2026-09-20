package com.leo.eats.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recapStore by preferencesDataStore("recap_prefs")

/** 回忆提醒设置（it-007 阶段B）：独立 DataStore 文件，不进 eats.json（specs/03 KV 小节） */
data class RecapReminderPrefs(
    val enabled: Boolean = false,
    val days: Int = 90,
    /** 上次提醒过的食堂：候选未用尽前不重复推 */
    val lastNotifiedPlaceId: String? = null,
)

class RecapPrefsStore(private val context: Context) {

    private val keyEnabled = booleanPreferencesKey("reminder_enabled")
    private val keyDays = intPreferencesKey("reminder_days")
    private val keyLast = stringPreferencesKey("last_notified_place_id")

    val flow: Flow<RecapReminderPrefs> = context.recapStore.data.map { p ->
        RecapReminderPrefs(
            enabled = p[keyEnabled] ?: false,
            days = p[keyDays] ?: 90,
            lastNotifiedPlaceId = p[keyLast],
        )
    }

    /** Worker 内同步读取 */
    suspend fun snapshot(): RecapReminderPrefs = flow.first()

    suspend fun set(enabled: Boolean, days: Int) {
        context.recapStore.edit {
            it[keyEnabled] = enabled
            it[keyDays] = days
        }
    }

    suspend fun setLastNotified(placeId: String) {
        context.recapStore.edit { it[keyLast] = placeId }
    }
}
