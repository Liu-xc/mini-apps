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
 * 号码球：径向渐变（左上高光）+ 白字印刷；可发光（剧场）、可淡出（未命中）、
 * 可挂命中角标。球色由调用方传入（玩法内容色，主题无关）。
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
    val shade = Color(base.red * 0.58f, base.green * 0.58f, base.blue * 0.58f)
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.38f else 1f)
            .drawBehind {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                if (glow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(base.copy(alpha = 0.55f), base.copy(alpha = 0f)),
                            center = c,
                            radius = r * 2.1f,
                        ),
                        radius = r * 2.1f,
                        center = c,
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(highlight, base, shade),
                        center = Offset(r * 0.78f, r * 0.68f),
                        radius = r * 1.55f,
                    ),
                    radius = r,
                    center = c,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = r - 0.75f,
                    center = c,
                    style = Stroke(width = 1.5f),
                )
                if (ring != null) {
                    drawCircle(color = ring, radius = r + 3f, center = c, style = Stroke(width = 3f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontSize = (size.value * 0.40f).sp,
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
