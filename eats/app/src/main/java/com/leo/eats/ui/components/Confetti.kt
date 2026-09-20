package com.leo.eats.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiParticle(
    val x: Float,
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val rotateSpeed: Float,
)

/**
 * 落账成功的彩屑迸开（it-002 R1 / R6 清新色盘）：
 * trigger 自增触发一轮 900ms 动画（嫩绿/湖蓝/草绿/淡金四色，重力下落）。
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val palette = listOf(Color(0xFF3FA265), Color(0xFF4E9BD8), Color(0xFF8CBE4F), Color(0xFFE8C25D), Color(0xFFF6FBF4))
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
        val g = 1.8f
        particles.forEach { p ->
            val dx = cos(p.angle) * p.speed * t
            val dy = sin(p.angle) * p.speed * t + 0.5f * g * t * t
            val px = p.x * size.width + dx * size.width * 0.55f
            val py = size.height * 0.78f + dy * size.height * 0.45f
            val alpha = (1f - t).coerceIn(0f, 1f)
            withTransform({ translate(px, py); rotate(p.rotateSpeed * t) }) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(-p.size / 2f, -p.size / 2.6f),
                    size = Size(p.size, p.size / 1.3f),
                )
            }
        }
    }
}
