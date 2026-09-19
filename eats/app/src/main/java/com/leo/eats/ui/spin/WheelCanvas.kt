package com.leo.eats.ui.spin

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.leo.eats.ui.theme.menuColors

/** 一个扇区：颜色 + 悬浮中心显示的名称 */
data class WheelSector(val color: Color, val label: String)

/**
 * 指针所指扇区下标：Canvas 角度系 0 = 3 点钟、顺时针为正；
 * 指针在正上方（-90°），转盘额外旋转 r 度后：a = (-90 - r) mod 360。
 */
fun sectorIndexAt(rotationDegrees: Float, sectorCount: Int): Int {
    if (sectorCount <= 0) return -1
    val sweep = 360f / sectorCount
    val a = (((-90f - rotationDegrees) % 360f) + 360f) % 360f
    return (a / sweep).toInt().coerceIn(0, sectorCount - 1)
}

/**
 * 转盘（specs/05 动效 1 + it-002 R1 拍动指针）：扇形按类型着色（相邻同色交替淡化）、
 * 白色细缝分隔。指针为"拍片"：旋转中随速度拍动（-16°↔4° 往复），停止时回正。
 * 中心内容由 [center] 叠加层提供（实时掠过的名称）。
 */
@Composable
fun WheelCanvas(
    rotationDegrees: Float,
    sectors: List<WheelSector>,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    center: @Composable () -> Unit = {},
) {
    val colors = menuColors()

    // 拍片角度：旋转中往复拍动，停止时弹簧回正
    val flapper = androidx.compose.animation.core.rememberInfiniteTransition(label = "flapper")
    val flapAngle by flapper.animateFloat(
        initialValue = -16f,
        targetValue = 4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(120),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "flap",
    )
    val settle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (spinning) flapAngle else 0f,
        animationSpec = if (spinning) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = 700f,
            )
        },
        label = "settle",
    )

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val n = sectors.size

            if (n == 0) {
                drawCircle(
                    color = colors.surface,
                    radius = diameter / 2f,
                    center = centerOffset,
                )
                drawCircle(
                    color = colors.hairline,
                    radius = diameter / 2f,
                    center = centerOffset,
                    style = Stroke(width = 2f),
                )
                return@Canvas
            }

            val sweep = 360f / n
            rotate(rotationDegrees) {
                sectors.forEachIndexed { i, s ->
                    drawArc(
                        color = s.color,
                        startAngle = i * sweep,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    // 细缝分隔
                    val angle = Math.toRadians((i * sweep - 90f).toDouble())
                    val edge = Offset(
                        centerOffset.x + (diameter / 2f * Math.cos(angle)).toFloat(),
                        centerOffset.y + (diameter / 2f * Math.sin(angle)).toFloat(),
                    )
                    drawLine(
                        color = colors.paper,
                        start = centerOffset,
                        end = edge,
                        strokeWidth = 3f,
                    )
                }
            }

            // 外圈 + 拍动指针（不随转盘旋转，枢轴在指针根部）
            drawCircle(
                color = colors.hairline,
                radius = diameter / 2f,
                center = centerOffset,
                style = Stroke(width = 4f),
            )
            val pointerPivot = Offset(centerOffset.x, topLeft.y + 6f)
            val pointer = Path().apply {
                val tipY = topLeft.y - 2f
                moveTo(pointerPivot.x, pointerPivot.y)
                lineTo(pointerPivot.x - 13f, tipY)
                lineTo(pointerPivot.x + 13f, tipY)
                close()
            }
            withTransform({ rotate(settle, pointerPivot) }) {
                drawPath(pointer, color = colors.accent)
            }
        }
        center()
    }
}
