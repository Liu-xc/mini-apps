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
    // 浅色（偏暖纸白）
    val Paper = Color(0xFFF7F7F5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111111)
    val InkFaint = Color(0xFF6B6B67)
    /** 中性主强调；照片和成品图保留原色，UI chrome 不再使用品牌绿。 */
    val Accent = Color(0xFF111111)
    val AccentStrong = Color(0xFF111111)
    val Hairline = Color(0xFFD9D9D4)

    // 深色（炭黑纸）
    val PaperDark = Color(0xFF111110)
    val SurfaceDark = Color(0xFF1B1B1A)
    val InkDark = Color(0xFFF2F2EF)
    val InkFaintDark = Color(0xFFA6A6A0)
    val AccentDark = Color(0xFFF2F2EF)
    val HairlineDark = Color(0xFF393936)
}

@Immutable
data class EditorialColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    /** 中性主强调，保留独立 token 以维持语义结构。 */
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
