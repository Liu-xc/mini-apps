package com.leo.lottery.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.leo.lottery.core.SeedHash
import com.leo.lottery.core.SplitMix64
import com.leo.lottery.ui.draw3d.Stage3D
import com.leo.lottery.ui.theme.LotteryPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2.5D 演播室舞台（it-002）：确定性 Canvas 摇奖机——与 3D 版同一套时间轴与编排语义，
 * 球带 z 深度（近大远小 + z 排序），玻璃罩/底座/出球口全手绘。
 * Filament 真三维实现留在 ui.draw3d（模拟器原生层不稳，真机验证后启用，ADR-006）。
 */
object CanvasStage {

    class BallSeed(val p0: Float, val p1: Float, val p2: Float, val spd: Float)

    fun seeds(count: Int, tag: String): List<BallSeed> {
        val rng = SplitMix64(SeedHash.seedLong("stage|$tag".toByteArray()))
        return List(count) {
            BallSeed(
                rng.nextInt(6283) / 1000f,
                rng.nextInt(6283) / 1000f,
                rng.nextInt(6283) / 1000f,
                0.7f + rng.nextInt(60) / 100f,
            )
        }
    }

    /** 单位球内乱飞位置（x,y,z，z∈[-1,1] 前正）。 */
    fun fly(tt: Float, s: BallSeed, jets: Float): FloatArray {
        if (jets <= 0.02f) return floatArrayOf(0f, -1f, 0f)
        val a = tt * 1.9f * s.spd + s.p0
        val b = tt * 1.3f * s.spd + s.p1
        val c = tt * 2.4f * s.spd + s.p2
        val breath = 0.55f + 0.42f * (0.5f + 0.5f * sin(tt * 1.1f * s.spd + s.p0 * 2f))
        var x = sin(a) * cos(b)
        var y = sin(b) * 0.82f + 0.06f * sin(c * 2f)
        var z = cos(a) * sin(b)
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4f)
        val rr = breath / len
        x *= rr; y *= rr; z *= rr
        return floatArrayOf(x, y, z)
    }

    fun jets(t: Long, swapAt: Long): Float = when {
        t < Stage3D.T_OPEN -> 0f
        t < Stage3D.T_OPEN + Stage3D.T_WARM -> (t - Stage3D.T_OPEN).toFloat() / Stage3D.T_WARM
        t >= swapAt && t < swapAt + Stage3D.T_SWAP -> 0.45f
        else -> 1f
    }
}

/**
 * 摇奖舞台：玻璃球 + 号码球群 + 底座 + 出球口。t 为剧场时钟（ms）。
 */
@Composable
fun DrawStageCanvas(
    t: Long,
    blue: Boolean,
    game: com.leo.lottery.core.Game,
    poolCount: Int,
    drawNumbers: Set<Int>,
    ejectNumber: Int?,
    seedTag: String,
    ballBase: Color,
    ballHi: Color,
    modifier: Modifier = Modifier,
) {
    val seeds = remember(seedTag, poolCount) { CanvasStage.seeds(poolCount, seedTag) }
    val measurer = rememberTextMeasurer()
    val txtStyle = remember(ballBase) {
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF241A14))
    }
    val swapAtBlue = blue

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val domeR = minOf(w * 0.36f, h * 0.40f)
        val cx = w / 2f
        val cy = h * 0.46f
        val sway = sin(t / 2600f) * domeR * 0.025f
        val breathe = 1f + sin(t / 1700f) * 0.008f

        withTransform({
            translate(sway, 0f)
            scale(breathe, breathe, pivot = Offset(cx, cy))
        }) {
            drawPedestal(cx, cy, domeR)
            drawDomeBack(cx, cy, domeR)

            // ---- 号码球群（z 排序，远小近大）----
            val jets = CanvasStage.jets(t, 0L)
            val tt = t / 1000f
            data class B(val x: Float, val y: Float, val z: Float, val num: Int, val eject: Float)

            val balls = ArrayList<B>(poolCount)
            seeds.forEachIndexed { i, s ->
                val num = i + 1
                var f = CanvasStage.fly(tt, s, jets)
                var eject = -1f
                if (num == ejectNumber) eject = 1f
                if (num in drawNumbers && num != ejectNumber) return@forEachIndexed
                if (ejectNumber != null && num == ejectNumber) {
                    // 吸顶：向 (0, ~0.95, 0) 收拢再升高（由调用方时间控制，这里用 eject=1 简化为顶位）
                    f = floatArrayOf(f[0] * 0.15f, 0.92f, f[2] * 0.15f)
                }
                balls.add(B(f[0], f[1], f[2], num, eject))
            }
            balls.sortedBy { it.z }.forEach { b ->
                val persp = 1f + b.z * 0.22f
                val bx = cx + b.x * domeR * 0.80f * persp
                val by = cy + b.y * domeR * 0.74f * persp - domeR * 0.06f
                val br = domeR * 0.135f * persp
                if (b.eject >= 0f) {
                    drawBall(bx, by - domeR * 0.28f, br, b.num, ballBase, ballHi, measurer, txtStyle)
                } else {
                    drawBall(bx, by, br, b.num, ballBase, ballHi, measurer, txtStyle)
                }
            }

            drawDomeFront(cx, cy, domeR, t, ballBase)
        }
    }
}

