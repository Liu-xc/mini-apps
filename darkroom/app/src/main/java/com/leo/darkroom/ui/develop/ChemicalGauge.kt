package com.leo.darkroom.ui.develop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.ui.theme.editorialColors

/**
 * it-007 M1 药水刻度条：化学量筒式自绘进度控件，替换 Material Slider。
 *
 * 结构：阶段名一排（按各自阶段宽度居中，当前阶段 ink）+ 刻度轨道。
 * 轨道未显影段是细 hairline，已显影段是实心 accent；5% 短刻度、25% 长刻度、
 * 阶段分界最长——进度直接显形在刻度上。手柄 8dp 圆点（拖动中放大到 12dp）。
 *
 * 交互语义与原 Slider 一致：按下暂停 → 拖动/点击 seek → 松手续播，支持倒放。
 * 触控区高度 44dp（视觉轨道居中偏上，刻度向下延伸）。
 */
@Composable
fun ChemicalGauge(
    progress: Float,
    stageLabels: List<String>,
    onDragStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = editorialColors()
    val activeIndex = DevelopSpec.phaseAt(progress).ordinal
    val phaseWidths = listOf(
        DevelopSpec.LATENT_END,
        DevelopSpec.EMERGING_END - DevelopSpec.LATENT_END,
        1f - DevelopSpec.EMERGING_END,
    )
    var dragging by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            stageLabels.take(3).forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == activeIndex) colors.ink else colors.inkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(phaseWidths[index])
                        .padding(bottom = 2.dp),
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = "药水刻度条" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        onDragStart()
                        onSeek(fractionOf(down.position.x, size.width))
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed } ?: break
                            onSeek(fractionOf(pressed.position.x, size.width))
                            pressed.consume()
                        }
                        dragging = false
                        onDragEnd()
                    }
                },
        ) {
            val p = progress.coerceIn(0f, 1f)
            val trackY = size.height / 2f - 7.dp.toPx()
            val activeX = size.width * p

            // 轨道：未显影细线 → 已显影实心
            drawLine(
                color = colors.hairline,
                start = Offset(activeX, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = colors.accent,
                start = Offset(0f, trackY),
                end = Offset(activeX, trackY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Butt,
            )

            // 刻度：5% 短、25% 长、阶段分界最长；已显影段实心，未显影段 hairline
            val tickTop = trackY + 4.dp.toPx()
            for (i in 0..20) {
                val t = i / 20f
                val length = when {
                    t == DevelopSpec.LATENT_END || t == DevelopSpec.EMERGING_END -> 13.dp.toPx()
                    i % 5 == 0 -> 9.dp.toPx()
                    else -> 5.dp.toPx()
                }
                val developed = t <= p + 0.0005f
                drawLine(
                    color = if (developed) colors.accent else colors.hairline,
                    start = Offset(size.width * t, tickTop),
                    end = Offset(size.width * t, tickTop + length),
                    strokeWidth = if (developed) 1.4.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }

            // 手柄：8dp 圆点 + 2dp 纸色描边，拖动中放大到 12dp
            val radius = if (dragging) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(
                color = colors.paper,
                radius = radius + 1.dp.toPx(),
                center = Offset(activeX, trackY),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(color = colors.accent, radius = radius, center = Offset(activeX, trackY))
        }
    }
}

private fun fractionOf(x: Float, width: Int): Float =
    if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)
