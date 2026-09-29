package com.leo.wardrobe.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 *
 * it-063 修1勘误：fadeColor 维持 paper——页面真实底色由 windowBackground(@color/paper)
 * 决定（实测 #F7F7F5），paper 才是正确对齐值；缺陷真因是带宽 28dp < 品类图标 44dp，
 * 图标前段全亮直到硬切（「一部分还透出」）。[opaqueStop]：渐隐在带宽该比例处提前
 * 到达全遮盖（默认 1f 保持原线性；元素比带宽宽的落点（W3 44dp 图标）用 <1 提前封满，
 * 带尾留纯底色）。
 *
 * it-065 修3：起点策略 [fadeAtStart]——无行尾固定钮的落点（W8 标签行 / W10 品类行）
 * 初始即给「右侧还有内容」轻提示（true）；W3 品类行因行尾「筛选」固定钮维持 it-064
 * 「起点干净」（false，滑离起点才渐隐），避免渐隐带在按钮旁制造底色遮挡。滑到尽头一律隐去。
 */
@Composable
fun FadingScrollRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    fadeWidth: Dp = 28.dp,
    fadeColor: Color = editorialColors().paper,
    opaqueStop: Float = 1f,
    fadeAtStart: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberScrollState()
    // it-071：渐隐判定移入 draw 期（同 fadingBottomEdge 先例）——滚动/内容变化只触发重绘，
    // 不再在组合期读 state（原注释「读在组合期」废止）。
    // it-064 修4：起点（未滚动）默认不渲染（fadeAtStart=false）；
    // it-065 修3：fadeAtStart=true 的落点起点即渲染，滑到尽头即隐（两端策略见参数注释）
    val brush = remember(fadeColor, opaqueStop) {
        Brush.horizontalGradient(
            colorStops = arrayOf(0f to Color.Transparent, opaqueStop.coerceIn(0.01f, 1f) to fadeColor),
        )
    }
    Box(
        modifier.drawWithContent {
            drawContent()
            val fadeActive = state.maxValue > 0 &&
                state.value < state.maxValue &&
                (fadeAtStart || state.value > 0)
            if (fadeActive) {
                val w = fadeWidth.toPx()
                drawRect(
                    brush = brush,
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

/**
 * it-042 C8：滚动容器底缘渐隐（[FadingScrollRow] 的纵向版，同一视觉语言）。
 *
 * 内容可继续向下滚动时，在容器底缘叠一条 [fadeHeight]（落地 28dp）的
 * `Brush.verticalGradient(透明 → 页面底色)`，缓解行卡在视口底缘被拦腰裁切的观感；
 * 滚到底/一屏放得下即隐去（[active] 由调用方给出滚动判定）。
 *
 * 判定 lambda 在 draw 期求值：滚动状态读取只触发重绘，不引发组合帧重组。
 * 落点：W1 槽位滚动列、W3 衣橱卡网格。
 *
 * it-063 修1勘误：fadeColor 维持 paper（页面底色由 windowBackground 决定，实测
 * 与 paper 一致），渐隐带在无内容处与底色零差、不显带。
 */
@Composable
fun Modifier.fadingBottomEdge(
    active: () -> Boolean,
    fadeHeight: Dp = 28.dp,
    fadeColor: Color = editorialColors().paper,
): Modifier {
    // it-071：Brush 记忆复用，不再每次重绘分配（remember 须在组合期，draw 期只读引用）
    val brush = remember(fadeColor) { Brush.verticalGradient(listOf(Color.Transparent, fadeColor)) }
    return this.drawWithContent {
        drawContent()
        if (active()) {
            val h = fadeHeight.toPx()
            drawRect(
                brush = brush,
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
            )
        }
    }
}
