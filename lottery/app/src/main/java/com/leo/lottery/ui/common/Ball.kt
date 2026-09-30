package com.leo.lottery.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 号码球（it-002 广播级）：白底号码盘 + 球面径向明暗 + 镜面高光点 + 边缘光 + 接触阴影。
 * 可发光（剧场）、可淡出（未命中）、可挂命中角标。球色由调用方传入（主题无关）。
 */
@Composable
fun Ball(
    number: Int,
    base: Color,
    highlight: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    dimmed: Boolean = false,
    checked: Boolean = false,
    ring: Color? = null,
) {
    val shade = Color(base.red * 0.52f, base.green * 0.52f, base.blue * 0.52f)
    val plateC = Color(0xFFFCFAF3)
    val inkC = Color(0xFF241A14)
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.35f else 1f)
            .drawBehind {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                if (glow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(base.copy(alpha = 0.5f), base.copy(alpha = 0f)),
                            center = c,
                            radius = r * 2.0f,
                        ),
                        radius = r * 2.0f,
                        center = c,
                    )
                }
                // 接触阴影
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(c.x, c.y + r * 0.82f),
                        radius = r * 1.15f,
                    ),
                    radius = r * 1.15f,
                    center = Offset(c.x, c.y + r * 0.82f),
                )
                // 球体
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(highlight, base, shade),
                        center = Offset(r * 0.74f, r * 0.64f),
                        radius = r * 1.6f,
                    ),
                    radius = r,
                    center = c,
                )
                // 边缘光（右下弱反光）
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.20f)),
                        center = Offset(r * 0.35f, r * 0.3f),
                        radius = r * 1.9f,
                    ),
                    radius = r,
                    center = c,
                )
                // 号码盘
                val pr = r * 0.66f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, plateC, Color(0xFFE9E2CF)),
                        center = Offset(c.x - pr * 0.18f, c.y - pr * 0.22f),
                        radius = pr * 1.6f,
                    ),
                    radius = pr,
                    center = c,
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.22f),
                    radius = pr,
                    center = c,
                    style = Stroke(width = r * 0.035f),
                )
                // 镜面高光点
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(c.x - r * 0.42f, c.y - r * 0.5f),
                        radius = r * 0.30f,
                    ),
                    radius = r * 0.30f,
                    center = Offset(c.x - r * 0.42f, c.y - r * 0.5f),
                )
                if (ring != null) {
                    drawCircle(color = ring, radius = r + 3f, center = c, style = Stroke(width = 3f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "%02d".format(number),
            color = inkC,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (checked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(Color(0xFF2F7A4F)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
