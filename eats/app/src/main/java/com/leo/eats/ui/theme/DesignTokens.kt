package com.leo.eats.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.leo.eats.domain.model.PlaceKind

/**
 * 餐牌手帐风调色板，token 定义见 specs/05-design-system.md。
 * M3 colorScheme 承载标准槽位，手帐风专有色经 LocalMenuColors 下发。
 */
object EatsPalette {
    // 浅色（暖米纸感）
    val Paper = Color(0xFFFBF7EF)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF211D19)
    val InkFaint = Color(0xFF8C8478)
    val Accent = Color(0xFFC8502E)
    val Hairline = Color(0xFFEAE2D4)

    // 深色（墨纸）
    val PaperDark = Color(0xFF161412)
    val SurfaceDark = Color(0xFF201D1A)
    val InkDark = Color(0xFFF2EDE4)
    val InkFaintDark = Color(0xFF9C9488)
    val AccentDark = Color(0xFFE07B54)
    val HairlineDark = Color(0xFF2D2925)

    // 类型语义色（marker / 图标 / 转盘扇区共用）
    val Restaurant = Color(0xFFC8502E)
    val Takeout = Color(0xFFD99A2B)
    val HomeCook = Color(0xFF6B8F4E)
    val RestaurantDark = Color(0xFFE07B54)
    val TakeoutDark = Color(0xFFE5B054)
    val HomeCookDark = Color(0xFF8FB073)
}

@Immutable
data class MenuColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkFaint: Color,
    val accent: Color,
    val hairline: Color,
    /** 类型语义色：marker / 图标 / 转盘扇区 */
    val restaurant: Color,
    val takeout: Color,
    val homeCook: Color,
)

val LocalMenuColors = staticCompositionLocalOf {
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
    )
}

/** 便捷取用餐牌手帐风专有色 */
@Composable
fun menuColors(): MenuColors = LocalMenuColors.current

/** 类型语义色便捷取用 */
@Composable
fun kindColor(kind: PlaceKind): Color = with(menuColors()) {
    when (kind) {
        PlaceKind.RESTAURANT -> restaurant
        PlaceKind.TAKEOUT -> takeout
        PlaceKind.HOME -> homeCook
    }
}
