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
    val Paper = Color(0xFFF5F9F3)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1D2620)
    val InkFaint = Color(0xFF747F75)
    val Accent = Color(0xFF429E68)
    val AccentStrong = Color(0xFF1D6845)
    val Hairline = Color(0xFFE3EBE0)

    // 深色（墨纸）
    val PaperDark = Color(0xFF10150F)
    val SurfaceDark = Color(0xFF1B231B)
    val InkDark = Color(0xFFEAF2E8)
    val InkFaintDark = Color(0xFF94A294)
    val AccentDark = Color(0xFF74C790)
    val HairlineDark = Color(0xFF273127)
}

@Immutable
data class EditorialColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    /** Brand green for decorative graphics only. */
    val accent: Color,
    /** High-contrast foreground for accent text and action states. */
    val accentContent: Color,
    val hairline: Color,
)

val LocalEditorialColors = staticCompositionLocalOf {
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

/** 便捷取用编辑风专有色 */
@Composable
fun editorialColors(): EditorialColors = LocalEditorialColors.current
