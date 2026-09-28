package com.leo.darkroom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 中性石墨主题；照片本身的可选影调不属于界面配色。 */
object DarkroomPalette {
    // Light interface
    val Paper = Color(0xFFF3F3F1)
    val SurfaceLight = Color(0xFFFBFBFA)
    val Ink = Color(0xFF191919)
    val InkFaint = Color(0xFF5B5B5B)
    val Accent = Color(0xFF414141)
    val Hairline = Color(0xFFD8D8D6)

    // Dark interface
    val PaperDark = Color(0xFF151515)
    val SurfaceDark = Color(0xFF212121)
    val InkDark = Color(0xFFF2F2F0)
    val InkFaintDark = Color(0xFFB9B9B6)
    val AccentDark = Color(0xFFD0D0CE)
    val HairlineDark = Color(0xFF383838)

    // Printed card stays a neutral light paper in either interface theme.
    val CardPaper = Color(0xFFFBFBFA)
    val CardInk = Color(0xFF191919)
    val CardInkFaint = Color(0xFF626262)
    val CardAccent = Color(0xFF414141)
    val CardHairline = Color(0xFFDEDEDC)
}

@Immutable
data class EditorialColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    /** 中性强调灰：只用于选择、进度和小范围交互反馈。 */
    val accent: Color,
    /** accent 底上的高对比前景。 */
    val accentContent: Color,
    val hairline: Color,
    /** 相纸卡恒定色组。 */
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
        accentContent = Color.White,
        hairline = DarkroomPalette.Hairline,
        cardPaper = DarkroomPalette.CardPaper,
        cardInk = DarkroomPalette.CardInk,
        cardInkFaint = DarkroomPalette.CardInkFaint,
        cardAccent = DarkroomPalette.CardAccent,
        cardHairline = DarkroomPalette.CardHairline,
    )
}

@Composable
fun editorialColors(): EditorialColors = LocalEditorialColors.current
