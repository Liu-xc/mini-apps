package com.leo.lottery.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.core.DrawResult
import com.leo.lottery.core.SeedHash
import com.leo.lottery.core.SplitMix64
import com.leo.lottery.core.Ticket
import com.leo.lottery.core.TicketVerdict
import com.leo.lottery.core.Verify
import com.leo.lottery.platform.Haptics
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.common.MiniChip
import com.leo.lottery.ui.common.PrimaryButton
import com.leo.lottery.ui.theme.LocalLotteryColors
import com.leo.lottery.ui.theme.LotteryPalette
import com.leo.lottery.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos

// ---- 时间轴常量（ms，总长约 10.7s ≤ 12s，见 05 §4 首播例外）----
private const val WARMUP = 1600L
private const val MAIN_INTERVAL = 850L
private const val FLIGHT = 520L
private const val SWAP_GAP = 300L
private const val SWAP_DUR = 1100L
private const val SPECIAL_GAP = 400L
private const val SPECIAL_INTERVAL = 800L
private const val VERDICT_DELAY = 500L
private const val END_HOLD = 1500L

// ---- 剧场墨幕固定色（不随主题）----
private val ThInk = Color(0xFFF2F1EC)
private val ThFaint = Color(0xFFA9A79E)
private val ThSurface = Color.White.copy(alpha = 0.06f)
private val ThHairline = Color.White.copy(alpha = 0.14f)

private class FlightWindow(val start: Long) {
    val land: Long get() = start + FLIGHT
}

private class Timeline(
    val flights: List<FlightWindow>,
    val swapStart: Long,
    val verdictAt: Long,
    val endAt: Long,
) {
    companion object {
        fun build(n1: Int, n2: Int): Timeline {
            val f1 = (0 until n1).map { FlightWindow(WARMUP + it * MAIN_INTERVAL) }
            val last1 = f1.last().land
            val swapStart = last1 + SWAP_GAP
            val swapEnd = swapStart + SWAP_DUR
            val f2 = (0 until n2).map { FlightWindow(swapEnd + SPECIAL_GAP + it * SPECIAL_INTERVAL) }
            val lastLand = (f1 + f2).maxOf { it.land }
            val verdictAt = lastLand + VERDICT_DELAY
            return Timeline(f1 + f2, swapStart, verdictAt, verdictAt + END_HOLD)
        }
    }
}

/**
 * 摇奖鼓物理（单位圆盘，y 向下）：重力 + 三叶拨片 + 墙面摩擦搅动。
 * 同种子同轨迹；球未出鼓前不显示号码。
 */
