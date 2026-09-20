package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiParticle(
    val x: Float,          // 0..1 起始横坐标（发射点附近）
    val angle: Float,      // 弧度
    val speed: Float,      // 初速（归一化）
    val size: Float,       // 粒子尺寸 px 基准
    val color: Color,
    val rotateSpeed: Float,
)

/**
 * 复制成功的彩屑迸开（specs/05 动效#4）：
 * trigger 自增触发一轮 900ms 动画（砖红/墨黑/米白三色，重力下落）。
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val palette = listOf(Color(0xFF429E68), Color(0xFF4E9BD8), Color(0xFF8CBE4F), Color(0xFF74C790))
    val particles = remember(trigger) {
        List(26) {
            ConfettiParticle(
                x = 0.5f + Random.nextFloat() * 0.14f - 0.07f,
                angle = (-95f + Random.nextFloat() * -50f + 25f) * (Math.PI.toFloat() / 180f),
                speed = 0.55f + Random.nextFloat() * 0.6f,
                size = 5f + Random.nextFloat() * 7f,
                color = palette[Random.nextInt(palette.size)],
                rotateSpeed = 420f + Random.nextFloat() * 540f,
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, animationSpec = tween(900, easing = LinearOutSlowInEasing))
    }
    val t = progress.value
    if (t >= 1f) return

    Canvas(modifier) {
        val g = 1.8f // 归一化重力
        particles.forEach { p ->
            val dx = cos(p.angle) * p.speed * t
            val dy = sin(p.angle) * p.speed * t + 0.5f * g * t * t
            val px = p.x * size.width + dx * size.width * 0.55f
            val py = size.height * 0.78f + dy * size.height * 0.45f
            val alpha = (1f - t).coerceIn(0f, 1f)
            val rotation = p.rotateSpeed * t
            withTransform({
                translate(px, py)
                rotate(rotation)
            }) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(-p.size / 2f, -p.size / 2.6f),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size / 1.3f),
                )
            }
        }
    }
}