private fun DrawScope.drawPedestal(cx: Float, cy: Float, domeR: Float) {
    val topW = domeR * 0.62f
    val botW = domeR * 0.78f
    val ph = domeR * 0.42f
    val top = cy + domeR * 0.86f
    val bot = top + ph
    // 接地阴影
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(cx, bot),
            radius = botW * 1.1f,
        ),
        topLeft = Offset(cx - botW * 1.1f, bot - botW * 0.3f),
        size = Size(botW * 2.2f, botW * 0.6f),
    )
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - topW, top)
        lineTo(cx + topW, top)
        lineTo(cx + botW, bot)
        lineTo(cx - botW, bot)
        close()
    }
    drawPath(
        path,
        brush = Brush.verticalGradient(
            listOf(Color(0xFF1E2A46), Color(0xFF0D1426)),
            startY = top,
            endY = bot,
        ),
    )
    // 金环
    drawLine(
        color = LotteryPalette.StageGold.copy(alpha = 0.9f),
        start = Offset(cx - topW * 1.04f, top + ph * 0.14f),
        end = Offset(cx + topW * 1.04f, top + ph * 0.14f),
        strokeWidth = domeR * 0.035f,
    )
    drawLine(
        color = LotteryPalette.StageGold.copy(alpha = 0.35f),
        start = Offset(cx - topW * 1.02f, top + ph * 0.24f),
        end = Offset(cx + topW * 1.02f, top + ph * 0.24f),
        strokeWidth = domeR * 0.02f,
    )
}

private fun DrawScope.drawDomeBack(cx: Float, cy: Float, domeR: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFF1A2B52).copy(alpha = 0.55f),
                Color(0xFF0D1830).copy(alpha = 0.75f),
                Color(0xFF0A1226).copy(alpha = 0.9f),
            ),
            center = Offset(cx - domeR * 0.25f, cy - domeR * 0.3f),
            radius = domeR * 1.05f,
        ),
        radius = domeR,
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawDomeFront(cx: Float, cy: Float, domeR: Float, t: Long, ballColor: Color) {
    // 气流微光
    val shimmer = 0.5f + 0.5f * sin(t / 210f)
    drawOval(
        brush = Brush.radialGradient(
            listOf(ballColor.copy(alpha = 0.10f + 0.07f * shimmer), Color.Transparent),
            center = Offset(cx, cy + domeR * 0.4f),
            radius = domeR * 0.85f,
        ),
        topLeft = Offset(cx - domeR * 0.85f, cy - domeR * 0.1f),
        size = Size(domeR * 1.7f, domeR * 1.5f),
    )
    // 玻璃轮廓
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = domeR,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.016f),
    )
    // 反光弧
    rotate(-38f, pivot = Offset(cx, cy)) {
        drawArc(
            color = Color.White.copy(alpha = 0.26f),
            startAngle = 116f,
            sweepAngle = 44f,
            useCenter = false,
            topLeft = Offset(cx - domeR * 0.88f, cy - domeR * 0.88f),
            size = Size(domeR * 1.76f, domeR * 1.76f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.07f),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = 174f,
            sweepAngle = 28f,
            useCenter = false,
            topLeft = Offset(cx - domeR * 0.82f, cy - domeR * 0.82f),
            size = Size(domeR * 1.64f, domeR * 1.64f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.045f),
        )
    }
    // 金环（赤道）
    val rx = domeR * 0.42f
    drawOval(
        color = LotteryPalette.StageGold.copy(alpha = 0.34f),
        topLeft = Offset(cx - rx, cy + domeR * 0.88f - rx * 0.14f),
        size = Size(rx * 2, rx * 0.28f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.014f),
    )
    // 出球口（球顶）
    val tubeTop = Offset(cx, cy - domeR * 1.06f)
    drawCircle(
        color = LotteryPalette.StageGold.copy(alpha = 0.9f),
        radius = domeR * 0.16f,
        center = tubeTop,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.03f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.10f),
        radius = domeR * 0.13f,
        center = tubeTop,
    )
}

/** 广播级手绘号码球：径向球体 + 白盘 + 高光 + 边缘光。 */
private fun DrawScope.drawBall(
    x: Float,
    y: Float,
    r: Float,
    num: Int,
    base: Color,
    hi: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
) {
    val shade = Color(base.red * 0.5f, base.green * 0.5f, base.blue * 0.5f)
    // 球体
    drawCircle(
        brush = Brush.radialGradient(
            listOf(hi, base, shade),
            center = Offset(x - r * 0.3f, y - r * 0.34f),
            radius = r * 1.65f,
        ),
        radius = r,
        center = Offset(x, y),
    )
    // 边缘光
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f)),
            center = Offset(x - r * 0.4f, y - r * 0.3f),
            radius = r * 1.9f,
        ),
        radius = r,
        center = Offset(x, y),
    )
    // 白盘
    val pr = r * 0.68f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFFCFAF3), Color(0xFFE7DFC9)),
            center = Offset(x - pr * 0.15f, y - pr * 0.2f),
            radius = pr * 1.7f,
        ),
        radius = pr,
        center = Offset(x, y),
    )
    // 高光点
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
            center = Offset(x - r * 0.45f, y - r * 0.52f),
            radius = r * 0.32f,
        ),
        radius = r * 0.32f,
        center = Offset(x - r * 0.45f, y - r * 0.52f),
    )
    // 号码
    val res = measurer.measure(num.toString(), style)
    drawText(
        res,
        topLeft = Offset(x - res.size.width / 2f, y - res.size.height / 2f),
    )
}
