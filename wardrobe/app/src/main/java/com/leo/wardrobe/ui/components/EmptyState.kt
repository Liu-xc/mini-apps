package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * 空状态：自绘"摇摆衣架"动画（specs/05 动效#8）。
 * 不引 Lottie 资产、零外部依赖，观感等价（it-001 验证记录有说明）。
 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val ink = editorialColors().inkFaint
    val accent = editorialColors().accent
    val transition = rememberInfiniteTransition(label = "hanger")
    val sway by transition.animateFloat(
        initialValue = -9f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(84.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            rotate(degrees = sway, pivot = Offset(cx, h * 0.10f)) {
                // 挂钩
                drawArc(
                    color = ink,
                    startAngle = 180f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.09f, h * 0.03f),
                    size = Size(w * 0.18f, w * 0.18f),
                    style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
                )
                // 三角衣身
                val body = Path().apply {
                    moveTo(cx, h * 0.24f)
                    lineTo(w * 0.10f, h * 0.62f)
                    lineTo(w * 0.90f, h * 0.62f)
                    close()
                }
                drawPath(
                    body,
                    color = ink,
                    style = Stroke(width = w * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // 底杆（强调色）
                drawLine(
                    color = accent,
                    start = Offset(w * 0.10f, h * 0.62f),
                    end = Offset(w * 0.90f, h * 0.62f),
                    strokeWidth = w * 0.045f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = editorialColors().ink)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = editorialColors().inkFaint)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
