package com.leo.eats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 全局主题：Material 3 × 餐牌手帐风 token（specs/05-design-system.md）。
 * 动效基调见 [EatsMotion]。
 */
@Composable
fun EatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) {
        MenuColors(
            paper = EatsPalette.PaperDark,
            surface = EatsPalette.SurfaceDark,
            ink = EatsPalette.InkDark,
            inkFaint = EatsPalette.InkFaintDark,
            accent = EatsPalette.AccentDark,
            hairline = EatsPalette.HairlineDark,
            restaurant = EatsPalette.RestaurantDark,
            takeout = EatsPalette.TakeoutDark,
            homeCook = EatsPalette.HomeCookDark,
            eat = EatsPalette.EatDark,
            drink = EatsPalette.DrinkDark,
            play = EatsPalette.PlayDark,
        )
    } else {
        MenuColors(
            paper = EatsPalette.Paper,
            surface = EatsPalette.SurfaceLight,
            ink = EatsPalette.Ink,
            inkFaint = EatsPalette.InkFaint,
            accent = EatsPalette.Accent,
            hairline = EatsPalette.Hairline,
            restaurant = EatsPalette.Restaurant,
            takeout = EatsPalette.Takeout,
            homeCook = EatsPalette.HomeCook,
            eat = EatsPalette.Eat,
            drink = EatsPalette.Drink,
            play = EatsPalette.Play,
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = EatsPalette.AccentDark,
            onPrimary = EatsPalette.PaperDark,
            primaryContainer = Color(0xFF1E4230),
            onPrimaryContainer = Color(0xFFC9EBD4),
            secondaryContainer = Color(0xFF243127),
            onSecondaryContainer = Color(0xFFC7D6C5),
            background = EatsPalette.PaperDark,
            onBackground = EatsPalette.InkDark,
            surface = EatsPalette.SurfaceDark,
            onSurface = EatsPalette.InkDark,
            surfaceVariant = EatsPalette.SurfaceDark,
            onSurfaceVariant = EatsPalette.InkFaintDark,
            outlineVariant = EatsPalette.HairlineDark,
            secondary = EatsPalette.AccentDark,
            onSecondary = EatsPalette.PaperDark,
        )
    } else {
        lightColorScheme(
            primary = EatsPalette.Accent,
            onPrimary = EatsPalette.Paper,
            primaryContainer = Color(0xFFDDF1E3),
            onPrimaryContainer = Color(0xFF1E5B36),
            secondaryContainer = Color(0xFFEAF3EC),
            onSecondaryContainer = Color(0xFF37503C),
            background = EatsPalette.Paper,
            onBackground = EatsPalette.Ink,
            surface = EatsPalette.SurfaceLight,
            onSurface = EatsPalette.Ink,
            surfaceVariant = EatsPalette.SurfaceLight,
            onSurfaceVariant = EatsPalette.InkFaint,
            outlineVariant = EatsPalette.Hairline,
            secondary = EatsPalette.Accent,
            onSecondary = EatsPalette.Paper,
        )
    }

    CompositionLocalProvider(LocalMenuColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EditorialTypography,
            shapes = EatsShapes,
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
