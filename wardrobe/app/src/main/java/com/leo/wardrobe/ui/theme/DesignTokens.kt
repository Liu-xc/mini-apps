package com.leo.wardrobe.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 时装编辑风调色板，token 定义见 specs/05-design-system.md。
 * M3 colorScheme 承载标准槽位，编辑风专有色经 LocalEditorialColors 下发。
 */
object WardrobePalette {
    // 浅色（米白纸感）
    val Paper = Color(0xFFFAF7F2)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A1A1A)
    val InkFaint = Color(0xFF8A857D)
    val Accent = Color(0xFFB5432A)
    val Hairline = Color(0xFFE8E2D9)

    // 深色（墨纸）
    val PaperDark = Color(0xFF141312)
    val SurfaceDark = Color(0xFF1F1D1B)
    val InkDark = Color(0xFFF2EFE9)
    val InkFaintDark = Color(0xFF9C968C)
    val AccentDark = Color(0xFFD96A50)
    val HairlineDark = Color(0xFF2C2926)
}

@Immutable
data class EditorialColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    val accent: Color,
    val hairline: Color,
)

val LocalEditorialColors = staticCompositionLocalOf {
    EditorialColors(
        paper = WardrobePalette.Paper,
        surface = WardrobePalette.SurfaceLight,
        ink = WardrobePalette.Ink,
        inkFaint = WardrobePalette.InkFaint,
        accent = WardrobePalette.Accent,
        hairline = WardrobePalette.Hairline,
    )
}

/** 便捷取用编辑风专有色 */
@Composable
fun editorialColors(): EditorialColors = LocalEditorialColors.current
