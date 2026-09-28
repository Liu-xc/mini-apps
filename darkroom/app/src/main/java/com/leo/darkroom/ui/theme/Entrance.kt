package com.leo.darkroom.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * it-008 入场编排（DESIGN.md §3：stagger 20–24ms/项，>200ms 编排只允许首屏）：
 * 单组 fade 240ms + rise 14dp。`enabled=false` 时首帧即落位（二次进入快路径），
 * 系统「移除动画」下瞬时显示。跨屏存续的开关由导航壳持有，本组件无状态。
 */
@Composable
fun EditorialEntrance(
    delayMs: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = EditorialMotion.reduceMotion()
    val progress = remember { Animatable(if (enabled && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!enabled || reduce) return@LaunchedEffect
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, tween(240))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * 定影光泽扫（it-008 M1.3）：一次性的对角高光带掠过卡片——峰值白 12%，
 * 在深色卡面与照片上可辨，浅纸边框上是物理正确的「亚光不反光」。
 * sweep∈(0,1) 才绘制；600ms 内完成（基线庆祝预算 ≤900ms）。
 */
@Composable
fun GlossSweepOverlay(sweep: Float, modifier: Modifier = Modifier) {
    if (sweep <= 0f || sweep >= 1f) return
    Canvas(modifier) {
        val bandW = size.width * 0.34f
        val x = size.width * (-0.4f + sweep * 1.8f)
        val brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
            startX = x - bandW,
            endX = x + bandW,
        )
        // 带长轴沿 -45°：矩形需放大到旋转后仍覆盖整卡
        withTransform({ rotate(-45f, pivot = center) }) {
            val s = size.width + size.height
            drawRect(brush, topLeft = Offset(-s, -s), size = Size(s * 2f, s * 2f))
        }
    }
}
