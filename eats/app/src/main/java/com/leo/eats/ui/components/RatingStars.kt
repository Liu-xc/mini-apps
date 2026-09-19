package com.leo.eats.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.eats.ui.theme.menuColors

/**
 * 评分星（US-01/W6）：展示模式只读；传入 onChange 即输入模式——
 * 点第 n 颗设 n 分，再点同一颗清除（US-08 验收里的「可空」）。
 */
@Composable
fun RatingStars(
    rating: Int?,
    onChange: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    starColor: androidx.compose.ui.graphics.Color = menuColors().accent,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val filled = rating != null && i < rating
            val icon = @Composable {
                Icon(
                    if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = if (filled) "${i + 1} 分" else "未评分",
                    tint = if (filled) starColor else menuColors().inkFaint.copy(alpha = 0.5f),
                    modifier = Modifier.size(size),
                )
            }
            if (onChange != null) {
                IconButton(
                    onClick = { onChange(if (rating == i + 1) null else i + 1) },
                    modifier = Modifier.size(size + 10.dp),
                ) { icon() }
            } else {
                icon()
            }
        }
    }
}

/** 「★4」式的紧凑文本（评分 + 数字） */
@Composable
fun RatingSummary(rating: Int?, modifier: Modifier = Modifier, prefix: String = "") {
    val text = when (rating) {
        null -> "未评分"
        else -> "★ $rating"
    }
    Text(
        text = prefix + text,
        style = MaterialTheme.typography.labelMedium,
        color = if (rating == null) menuColors().inkFaint else menuColors().accent,
        modifier = modifier,
    )
}
