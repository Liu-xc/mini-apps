package com.leo.wardrobe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 全局主题：Material 3 × 时装编辑风 token（specs/05-design-system.md）。
 * 动效基调见 [EditorialMotion]；Expressive motionScheme 待 material3 1.5 稳定后接入（ADR-004）。
 */
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
            hairline = WardrobePalette.HairlineDark,
        )
    } else {
        EditorialColors(
            paper = WardrobePalette.Paper,
            surface = WardrobePalette.SurfaceLight,
            ink = WardrobePalette.Ink,
            inkFaint = WardrobePalette.InkFaint,
            accent = WardrobePalette.Accent,
            hairline = WardrobePalette.Hairline,
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = WardrobePalette.AccentDark,
            onPrimary = WardrobePalette.PaperDark,
            primaryContainer = Color(0xFF1E4230),
            onPrimaryContainer = Color(0xFFCDEBD6),
            secondaryContainer = Color(0xFF243127),
            onSecondaryContainer = Color(0xFFC7D6C5),
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
            primary = WardrobePalette.Accent,
            onPrimary = WardrobePalette.Paper,
            primaryContainer = Color(0xFFDDF0E4),
            onPrimaryContainer = Color(0xFF1E5B36),
            secondaryContainer = Color(0xFFEAF3EC),
            onSecondaryContainer = Color(0xFF37503C),
            background = WardrobePalette.Paper,
            onBackground = WardrobePalette.Ink,
            surface = WardrobePalette.SurfaceLight,
            onSurface = WardrobePalette.Ink,
            surfaceVariant = WardrobePalette.SurfaceLight,
            onSurfaceVariant = WardrobePalette.InkFaint,
            outlineVariant = WardrobePalette.Hairline,
            secondary = WardrobePalette.Accent,
            onSecondary = WardrobePalette.Paper,
            // it-029 C2：M3 容器色阶未定义会跌回基线淡紫（导航/弹层），全部对齐纸面白
            surfaceContainerLowest = WardrobePalette.SurfaceLight,
            surfaceContainerLow = WardrobePalette.SurfaceLight,
            surfaceContainer = WardrobePalette.SurfaceLight,
            surfaceContainerHigh = WardrobePalette.SurfaceLight,
            surfaceContainerHighest = WardrobePalette.SurfaceLight,
        )
    }

    CompositionLocalProvider(LocalEditorialColors provides palette) {
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