private class DrumWorld(seed: Long, count: Int) {

    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float)

    private val balls = ArrayList<P>(count)
    private var theta = 0f

    init {
        val rng = SplitMix64(seed)
        repeat(count) {
            val ang = rng.nextInt(6283) / 1000f
            val rad = 0.12f + rng.nextInt(450) / 1000f
            val vx = (rng.nextInt(2000) - 1000) / 1000f * 1.2f
            val vy = (rng.nextInt(2000) - 1000) / 1000f * 1.2f
            balls.add(P(cos(ang) * rad, sin(ang) * rad, vx, vy))
        }
    }

    fun posX(i: Int): Float = balls[i].x
    fun posY(i: Int): Float = balls[i].y

    fun step(dt: Float) {
        var rem = dt
        while (rem > 0f) {
            val h = min(rem, 1f / 120f)
            integrate(h)
            rem -= h
        }
    }

    private fun integrate(h: Float) {
        theta += OMEGA * h
        for (b in balls) {
            b.vy += GRAVITY * h
            b.x += b.vx * h
            b.y += b.vy * h

            // 圆墙碰撞 + 滚动摩擦（把墙面速度传给球 → 搅动）
            val d = sqrt(b.x * b.x + b.y * b.y)
            val lim = 1f - BALL_R
            if (d > lim && d > 1e-5f) {
                val nx = b.x / d
                val ny = b.y / d
                b.x = nx * lim
                b.y = ny * lim
                val vn = b.vx * nx + b.vy * ny
                if (vn > 0f) {
                    b.vx -= (1f + WALL_E) * vn * nx
                    b.vy -= (1f + WALL_E) * vn * ny
                }
                val tx = -ny
                val ty = nx
                val vt = b.vx * tx + b.vy * ty
                val target = OMEGA * lim
                val f = (2.0f * h).coerceAtMost(0.2f)
                b.vx += (target - vt) * tx * f
                b.vy += (target - vt) * ty * f
            }

            // 三叶拨片（旋转点）：把球抛起
            for (k in 0 until 2) {
                val pa = theta + k * (PI.toFloat())
                val px = cos(pa) * PADDLE_R
                val py = sin(pa) * PADDLE_R
                val dx = b.x - px
                val dy = b.y - py
                val dd = sqrt(dx * dx + dy * dy)
                val reach = BALL_R + PADDLE_W
                if (dd < reach && dd > 1e-5f) {
                    val nx = dx / dd
                    val ny = dy / dd
                    val sx = -ny * OMEGA * PADDLE_R
                    val sy = nx * OMEGA * PADDLE_R
                    val vn = (b.vx - sx) * nx + (b.vy - sy) * ny
                    if (vn < 0f) {
                        b.vx += -(1f + PAD_E) * vn * nx
                        b.vy += -(1f + PAD_E) * vn * ny
                    }
                    val overlap = reach - dd
                    b.x += nx * overlap
                    b.y += ny * overlap
                }
            }

            val damp = 1f / (1f + 0.5f * h)
            b.vx *= damp
            b.vy *= damp
        }

        // 球球碰撞
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val a = balls[i]
                val c = balls[j]
                val dx = c.x - a.x
                val dy = c.y - a.y
                val dd = sqrt(dx * dx + dy * dy)
                val minD = BALL_R * 2f
                if (dd < minD && dd > 1e-5f) {
                    val nx = dx / dd
                    val ny = dy / dd
                    val overlap = (minD - dd) / 2f
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    c.x += nx * overlap
                    c.y += ny * overlap
                    val vn = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
                    if (vn < 0f) {
                        val imp = -(1f + BALL_E) * vn / 2f
                        a.vx -= imp * nx
                        a.vy -= imp * ny
                        c.vx += imp * nx
                        c.vy += imp * ny
                    }
                }
            }
        }
    }

    companion object {
        const val BALL_R = 0.11f
        const val GRAVITY = 6.0f
        const val OMEGA = 2.6f
        const val WALL_E = 0.5f
        const val BALL_E = 0.6f
        const val PADDLE_R = 0.62f
        const val PADDLE_W = 0.05f
        const val PAD_E = 0.4f
    }
}

private class Geom(
    val stageW: Float,
    val stageH: Float,
    val drumCenter: Offset,
    val drumR: Float,
    val rowY1: Float,
    val rowY2: Float,
    val slotS: Float,
    val slotG: Float,
) {
    fun slotCenter(i: Int, n1: Int, n2: Int): Offset {
        val n = if (i < n1) n1 else n2
        val idx = if (i < n1) i else i - n1
        val totalW = n * slotS + (n - 1) * slotG
        val x = (stageW - totalW) / 2f + idx * (slotS + slotG) + slotS / 2f
        val y = if (i < n1) rowY1 else rowY2
        return Offset(x, y)
    }
}

/**
 * W2-剧场（US-2）：摇奖鼓物理 → 主区逐球飞出落槽 → 换鼓 → 特号区 → 验票面板。
 * 单一时钟驱动全时间轴；跳过直达验票；reduce-motion 直接终态（红线 §3）。
 */
