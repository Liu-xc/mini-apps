package com.leo.eats.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind

/**
 * 清新草绿调色板（R6 主题重塑），token 定义见 specs/05-design-system.md。
 * 绿为主调 + 青/湖蓝/草绿三枚类型语义色；评分星保留金色惯例（组件内固定）。
 * it-008：新增「玩」紫（分类语义色），marker/图例改按分类着色。
 */
object EatsPalette {
    // 浅色（淡绿纸感）
    val Paper = Color(0xFFF2F7EF)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E2822)
    val InkFaint = Color(0xFF828E7D)  // it-015：原 #84907F 对 paper 3.08:1 贴线，微调至 3.23:1（DESIGN.md §2.2）
    val Accent = Color(0xFF3FA265)
    val Hairline = Color(0xFFE2ECDF)

    // 深色（墨绿纸）
    val PaperDark = Color(0xFF101711)
    val SurfaceDark = Color(0xFF1B241C)
    val InkDark = Color(0xFFE9F2E7)
    val InkFaintDark = Color(0xFF93A493)
    val AccentDark = Color(0xFF7BCD93)
    val HairlineDark = Color(0xFF263229)

    // 类型语义色（it-008 起保留给旧组件过渡；marker/图例已改用分类色）
    val Restaurant = Color(0xFFD25446)
    val Takeout = Color(0xFFDA9A2B)
    val HomeCook = Color(0xFF4C9E5F)
    val RestaurantDark = Color(0xFFE58377)
    val TakeoutDark = Color(0xFFE7B566)
    val HomeCookDark = Color(0xFF86C795)

    // 分类语义色（it-008 ADR-012）：吃=红 / 喝=琥珀 / 玩=紫
    val Eat = Restaurant
    val Drink = Takeout
    val Play = Color(0xFF7C6BC4)
    val EatDark = RestaurantDark
    val DrinkDark = TakeoutDark
    val PlayDark = Color(0xFFAB9EE3)
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
    /** 分类语义色（it-008）：吃/喝/玩 */
    val eat: Color,
    val drink: Color,
    val play: Color,
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
        eat = EatsPalette.Eat,
        drink = EatsPalette.Drink,
        play = EatsPalette.Play,
    )
}

/** 便捷取用餐牌手帐风专有色 */
@Composable
fun menuColors(): MenuColors = LocalMenuColors.current

/** 分类语义色便捷取用（it-008：marker / 图例 / chips 按分类着色） */
@Composable
fun categoryColor(category: PlaceCategory): Color = with(menuColors()) {
    when (category) {
        PlaceCategory.EAT -> eat
        PlaceCategory.DRINK -> drink
        PlaceCategory.PLAY -> play
    }
}

/** 类型语义色便捷取用 */
@Composable
fun kindColor(kind: PlaceKind): Color = with(menuColors()) {
    when (kind) {
        PlaceKind.RESTAURANT -> restaurant
        PlaceKind.TAKEOUT -> takeout
        PlaceKind.HOME -> homeCook
    }
}
