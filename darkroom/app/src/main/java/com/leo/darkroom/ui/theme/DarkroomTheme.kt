package com.leo.darkroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Global monochrome theme; printed photo cards keep their neutral light-paper palette. */
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
            accentContent = Color(0xFF171717),
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
            accentContent = Color.White,
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
            onPrimary = Color(0xFF171717),
            primaryContainer = Color(0xFF353535),
            onPrimaryContainer = Color(0xFFF0F0EE),
            secondary = DarkroomPalette.InkFaintDark,
            onSecondary = Color(0xFF171717),
            secondaryContainer = Color(0xFF292929),
            onSecondaryContainer = Color(0xFFE0E0DE),
            outline = DarkroomPalette.InkFaintDark,
            outlineVariant = DarkroomPalette.HairlineDark,
            inverseSurface = Color(0xFFE8E8E6),
            inverseOnSurface = Color(0xFF222222),
            inversePrimary = DarkroomPalette.Accent,
            background = DarkroomPalette.PaperDark,
            onBackground = DarkroomPalette.InkDark,
            surface = DarkroomPalette.SurfaceDark,
            onSurface = DarkroomPalette.InkDark,
            surfaceVariant = DarkroomPalette.SurfaceDark,
            onSurfaceVariant = DarkroomPalette.InkFaintDark,
            surfaceContainerLowest = DarkroomPalette.SurfaceDark,
            surfaceContainerLow = DarkroomPalette.SurfaceDark,
            surfaceContainer = DarkroomPalette.SurfaceDark,
            surfaceContainerHigh = DarkroomPalette.SurfaceDark,
            surfaceContainerHighest = DarkroomPalette.SurfaceDark,
        )
    } else {
        lightColorScheme(
            primary = DarkroomPalette.Accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE7E7E5),
            onPrimaryContainer = Color(0xFF232323),
            secondary = DarkroomPalette.InkFaint,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDEDEC),
            onSecondaryContainer = Color(0xFF333333),
            outline = DarkroomPalette.InkFaint,
            outlineVariant = DarkroomPalette.Hairline,
            inverseSurface = Color(0xFF292929),
            inverseOnSurface = DarkroomPalette.InkDark,
            inversePrimary = DarkroomPalette.AccentDark,
            background = DarkroomPalette.Paper,
            onBackground = DarkroomPalette.Ink,
            surface = DarkroomPalette.SurfaceLight,
            onSurface = DarkroomPalette.Ink,
            surfaceVariant = DarkroomPalette.SurfaceLight,
            onSurfaceVariant = DarkroomPalette.InkFaint,
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
