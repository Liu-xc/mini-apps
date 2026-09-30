package com.leo.lottery.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2.5D 演播室舞台（it-002）：确定性 Canvas 摇奖机——与 3D 版同一套时间轴与编排语义，
 * 球带 z 深度（近大远小 + z 排序），玻璃罩/底座/出球口全手绘。
 * 性能约法：号码排版与渐变刷全部缓存；每帧只做变换与绘制，不分配大对象。
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

    /** 舞台内出球口坐标（与绘制同一几何公式；sway/breathe 幅度 <1% 忽略）。 */
    fun tubeTopIn(stage: Rect): Offset {
        val w = stage.width
        val h = stage.height
        val domeR = minOf(w * 0.36f, h * 0.40f)
        return Offset(stage.left + w / 2f, stage.top + h * 0.46f - domeR * 1.06f)
    }
}

/** 号码球绘制器：排版与渐变按 (颜色,号码) 缓存，热路径零分配。 */
internal class BallPainter(measurer: TextMeasurer) {
    val layouts = HashMap<Int, TextLayoutResult>()
    val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF241A14))
    val measurer = measurer

    fun layout(num: Int): TextLayoutResult = layouts.getOrPut(num) {
        measurer.measure(num.toString(), style)
    }
}

internal class BallBrushes(base: Color, hi: Color) {
    val sphere: Brush
    val rim: Brush
    val plate: Brush
    val spec: Brush

    init {
        val shade = Color(base.red * 0.5f, base.green * 0.5f, base.blue * 0.5f)
        sphere = Brush.radialGradient(
            listOf(hi, base, shade),
            center = Offset(-0.3f * R, -0.34f * R),
            radius = 1.65f * R,
        )
        rim = Brush.radialGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f)),
            center = Offset(-0.4f * R, -0.3f * R),
            radius = 1.9f * R,
        )
        plate = Brush.radialGradient(
            listOf(Color.White, Color(0xFFFCFAF3), Color(0xFFE7DFC9)),
            center = Offset(-0.1f * R, -0.14f * R),
            radius = 1.16f * R,
        )
        spec = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
            center = Offset(-0.45f * R, -0.52f * R),
            radius = 0.32f * R,
        )
    }

    companion object {
        const val R = 40f
    }
}

/**
 * 摇奖舞台：玻璃球 + 号码球群 + 底座 + 出球口。
 * tState 为剧场时钟（在绘制 lambda 内读取，避免逐帧重组）。
 * eject：ejectNum 非 null 时该球正被吸向出球口（ejectProg ∈ [0,1]）。
 */
@Composable
fun DrawStageCanvas(
    tState: androidx.compose.runtime.State<Long>,
    swapAt: Long,
    poolCount: Int,
    seedTag: String,
    numbers: List<Int>,
    dropStarts: LongArray,
    n1: Int,
    ballBase: Color,
    ballHi: Color,
    modifier: Modifier = Modifier,
) {
    val seeds = remember(seedTag, poolCount) { CanvasStage.seeds(poolCount, seedTag) }
    val measurer = rememberTextMeasurer()
    val painter = remember(measurer) { BallPainter(measurer) }
    val brushes = remember(ballBase, ballHi) { BallBrushes(ballBase, ballHi) }
    val nums = remember(numbers) { numbers.toIntArray() }
    val starts = remember(dropStarts) { dropStarts }

    Canvas(modifier) {
        val t = tState.value
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

            // 出球状态（绘制层内推导，避免逐帧重组）
            var ejectNum = -1
            var ejectProg = 0f
            var goneFrom = -1L
            for (k in nums.indices) {
                val ds = starts[k]
                if (t >= ds && t < ds + Stage3D.T_DROP) {
                    ejectNum = nums[k]
                    ejectProg = (t - ds).toFloat() / Stage3D.T_DROP
                    goneFrom = ds
                    break
                }
            }

            val jets = CanvasStage.jets(t, swapAt)
            val tt = t / 1000f
            val balls = ArrayList<BallDraw>(poolCount)

            for (i in seeds.indices) {
                val num = i + 1
                val s = seeds[i]
                val f = CanvasStage.fly(tt, s, jets)
                val persp = 1f + f[2] * 0.22f
                var bx = cx + f[0] * domeR * 0.80f * persp
                var by = cy + f[1] * domeR * 0.74f * persp - domeR * 0.06f
                if (num == ejectNum) {
                    // 吸顶：加速收拢到出球口；0.55 后交给飞行层
                    val e = (ejectProg * 1.82f).coerceIn(0f, 1f)
                    val tubeX = cx
                    val tubeY = cy - domeR * 1.02f
                    bx = bx + (tubeX - bx) * e * e
                    by = by + (tubeY - by) * e * e
                    if (ejectProg >= 0.55f) continue
                } else {
                    // 已出球的号码不再回机内
                    var landed = false
                    for (k in nums.indices) {
                        if (nums[k] == num && t >= starts[k] + Stage3D.T_DROP * 0.55f) {
                            landed = true
                            break
                        }
                    }
                    if (landed) continue
                }
                balls.add(BallDraw(bx, by, domeR * 0.135f * persp, f[2], num))
            }
            balls.sortBy { it.z }
            balls.forEach { b ->
                drawGlossyBall(b.x, b.y, b.r, b.num, painter, brushes)
            }

            drawDomeFront(cx, cy, domeR, t, ballBase)
        }
    }
}

private class BallDraw(
    val x: Float,
    val y: Float,
    val r: Float,
    val z: Float,
    val num: Int,
)

private fun DrawScope.drawPedestal(cx: Float, cy: Float, domeR: Float) {
    val topW = domeR * 0.62f
    val botW = domeR * 0.78f
    val ph = domeR * 0.42f
    val top = cy + domeR * 0.86f
    val bot = top + ph
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
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = domeR,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.016f),
    )
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
    val rx = domeR * 0.42f
    drawOval(
        color = LotteryPalette.StageGold.copy(alpha = 0.34f),
        topLeft = Offset(cx - rx, cy + domeR * 0.88f - rx * 0.14f),
        size = Size(rx * 2, rx * 0.28f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = domeR * 0.014f),
    )
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

/** 广播级号码球：缓存刷 + 固定基准半径 + scale 变换（热路径零分配）。 */
internal fun DrawScope.drawGlossyBall(
    x: Float,
    y: Float,
    r: Float,
    num: Int,
    painter: BallPainter,
    brushes: BallBrushes,
) {
    val s = r / BallBrushes.R
    withTransform({ translate(x - BallBrushes.R, y - BallBrushes.R); scale(s, s) }) {
        val c = Offset(BallBrushes.R, BallBrushes.R)
        drawCircle(brush = brushes.sphere, radius = BallBrushes.R, center = c)
        drawCircle(brush = brushes.rim, radius = BallBrushes.R, center = c)
        val pr = BallBrushes.R * 0.68f
        drawCircle(brush = brushes.plate, radius = pr, center = c)
        drawCircle(brush = brushes.spec, radius = 0.32f * BallBrushes.R, center = c)
        val res = painter.layout(num)
        drawText(
            res,
            topLeft = Offset(BallBrushes.R - res.size.width / 2f, BallBrushes.R - res.size.height / 2f),
        )
    }
}
