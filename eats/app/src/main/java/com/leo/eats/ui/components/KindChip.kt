package com.leo.eats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.ui.theme.kindColor

val PlaceKind.label: String
    get() = when (this) {
        PlaceKind.RESTAURANT -> "堂食"
        PlaceKind.TAKEOUT -> "外卖"
        PlaceKind.HOME -> "自做"
    }

val PlaceKind.icon: ImageVector
    get() = when (this) {
        PlaceKind.RESTAURANT -> Icons.Rounded.Restaurant
        PlaceKind.TAKEOUT -> Icons.Rounded.DeliveryDining
        PlaceKind.HOME -> Icons.Rounded.SoupKitchen
    }

/** 类型小圆点（W3 列表行、详情用） */
@Composable
fun KindDot(kind: PlaceKind, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .background(kindColor(kind), CircleShape),
    )
}

/** 类型 chip：色点 + 图标 + 文案 */
@Composable
fun KindChip(kind: PlaceKind, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = kindColor(kind).copy(alpha = 0.14f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                kind.icon,
                contentDescription = kind.label,
                tint = kindColor(kind),
                modifier = Modifier.size(if (compact) 13.dp else 15.dp),
            )
            if (!compact) {
                Text(
                    kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = kindColor(kind),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** 无图占位：按类型着色的 emoji 图标块 */
@Composable
fun KindPlaceholder(kind: PlaceKind, modifier: Modifier = Modifier, contentColor: Color = Color.Unspecified) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = kindColor(kind).copy(alpha = 0.14f),
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                kind.icon,
                contentDescription = null,
                tint = if (contentColor == Color.Unspecified) kindColor(kind) else contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
