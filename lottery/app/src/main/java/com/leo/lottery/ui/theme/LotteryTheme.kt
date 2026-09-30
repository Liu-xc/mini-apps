package com.leo.lottery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.leo.lottery.core.Game

/**
 * 拾彩配色（05-design-system）：家族纸感骨底 + 玩法内容色（球色，不占 accent 预算）
 * + 剧场墨幕。内容色不随主题翻转，深色仅重绘六个语义 token。
 */
object LotteryPalette {
    // Light
    val PaperLight = Color(0xFFF5F4F0)
    val SurfaceLight = Color(0xFFFFFFFF)
    val InkLight = Color(0xFF1A1A18)
    val InkFaintLight = Color(0xFF6A685F)
    val AccentLight = Color(0xFFC7372F)
    val HairlineLight = Color(0xFFE2E0D8)

    // Dark（墨绿纸同构）
    val PaperDark = Color(0xFF15171A)
    val SurfaceDark = Color(0xFF1E2126)
    val InkDark = Color(0xFFF2F1EC)
    val InkFaintDark = Color(0xFFA9A79E)
    val AccentDark = Color(0xFFE0645B)
    val HairlineDark = Color(0xFF33363C)

    // 玩法内容色（球色，主题无关）
    val SsqRed = Color(0xFFD84B40)
    val SsqRedHi = Color(0xFFE8756B)
    val SsqBlue = Color(0xFF3A6FD8)
    val SsqBlueHi = Color(0xFF7AA1F5)
    val DltFront = Color(0xFF2F7A4F)
    val DltFrontHi = Color(0xFF5FA97B)
    val DltBack = Color(0xFFE08A2E)
    val DltBackHi = Color(0xFFF0B56B)

    // 剧场墨幕（05 §4 偏移声明：非纯黑）
    val Theater = Color(0xFF141513)

    /** 购票卡恒定浅纸（导出图不随主题翻转）。 */
    val CardPaper = Color(0xFFFCFBF7)
    val CardInk = Color(0xFF1A1A18)
    val CardInkFaint = Color(0xFF6A685F)
    val CardHairline = Color(0xFFDEDCD2)
}

@Immutable
data class LotteryColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentContent: Color,
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
        hairline = LotteryPalette.HairlineLight,
    )
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp),
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
            accentContent = Color(0xFF15171A),
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