@Composable
fun ReplayOverlay(vm: LotteryViewModel, state: LotteryViewModel.UiState) {
    val d = state.draw
    val result = vm.resultOf(d.game, d.issue)
    if (result == null) {
        LaunchedEffect(Unit) { vm.finishReplay() }
        return
    }

    val context = LocalContext.current
    val reduceMotion = Motion.reduceMotion()
    val tickets = vm.ticketsFor(result.game, result.issue)
    val verdicts = remember(result, tickets) { tickets.map { it to Verify.verify(it, result) } }
    val anyWon = remember(verdicts) { verdicts.any { it.second.won } }

    val n1 = result.zone1.size
    val n2 = result.zone2.size
    val total = n1 + n2
    val tl = remember(result) { Timeline.build(n1, n2) }
    val w1 = remember(result) {
        DrumWorld(SeedHash.seedLong("drum|${result.game}|${result.issue}|a".toByteArray()), n1)
    }
    val w2 = remember(result) {
        DrumWorld(SeedHash.seedLong("drum|${result.game}|${result.issue}|b".toByteArray()), n2)
    }

    val clock = remember { mutableLongStateOf(0L) }
    val skipReq = remember { mutableStateOf(false) }
    val fired = remember(tl) { BooleanArray(total + 1) }

    LaunchedEffect(tl, reduceMotion) {
        if (reduceMotion) {
            clock.longValue = tl.endAt + 1
            fired.fill(true)
            if (anyWon) Haptics.confirm(context)
            return@LaunchedEffect
        }
        var startNs = -1L
        var lastNs = -1L
        var shifted = false
        while (isActive) {
            withFrameNanos { now ->
                if (startNs < 0) {
                    startNs = now
                    lastNs = now
                }
                if (skipReq.value && !shifted) {
                    shifted = true
                    val curT = (now - startNs) / 1_000_000
                    if (curT < tl.verdictAt) startNs = now - tl.verdictAt * 1_000_000
                    for (i in 0 until total) fired[i] = true
                }
                val dt = ((now - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                lastNs = now
                if (dt > 0f) {
                    w1.step(dt)
                    w2.step(dt)
                }
                val t = ((now - startNs) / 1_000_000).coerceAtLeast(0L)
                clock.longValue = minOf(t, tl.endAt)
                for (i in 0 until total) {
                    if (!fired[i] && t >= tl.flights[i].land) {
                        fired[i] = true
                        Haptics.tick(context)
                    }
                }
                if (!fired[total] && t >= tl.verdictAt) {
                    fired[total] = true
                    if (anyWon) Haptics.confirm(context)
                }
            }
            if (clock.longValue >= tl.endAt) break
        }
    }

    val ended by remember(tl, clock) { derivedStateOf { clock.longValue >= tl.endAt } }
    val verdictShown by remember(tl, clock) { derivedStateOf { clock.longValue >= tl.verdictAt } }
    val phase by remember(tl, clock, result.game) {
        derivedStateOf {
            val t = clock.longValue
            when {
                t < WARMUP -> "摇奖机启动中"
                t < tl.swapStart -> "开出${result.game.zone1Label}"
                t < tl.verdictAt -> "开出${result.game.zone2Label}"
                else -> "开奖结束"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(LotteryPalette.Theater)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${result.issue} 期 · ${result.game.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ThInk,
                    modifier = Modifier.weight(1f),
                )
                MiniChip("演示数据 · 非官方", borderColor = ThHairline, textColor = ThFaint)
                TextButton(onClick = {
                    if (ended) vm.finishReplay() else skipReq.value = true
                }) {
                    Text(if (ended) "关闭" else "跳过", color = ThInk)
                }
            }

            Stage(Modifier.fillMaxWidth().weight(1f), result, tl, clock, w1, w2, phase)

            Box(Modifier.weight(0.75f), contentAlignment = Alignment.BottomCenter) {
                if (verdictShown) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        VerdictPanel(
                            verdicts = verdicts,
                            result = result,
                            reduceMotion = reduceMotion,
                            onClose = vm::finishReplay,
                        )
                    }
                }
            }
        }

        if (anyWon && !reduceMotion) {
            Confetti(tl, clock, result)
        }
    }
}

