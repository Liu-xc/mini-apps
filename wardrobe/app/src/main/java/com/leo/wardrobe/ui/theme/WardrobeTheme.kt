package com.leo.wardrobe.ui.theme

import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * it-070 US-61：全局生效的深色判定（主题设置三态收敛后唯一取值处）。
 * 局部预览/长图配色等直读 `isSystemInDarkTheme()` 的地方一律改读本 Local，
 * 否则强制亮/暗时局部取色会跟系统走偏。
 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/**
 * 全局主题：Material 3 × 黑白灰时装编辑风 token（specs/05-design-system.md）。
 * 动效基调见 [EditorialMotion]；Expressive motionScheme 待 material3 1.5 稳定后接入（ADR-004）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WardrobeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) {
        EditorialColors(
            paper = WardrobePalette.PaperDark,
            surface = WardrobePalette.SurfaceDark,
            ink = WardrobePalette.InkDark,
            inkFaint = WardrobePalette.InkFaintDark,
            accent = WardrobePalette.AccentDark,
            accentContent = WardrobePalette.AccentDark,
            hairline = WardrobePalette.HairlineDark,
        )
    } else {
        EditorialColors(
            paper = WardrobePalette.Paper,
            surface = WardrobePalette.SurfaceLight,
            ink = WardrobePalette.Ink,
            inkFaint = WardrobePalette.InkFaint,
            accent = WardrobePalette.Accent,
            accentContent = WardrobePalette.AccentStrong,
            hairline = WardrobePalette.Hairline,
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = WardrobePalette.AccentDark,
            onPrimary = WardrobePalette.PaperDark,
            outline = WardrobePalette.InkFaintDark,
            primaryContainer = Color(0xFF333330),
            onPrimaryContainer = WardrobePalette.InkDark,
            secondaryContainer = Color(0xFF2A2A28),
            onSecondaryContainer = WardrobePalette.InkDark,
            background = WardrobePalette.PaperDark,
            onBackground = WardrobePalette.InkDark,
            surface = WardrobePalette.SurfaceDark,
            onSurface = WardrobePalette.InkDark,
            surfaceVariant = WardrobePalette.SurfaceDark,
            onSurfaceVariant = WardrobePalette.InkFaintDark,
            outlineVariant = WardrobePalette.HairlineDark,
            secondary = WardrobePalette.AccentDark,
            onSecondary = WardrobePalette.PaperDark,
            // it-029 C2：M3 容器色阶未定义会跌回基线淡紫（导航/弹层），全部对齐墨绿纸面
            surfaceContainerLowest = WardrobePalette.SurfaceDark,
            surfaceContainerLow = WardrobePalette.SurfaceDark,
            surfaceContainer = WardrobePalette.SurfaceDark,
            surfaceContainerHigh = WardrobePalette.SurfaceDark,
            surfaceContainerHighest = WardrobePalette.SurfaceDark,
        )
    } else {
        lightColorScheme(
            primary = WardrobePalette.AccentStrong,
            onPrimary = WardrobePalette.SurfaceLight,
            outline = WardrobePalette.InkFaint,
            primaryContainer = Color(0xFFE5E5E1),
            onPrimaryContainer = WardrobePalette.Ink,
            secondaryContainer = Color(0xFFEEEEEB),
            onSecondaryContainer = WardrobePalette.Ink,
            background = WardrobePalette.Paper,
            onBackground = WardrobePalette.Ink,
            surface = WardrobePalette.SurfaceLight,
            onSurface = WardrobePalette.Ink,
            surfaceVariant = WardrobePalette.SurfaceLight,
            onSurfaceVariant = WardrobePalette.InkFaint,
            outlineVariant = WardrobePalette.Hairline,
            secondary = WardrobePalette.AccentStrong,
            onSecondary = WardrobePalette.SurfaceLight,
            // it-029 C2：M3 容器色阶未定义会跌回基线淡紫（导航/弹层），全部对齐纸面白
            surfaceContainerLowest = WardrobePalette.SurfaceLight,
            surfaceContainerLow = WardrobePalette.SurfaceLight,
            surfaceContainer = WardrobePalette.SurfaceLight,
            surfaceContainerHigh = WardrobePalette.SurfaceLight,
            surfaceContainerHighest = WardrobePalette.SurfaceLight,
        )
    }

    CompositionLocalProvider(
        LocalEditorialColors provides palette,
        LocalAppDarkTheme provides darkTheme,
        // it-073：全站禁用 Android 12+ 滚动 overscroll 拉伸（「弹簧绳」）效果——
        // 到头即停，Leo 偏好；fling/吸附行为不受影响
        LocalOverscrollConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EditorialTypography,
            shapes = WardrobeShapes,
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
