package com.leo.wardrobe.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * it-036 C11：横向筛选行右缘渐隐（全站规范，specs/05「筛选行渐隐与分段控件」）。
 *
 * 滚动容器右缘叠一条 [fadeWidth]（24–32dp，落地 28dp）的
 * `Brush.horizontalGradient(透明 → 页面底色)`，暗示右侧还有 chip 可滑；
 * 仅内容超出一屏且未滑到尽头时出现，滑到尽头/一屏放得下即隐去。
 * 只加边缘渐隐，不改 chips 本体形制与热区（it-033 触控基线不回退）。
 *
 * 落点：W3 品类 chips 行、W8 标签筛选条（FilterChipsRow）、W10 品类 chips 行。
 */
@Composable
fun FadingScrollRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    fadeWidth: Dp = 28.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberScrollState()
    val pageBg = editorialColors().paper
    // 读在组合期：滚动/内容变化都会重组，draw 块只负责画
    val fadeActive = state.maxValue > 0 && state.value < state.maxValue
    Box(
        modifier.drawWithContent {
            drawContent()
            if (fadeActive) {
                val w = fadeWidth.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, pageBg)),
                    topLeft = Offset(size.width - w, 0f),
                    size = Size(w, size.height),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(state),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
