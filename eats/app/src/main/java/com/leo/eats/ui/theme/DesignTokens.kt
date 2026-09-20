package com.leo.eats.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.leo.eats.domain.model.PlaceKind

/**
 * 清新草绿调色板（R6 主题重塑），token 定义见 specs/05-design-system.md。
 * 绿为主调 + 青/湖蓝/草绿三枚类型语义色；评分星保留金色惯例（组件内固定）。
 */
object EatsPalette {
    // 浅色（淡绿纸感）
    val Paper = Color(0xFFF2F7EF)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E2822)
    val InkFaint = Color(0xFF84907F)
    val Accent = Color(0xFF3FA265)
    val Hairline = Color(0xFFE2ECDF)

    // 深色（墨绿纸）
    val PaperDark = Color(0xFF101711)
    val SurfaceDark = Color(0xFF1B241C)
    val InkDark = Color(0xFFE9F2E7)
    val InkFaintDark = Color(0xFF93A493)
    val AccentDark = Color(0xFF7BCD93)
    val HairlineDark = Color(0xFF263229)

    // 类型语义色（marker / 图标 / 卡组共用）：it-004 回归 spec W2 基线 红/琥珀/绿，拉开色相
    val Restaurant = Color(0xFFD25446)
    val Takeout = Color(0xFFDA9A2B)
    val HomeCook = Color(0xFF4C9E5F)
    val RestaurantDark = Color(0xFFE58377)
    val TakeoutDark = Color(0xFFE7B566)
    val HomeCookDark = Color(0xFF86C795)
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
