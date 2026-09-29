package com.leo.darkroom.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leo.darkroom.card.FrameStyle
import com.leo.darkroom.card.TitleFontStyle
import com.leo.darkroom.card.TitleSizeOption
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "darkroom_prefs")

/**
 * 用户设置（it-001 US-2/US-3，it-007 US-14，it-010 US-16/17）：显影速度档、显影模式、
 * 甩一甩开关、创作选项默认值（脚注文案、相纸框型、标题字体/字号）——
 * 上一次 W3 的选择即下一次会话的默认，与模式同语义。
 */
class PrefsStore(private val context: Context) {

    private object Keys {
        val SPEED = stringPreferencesKey("develop_speed")
        val MODE = stringPreferencesKey("develop_mode")
        val SHAKE = booleanPreferencesKey("shake_enabled")
        // it-010 迁移：旧 watermark_default=false → 脚注默认空串（不印）
        val WATERMARK_LEGACY = booleanPreferencesKey("watermark_default")
        val FOOTER = stringPreferencesKey("footer_default")
        val FRAME = stringPreferencesKey("frame_style")
        val TITLE_FONT = stringPreferencesKey("title_font")
        val TITLE_SIZE = stringPreferencesKey("title_size")
    }

    val speed: Flow<DevelopSpeed> = context.dataStore.data.map { prefs ->
        DevelopSpeed.fromOrDefault(prefs[Keys.SPEED])
    }

    /** 上次使用的显影模式，跨会话保留；缺省拍立得 */
    val mode: Flow<DevelopMode> = context.dataStore.data.map { prefs ->
        DevelopMode.fromName(prefs[Keys.MODE])
    }

    val shakeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHAKE] ?: true
    }

    /** 卡脚脚注默认文案：空串 = 不印（承接旧水印开关语义） */
    val footerDefault: Flow<String> = context.dataStore.data.map { prefs ->
        when {
            prefs.contains(Keys.FOOTER) -> prefs[Keys.FOOTER].orEmpty()
            prefs[Keys.WATERMARK_LEGACY] == false -> ""
            else -> com.leo.darkroom.card.CardSpec.DEFAULT_FOOTER
        }
    }

    val frameDefault: Flow<FrameStyle> = context.dataStore.data.map { prefs ->
        FrameStyle.fromName(prefs[Keys.FRAME])
    }

    val titleFontDefault: Flow<TitleFontStyle> = context.dataStore.data.map { prefs ->
        TitleFontStyle.fromName(prefs[Keys.TITLE_FONT])
    }

    val titleSizeDefault: Flow<TitleSizeOption> = context.dataStore.data.map { prefs ->
        TitleSizeOption.fromName(prefs[Keys.TITLE_SIZE])
    }

    suspend fun setSpeed(value: DevelopSpeed) {
        context.dataStore.edit { it[Keys.SPEED] = value.name }
    }

    suspend fun setMode(value: DevelopMode) {
        context.dataStore.edit { it[Keys.MODE] = value.name }
    }

    suspend fun setShakeEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SHAKE] = value }
    }

    suspend fun setFooterDefault(value: String) {
        context.dataStore.edit { it[Keys.FOOTER] = value }
    }

    suspend fun setFrameDefault(value: FrameStyle) {
        context.dataStore.edit { it[Keys.FRAME] = value.name }
    }

    suspend fun setTitleFontDefault(value: TitleFontStyle) {
        context.dataStore.edit { it[Keys.TITLE_FONT] = value.name }
    }

    suspend fun setTitleSizeDefault(value: TitleSizeOption) {
        context.dataStore.edit { it[Keys.TITLE_SIZE] = value.name }
    }
}
