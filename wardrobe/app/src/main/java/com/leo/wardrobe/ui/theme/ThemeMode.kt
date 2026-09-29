package com.leo.wardrobe.ui.theme

/**
 * it-070 US-61 外观主题三态：跟随系统 / 亮色 / 暗色。
 * storage 值即 DataStore `theme_mode` 的持久化格式（未知值回落 [SYSTEM]）。
 */
enum class ThemeMode(val storage: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String): ThemeMode =
            entries.firstOrNull { it.storage == value } ?: SYSTEM
    }
}
