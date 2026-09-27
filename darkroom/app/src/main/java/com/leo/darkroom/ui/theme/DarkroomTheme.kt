package com.leo.darkroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 全局主题：Material 3 × 暗房相纸 token（specs/05-design-system.md）。
 * 动效基调见 [EditorialMotion]；相纸卡恒定白（个性偏移声明见 05 与 DESIGN.md §1 表）。
 */
@Composable
fun DarkroomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) {
        EditorialColors(
            paper = DarkroomPalette.PaperDark,
            surface = DarkroomPalette.SurfaceDark,
            ink = DarkroomPalette.InkDark,
            inkFaint = DarkroomPalette.InkFaintDark,
            accent = DarkroomPalette.AccentDark,
            accentContent = DarkroomPalette.PaperDark,
            hairline = DarkroomPalette.HairlineDark,
            cardPaper = DarkroomPalette.CardPaper,
            cardInk = DarkroomPalette.CardInk,
            cardInkFaint = DarkroomPalette.CardInkFaint,
            cardAccent = DarkroomPalette.CardAccent,
            cardHairline = DarkroomPalette.CardHairline,
        )
    } else {
        EditorialColors(
            paper = DarkroomPalette.Paper,
            surface = DarkroomPalette.SurfaceLight,
            ink = DarkroomPalette.Ink,
            inkFaint = DarkroomPalette.InkFaint,
            accent = DarkroomPalette.Accent,
            accentContent = DarkroomPalette.SurfaceLight,
            hairline = DarkroomPalette.Hairline,
            cardPaper = DarkroomPalette.CardPaper,
            cardInk = DarkroomPalette.CardInk,
            cardInkFaint = DarkroomPalette.CardInkFaint,
            cardAccent = DarkroomPalette.CardAccent,
            cardHairline = DarkroomPalette.CardHairline,
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = DarkroomPalette.AccentDark,
            onPrimary = DarkroomPalette.PaperDark,
            outline = DarkroomPalette.InkFaintDark,
            primaryContainer = Color(0xFF3A2A1A),
            onPrimaryContainer = Color(0xFFF3D9C2),
            secondaryContainer = Color(0xFF23261C),
            onSecondaryContainer = Color(0xFFD2CEC0),
            background = DarkroomPalette.PaperDark,
            onBackground = DarkroomPalette.InkDark,
            surface = DarkroomPalette.SurfaceDark,
            onSurface = DarkroomPalette.InkDark,
            surfaceVariant = DarkroomPalette.SurfaceDark,
            onSurfaceVariant = DarkroomPalette.InkFaintDark,
            outlineVariant = DarkroomPalette.HairlineDark,
            secondary = DarkroomPalette.AccentDark,
            onSecondary = DarkroomPalette.PaperDark,
            // 容器色阶全对齐暗房墨纸，防 M3 基线淡紫回跌（wardrobe it-029 C2 先例）
            surfaceContainerLowest = DarkroomPalette.SurfaceDark,
            surfaceContainerLow = DarkroomPalette.SurfaceDark,
            surfaceContainer = DarkroomPalette.SurfaceDark,
            surfaceContainerHigh = DarkroomPalette.SurfaceDark,
            surfaceContainerHighest = DarkroomPalette.SurfaceDark,
        )
    } else {
        lightColorScheme(
            primary = DarkroomPalette.Accent,
            onPrimary = DarkroomPalette.SurfaceLight,
            outline = DarkroomPalette.InkFaint,
            primaryContainer = Color(0xFFF4E0D0),
            onPrimaryContainer = Color(0xFF6E300B),
            secondaryContainer = Color(0xFFEFEBDD),
            onSecondaryContainer = Color(0xFF43402F),
            background = DarkroomPalette.Paper,
            onBackground = DarkroomPalette.Ink,
            surface = DarkroomPalette.SurfaceLight,
            onSurface = DarkroomPalette.Ink,
            surfaceVariant = DarkroomPalette.SurfaceLight,
            onSurfaceVariant = DarkroomPalette.InkFaint,
            outlineVariant = DarkroomPalette.Hairline,
            secondary = DarkroomPalette.Accent,
            onSecondary = DarkroomPalette.SurfaceLight,
            surfaceContainerLowest = DarkroomPalette.SurfaceLight,
            surfaceContainerLow = DarkroomPalette.SurfaceLight,
            surfaceContainer = DarkroomPalette.SurfaceLight,
            surfaceContainerHigh = DarkroomPalette.SurfaceLight,
            surfaceContainerHighest = DarkroomPalette.SurfaceLight,
        )
    }

    CompositionLocalProvider(LocalEditorialColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EditorialTypography,
            shapes = DarkroomShapes,
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