@Composable
private fun Stage(
    modifier: Modifier,
    result: DrawResult,
    tl: Timeline,
    clock: MutableLongState,
    w1: DrumWorld,
    w2: DrumWorld,
    phase: String,
) {
    val c = LocalLotteryColors.current
    val density = LocalDensity.current
    val n1 = result.zone1.size
    val n2 = result.zone2.size
    val total = n1 + n2
    val startCache = remember(tl) { MutableList<Offset?>(tl.flights.size) { null } }

    BoxWithConstraints(modifier) {
        val stageW = maxWidth
        val stageH = maxHeight
        val g = with(density) {
            val w = stageW.toPx()
            val h = stageH.toPx()
            Geom(
                stageW = w,
                stageH = h,
                drumCenter = Offset(w / 2f, h * 0.25f),
                drumR = Dp(min(stageW.value * 0.32f, stageH.value * 0.24f)).toPx(),
                rowY1 = h * 0.63f,
                rowY2 = h * 0.63f + 42.dp.toPx() + 16.dp.toPx(),
                slotS = 42.dp.toPx(),
                slotG = 8.dp.toPx(),
            )
        }
        val slotDp = 42.dp
        val z1Base = c.zone1(result.game)
        val z1Hi = c.zone1Hi(result.game)
        val z2Base = c.zone2(result.game)
        val z2Hi = c.zone2Hi(result.game)
        val scale0 = (DrumWorld.BALL_R * 2f * g.drumR) / g.slotS
        val liftPx = with(density) { 64.dp.toPx() }
        val slotCenter: (Int) -> Offset = { g.slotCenter(it, n1, n2) }
        val worldPos: (Int) -> Offset = { i ->
            val wx = if (i < n1) w1.posX(i) else w2.posX(i - n1)
            val wy = if (i < n1) w1.posY(i) else w2.posY(i - n1)
            g.drumCenter + Offset(wx, wy) * g.drumR
        }

        // 摇奖鼓
        Canvas(
            Modifier
                .offset {
                    IntOffset(
                        (g.drumCenter.x - g.drumR).roundToInt(),
                        (g.drumCenter.y - g.drumR).roundToInt(),
                    )
                }
                .size(Dp(g.drumR * 2f)),
        ) {
            val t = clock.longValue
            val r = size.width / 2f
            drawCircle(color = ThHairline, radius = r - 1.5f, style = Stroke(width = 3f))
            val thetaDeg = Math.toDegrees((DrumWorld.OMEGA * (t / 1000f)).toDouble()).toFloat()
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = thetaDeg,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(8f, 8f),
                size = Size(size.width - 16f, size.height - 16f),
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            val a1 = if (t < tl.swapStart) {
                1f
            } else {
                (1f - (t - tl.swapStart).toFloat() / SWAP_DUR).coerceIn(0f, 1f)
            }
            val a2 = ((t - tl.swapStart).toFloat() / SWAP_DUR).coerceIn(0f, 1f)
            fun drawBall(bx: Float, by: Float, base: Color, hi: Color, alpha: Float) {
                val br = DrumWorld.BALL_R * r
                val cx = r + bx * r
                val cy = r + by * r
                val shade = Color(base.red * 0.58f, base.green * 0.58f, base.blue * 0.58f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(hi.copy(alpha = alpha), base.copy(alpha = alpha), shade.copy(alpha = alpha)),
                        center = Offset(cx - br * 0.22f, cy - br * 0.32f),
                        radius = br * 1.55f,
                    ),
                    radius = br,
                    center = Offset(cx, cy),
                )
            }
            for (i in 0 until n1) {
                if (a1 <= 0.01f || t > tl.flights[i].start) continue
                drawBall(w1.posX(i), w1.posY(i), z1Base, z1Hi, a1)
            }
            for (i in 0 until n2) {
                if (a2 <= 0.01f || t > tl.flights[n1 + i].start) continue
                drawBall(w2.posX(i), w2.posY(i), z2Base, z2Hi, a2)
            }
        }

        // 阶段字幕
        Text(
            text = phase,
            style = MaterialTheme.typography.bodyMedium,
            color = ThFaint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (g.stageH * 0.50f - 10.dp.toPx()).roundToInt()) },
        )

        // 空槽（出球前占位）
        repeat(total) { i ->
            val w = tl.flights[i]
            val center = slotCenter(i)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (center.x - g.slotS / 2f).roundToInt(),
                            (center.y - g.slotS / 2f).roundToInt(),
                        )
                    }
                    .size(slotDp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .graphicsLayer {
                        alpha = if (clock.longValue > w.start) 0f else 1f
                    },
            )
        }

        // 飞行 + 落槽球
        repeat(total) { i ->
            val w = tl.flights[i]
            val center = slotCenter(i)
            val isZ1 = i < n1
            val num = if (isZ1) result.zone1[i] else result.zone2[i - n1]
            val base = if (isZ1) z1Base else z2Base
            val hi = if (isZ1) z1Hi else z2Hi
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (center.x - g.slotS / 2f).roundToInt(),
                            (center.y - g.slotS / 2f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        val t = clock.longValue
                        val p = ((t - w.start).toFloat() / FLIGHT.toFloat()).coerceIn(0f, 1f)
                        if (p <= 0f) {
                            alpha = 0f
                        } else {
                            alpha = 1f
                            if (startCache[i] == null) startCache[i] = worldPos(i)
                            val s0 = startCache[i] ?: center
                            val q = 1f - (1f - p) * (1f - p)
                            val ctrl = Offset(
                                (s0.x + center.x) / 2f,
                                (s0.y + center.y) / 2f - liftPx,
                            )
                            val mq = 1f - q
                            val pos = Offset(
                                mq * mq * s0.x + 2f * mq * q * ctrl.x + q * q * center.x,
                                mq * mq * s0.y + 2f * mq * q * ctrl.y + q * q * center.y,
                            )
                            translationX = pos.x - center.x
                            translationY = pos.y - center.y
                            var sc = scale0 + (1f - scale0) * q
                            if (t >= w.land) {
                                val u = ((t - w.land).toFloat() / 300f).coerceIn(0f, 1f)
                                sc = 1f + 0.30f * sin((u * PI).toDouble()).toFloat()
                            }
                            scaleX = sc
                            scaleY = sc
                        }
                    },
            ) {
                Box(Modifier.size(slotDp), contentAlignment = Alignment.Center) {
                    Ball(
                        number = num,
                        base = base,
                        highlight = hi,
                        size = slotDp,
                        glow = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictPanel(
    verdicts: List<Pair<Ticket, TicketVerdict>>,
    result: DrawResult,
    reduceMotion: Boolean,
    onClose: () -> Unit,
) {
    val anyWon = verdicts.any { it.second.won }
    val bestLabel = verdicts.mapNotNull { it.second.best }.minByOrNull { it.order }?.label
    val stamp = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    val density = LocalDensity.current
    val enterPx = with(density) { 56.dp.toPx() }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            stamp.snapTo(1f)
            enter.snapTo(1f)
        } else {
            enter.animateTo(1f, Motion.smooth())
            stamp.animateTo(1f, Motion.bouncy())
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * enterPx
            }
            .heightIn(max = 340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ThSurface)
            .border(1.dp, ThHairline, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "验票结果",
                style = MaterialTheme.typography.titleLarge,
                color = ThInk,
                modifier = Modifier.weight(1f),
            )
            if (anyWon) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val v = stamp.value
                            alpha = v.coerceIn(0f, 1f)
                            scaleX = 0.5f + 0.5f * v
                            scaleY = 0.5f + 0.5f * v
                            rotationZ = -7f
                        }
                        .border(2.5.dp, LotteryPalette.AccentDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (bestLabel != null) "喜中 $bestLabel" else "中奖",
                        color = LotteryPalette.AccentDark,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (verdicts.isEmpty()) {
            Text(
                text = "本期未持有票 · 下次先攒一张",
                color = ThFaint,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            verdicts.forEach { (ticket, verdict) ->
                VerdictRow(ticket, verdict, result)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = "关闭", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VerdictRow(ticket: Ticket, verdict: TicketVerdict, result: DrawResult) {
    val c = LocalLotteryColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThSurface)
            .border(1.dp, ThHairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ticket.zone1.forEach { n ->
                    val hit = n in result.zone1
                    Ball(
                        number = n,
                        base = c.zone1(ticket.game),
                        highlight = c.zone1Hi(ticket.game),
                        size = 22.dp,
                        dimmed = !hit,
                        checked = hit,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ticket.zone2.forEach { n ->
                    val hit = n in result.zone2
                    Ball(
                        number = n,
                        base = c.zone2(ticket.game),
                        highlight = c.zone2Hi(ticket.game),
                        size = 22.dp,
                        dimmed = !hit,
                        checked = hit,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val best = verdict.best
        if (verdict.won && best != null) {
            val label = if (verdict.totalWinning > 1) {
                "${best.label}×${verdict.totalWinning}"
            } else {
                best.label
            }
            Text(
                text = label,
                color = LotteryPalette.AccentDark,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text("陪跑", color = ThFaint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private class ConfettiP(
    val x0: Float,
    val delay: Long,
    val life: Long,
    val rot: Float,
    val rotSpd: Float,
    val colorIdx: Int,
    val w: Float,
    val h: Float,
    val drift: Float,
)

/** 中奖彩纸：仅中奖时播放，窗口 ≤900ms（DESIGN §4）。reduce-motion 不画。 */
@Composable
private fun Confetti(tl: Timeline, clock: MutableLongState, result: DrawResult) {
    val c = LocalLotteryColors.current
    val particles = remember(result) {
        val rng = SplitMix64(SeedHash.seedLong("conf|${result.issue}".toByteArray()))
        List(44) {
            ConfettiP(
                x0 = rng.nextInt(1000) / 1000f,
                delay = rng.nextInt(260).toLong(),
                life = (480 + rng.nextInt(220)).toLong(),
                rot = rng.nextInt(360).toFloat(),
                rotSpd = (if (rng.nextInt(2) == 0) -1 else 1) * (160f + rng.nextInt(300)),
                colorIdx = rng.nextInt(6),
                w = 6f + rng.nextInt(7),
                h = 9f + rng.nextInt(8),
                drift = rng.nextInt(201) / 100f - 1f,
            )
        }
    }
    val colors = listOf(
        LotteryPalette.AccentDark,
        c.zone1(result.game),
        c.zone2(result.game),
        Color(0xFFF2F1EC),
        Color(0xFFE8C86A),
        Color.White,
    )

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.longValue
        val local = t - tl.verdictAt
        if (local < 0L || local > 900L) return@Canvas
        particles.forEach { p ->
            val lp = (local - p.delay).toFloat() / p.life
            if (lp < 0f || lp > 1.15f) return@forEach
            val wPx = p.w.dp.toPx()
            val hPx = p.h.dp.toPx()
            val y = -20f + lp * (size.height + 40f)
            val x = p.x0 * size.width + p.drift * 90f * lp
            val a = if (lp > 0.75f) ((1.15f - lp) / 0.4f).coerceIn(0f, 1f) else 1f
            val col = colors[p.colorIdx % colors.size].copy(alpha = a)
            rotate(p.rot + p.rotSpd * lp) {
                drawRoundRect(
                    color = col,
                    topLeft = Offset(x - wPx / 2f, y - hPx / 2f),
                    size = Size(wPx, hPx),
                    cornerRadius = CornerRadius(2f),
                )
            }
        }
    }
}
