package com.leo.eats.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leo.eats.R
import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.labelIn
import com.leo.eats.ui.theme.categoryColor
import com.leo.eats.ui.theme.kindColor

val PlaceKind.label: String
    get() = when (this) {
        PlaceKind.RESTAURANT -> "堂食"
        PlaceKind.TAKEOUT -> "外卖"
        PlaceKind.HOME -> "自做"
    }

/** 类型 3D 图标（thiings.co 素材，README 署名） */
val PlaceKind.iconRes: Int
    get() = when (this) {
        PlaceKind.RESTAURANT -> R.drawable.kind_restaurant
        PlaceKind.TAKEOUT -> R.drawable.kind_takeout
        PlaceKind.HOME -> R.drawable.kind_home
    }

/** 分类小圆点（it-008：分类语义色 吃红/喝琥珀/玩紫） */
@Composable
fun CategoryDot(category: PlaceCategory, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .background(categoryColor(category), CircleShape),
    )
}

/** 类型小圆点（列表行辅助标识，it-008 后仅旧样式兜底用） */
@Composable
fun KindDot(kind: PlaceKind, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .background(kindColor(kind), CircleShape),
    )
}

/**
 * 类型 chip：3D 图标 + 「分类·类型」文案（it-008：文案按分类适配，底色用分类语义色）。
 * compact 模式只显示图标，底色仍是分类色。
 */
@Composable
fun KindChip(
    kind: PlaceKind,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    category: PlaceCategory = PlaceCategory.EAT,
) {
    val tint = categoryColor(category)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Image(
                painter = painterResource(kind.iconRes),
                contentDescription = kind.labelIn(category),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 14.dp else 17.dp),
            )
            if (!compact) {
                Text(
                    "${category.shortLabel}·${kind.labelIn(category)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** 无图占位：3D 类型插画块（it-002 R2：插画随容器放大，hero 友好） */
@Composable
fun KindPlaceholder(kind: PlaceKind, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 30.dp) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = kindColor(kind).copy(alpha = 0.10f),
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(kind.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(12.dp)
                    .size(iconSize),
            )
        }
    }
}
