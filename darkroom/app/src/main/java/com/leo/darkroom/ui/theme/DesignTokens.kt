package com.leo.darkroom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 暗房相纸调色板（token 结构对齐 DESIGN.md §2.1，个性值声明见 specs/05-design-system.md）：
 * 页面 = 暖米纸（家族淡绿纸感微调偏暖）；卡片 = 恒定相纸白（拟物焦点，昼夜不翻色，
 * 同 xiangqi「棋盘是唯一拟物焦点」先例）；accent = 日期章橙，只做强调。
 */
object DarkroomPalette {
    // 浅色（暖米纸）
    val Paper = Color(0xFFF6F4EB)
    val SurfaceLight = Color(0xFFFDFCF6)
    val Ink = Color(0xFF27231A)
    val InkFaint = Color(0xFF6F695B)
    val Accent = Color(0xFF9E4513)
    val AccentStrong = Color(0xFF83390F)
    val Hairline = Color(0xFFE4DFD0)

    // 深色（暗房墨绿纸）
    val PaperDark = Color(0xFF14160F)
    val SurfaceDark = Color(0xFF1D2016)
    val InkDark = Color(0xFFEDE9DC)
    val InkFaintDark = Color(0xFFA59E8B)
    val AccentDark = Color(0xFFE09A5E)
    val HairlineDark = Color(0xFF2A2D20)

    // 相纸卡（恒定，昼夜不翻色：实物拍立得不分昼夜都是白纸）
    val CardPaper = Color(0xFFFDFCF6)
    val CardInk = Color(0xFF27231A)
    val CardInkFaint = Color(0xFF8A8375)
    val CardAccent = Color(0xFFC05A1A)
    val CardHairline = Color(0xFFE8E3D5)
}

@Immutable
data class EditorialColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    /** 日期章橙：强调色，屏幕占比 <10%（DESIGN.md §2.1） */
    val accent: Color,
    /** accent 底上的高对比前景（按钮文字） */
    val accentContent: Color,
    val hairline: Color,
    /** 相纸卡恒定色组 */
    val cardPaper: Color,
    val cardInk: Color,
    val cardInkFaint: Color,
    val cardAccent: Color,
    val cardHairline: Color,
)

val LocalEditorialColors = staticCompositionLocalOf {
    EditorialColors(
        paper = DarkroomPalette.Paper,
        surface = DarkroomPalette.SurfaceLight,
        ink = DarkroomPalette.Ink,
        inkFaint = DarkroomPalette.InkFaint,
        accent = DarkroomPalette.Accent,
        accentContent = Color(0xFFFDFCF6),
        hairline = DarkroomPalette.Hairline,
        cardPaper = DarkroomPalette.CardPaper,
        cardInk = DarkroomPalette.CardInk,
        cardInkFaint = DarkroomPalette.CardInkFaint,
        cardAccent = DarkroomPalette.CardAccent,
        cardHairline = DarkroomPalette.CardHairline,
    )
}

/** 便捷取用编辑风专有色 */
@Composable
fun editorialColors(): EditorialColors = LocalEditorialColors.current
