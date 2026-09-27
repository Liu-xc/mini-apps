package com.leo.darkroom.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leo.darkroom.develop.DevelopSpeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "darkroom_prefs")

/**
 * 用户设置（it-001 US-2/US-3）：显影速度档、甩一甩开关、卡脚水印默认值。
 */
class PrefsStore(private val context: Context) {

    private object Keys {
        val SPEED = stringPreferencesKey("develop_speed")
        val SHAKE = booleanPreferencesKey("shake_enabled")
        val WATERMARK = booleanPreferencesKey("watermark_default")
    }

    val speed: Flow<DevelopSpeed> = context.dataStore.data.map { prefs ->
        DevelopSpeed.fromOrDefault(prefs[Keys.SPEED])
    }

    val shakeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHAKE] ?: true
    }

    val watermarkDefault: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WATERMARK] ?: true
    }

    suspend fun setSpeed(value: DevelopSpeed) {
        context.dataStore.edit { it[Keys.SPEED] = value.name }
    }

    suspend fun setShakeEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SHAKE] = value }
    }

    suspend fun setWatermarkDefault(value: Boolean) {
        context.dataStore.edit { it[Keys.WATERMARK] = value }
    }
}
