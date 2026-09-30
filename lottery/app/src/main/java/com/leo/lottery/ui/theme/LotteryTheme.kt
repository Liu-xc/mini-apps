package com.leo.lottery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.leo.lottery.core.Game

/**
 * 拾彩配色（it-002 / 05-design-system）：「福彩演播室」身份——
 * 暖票纸底 + 福彩红 + 金线（日间票据感）/ 暗夜演播厅（深色同构）；
 * 开奖剧场恒用深蓝舞台（Theater，不随主题）。玩法内容色（球色）主题无关。
 */
object LotteryPalette {
    // Light：票纸日间
    val PaperLight = Color(0xFFF6F5F1)
    val SurfaceLight = Color(0xFFFDFCF8)
    val InkLight = Color(0xFF252720)
    val InkFaintLight = Color(0xFF6B6E64)
    val AccentLight = Color(0xFFB83B37)
    val GoldLight = Color(0xFF9B895E)
    val HairlineLight = Color(0xFFDDDED5)

    // Dark：暗夜演播厅
    val PaperDark = Color(0xFF171C19)
    val SurfaceDark = Color(0xFF222A24)
    val InkDark = Color(0xFFEEEFE6)
    val InkFaintDark = Color(0xFFAFB7AA)
    val AccentDark = Color(0xFFE85545)
    val GoldDark = Color(0xFFD6B35F)
    val HairlineDark = Color(0xFF3C463E)

    // 玩法内容色（球色，主题无关；广播级高饱和）
    val SsqRed = Color(0xFFE0342B)
    val SsqRedHi = Color(0xFFF06A55)
    val SsqBlue = Color(0xFF2264D8)
    val SsqBlueHi = Color(0xFF5C93F0)
    val DltFront = Color(0xFF2E8A54)
    val DltFrontHi = Color(0xFF63B585)
    val DltBack = Color(0xFFE8962E)
    val DltBackHi = Color(0xFFF4BC6B)

    // 演播室舞台（剧场恒定，不随主题）
    val Theater = Color(0xFF151D20)
    val TheaterHi = Color(0xFF293538)
    val StageGold = Color(0xFF9FAAA7)
    val StageInk = Color(0xFFF3F2E9)
    val StageFaint = Color(0xFFAAB6B5)

    /** 购票卡恒定浅纸（导出图不随主题翻转）。 */
    val CardPaper = Color(0xFFFDFCF8)
    val CardInk = Color(0xFF252720)
    val CardInkFaint = Color(0xFF6B6E64)
    val CardHairline = Color(0xFFDDDED5)
    val CardRed = Color(0xFFB83B37)
    val CardGold = Color(0xFF9B895E)
}

@Immutable
data class LotteryColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentContent: Color,
    val gold: Color,
    val hairline: Color,
) {
    fun zone1(game: Game): Color = when (game) {
        Game.SSQ -> LotteryPalette.SsqRed
        Game.DLT -> LotteryPalette.DltFront
    }

    fun zone1Hi(game: Game): Color = when (game) {
        Game.SSQ -> LotteryPalette.SsqRedHi
        Game.DLT -> LotteryPalette.DltFrontHi
    }

    fun zone2(game: Game): Color = when (game) {
        Game.SSQ -> LotteryPalette.SsqBlue
        Game.DLT -> LotteryPalette.DltBack
    }

    fun zone2Hi(game: Game): Color = when (game) {
        Game.SSQ -> LotteryPalette.SsqBlueHi
        Game.DLT -> LotteryPalette.DltBackHi
    }
}

val LocalLotteryColors = staticCompositionLocalOf {
    LotteryColors(
        paper = LotteryPalette.PaperLight,
        surface = LotteryPalette.SurfaceLight,
        ink = LotteryPalette.InkLight,
        inkFaint = LotteryPalette.InkFaintLight,
        accent = LotteryPalette.AccentLight,
        accentContent = Color.White,
        gold = LotteryPalette.GoldLight,
        hairline = LotteryPalette.HairlineLight,
    )
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.2.sp),
)

/** 剧场大号读数（广播记分牌）。 */
val StageReadout = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 44.sp,
    letterSpacing = 1.sp,
)

@Composable
fun LotteryTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) {
        LotteryColors(
            paper = LotteryPalette.PaperDark,
            surface = LotteryPalette.SurfaceDark,
            ink = LotteryPalette.InkDark,
            inkFaint = LotteryPalette.InkFaintDark,
            accent = LotteryPalette.AccentDark,
            accentContent = Color(0xFF171C19),
            gold = LotteryPalette.GoldDark,
            hairline = LotteryPalette.HairlineDark,
        )
    } else {
        LotteryColors(
            paper = LotteryPalette.PaperLight,
            surface = LotteryPalette.SurfaceLight,
            ink = LotteryPalette.InkLight,
            inkFaint = LotteryPalette.InkFaintLight,
            accent = LotteryPalette.AccentLight,
            accentContent = Color.White,
            gold = LotteryPalette.GoldLight,
            hairline = LotteryPalette.HairlineLight,
        )
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentContent,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.inkFaint,
            outline = colors.hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentContent,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.inkFaint,
            outline = colors.hairline,
        )
    }

    CompositionLocalProvider(
        LocalLotteryColors provides colors,
        LocalContentColor provides colors.ink,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
