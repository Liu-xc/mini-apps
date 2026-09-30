package com.leo.lottery.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.leo.lottery.ui.draw3d.Stage3D
import com.leo.lottery.ui.theme.LocalLotteryColors
import com.leo.lottery.ui.theme.LotteryPalette
import com.leo.lottery.ui.theme.Motion
import com.leo.lottery.ui.theme.StageReadout
import kotlinx.coroutines.isActive

// ---- 剧场固定色（不随主题）----
private val ThStage = LotteryPalette.Theater
private val ThStageHi = LotteryPalette.TheaterHi
private val ThGold = LotteryPalette.StageGold
private val ThInk = LotteryPalette.StageInk
private val ThFaint = LotteryPalette.StageFaint
private val ThSurface = Color.White.copy(alpha = 0.07f)
private val ThHairline = Color.White.copy(alpha = 0.16f)

/**
 * W2-剧场（it-002 / US-2R）：直播式开奖复现。
 * 摇奖机（Canvas 2.5D 确定性舞台；Filament 真三维实现在 ui.draw3d，模拟器原生层不稳，
 * 真机验证后可切换，ADR-006）→ 出球轨道 → 大号读数 → 解说字幕 → 终幕验票；
 * 跳过直达验票；reduce-motion 直接终态。
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

    val c = LocalLotteryColors.current
    val n1 = result.zone1.size
    val n2 = result.zone2.size
    val total = n1 + n2
    val beat = remember(result) { Stage3D.beat(n1, n2) }

    fun dropStartOf(k: Int): Long {
        val base = if (k < n1) Stage3D.T_OPEN + Stage3D.T_WARM else beat.swapAt + Stage3D.T_SWAP
        return base + (if (k < n1) k else k - n1) * Stage3D.T_BALL + Stage3D.T_PRE
    }

    val clock = remember { mutableLongStateOf(0L) }
    val skipReq = remember { mutableStateOf(false) }
    val fired = remember(beat) { BooleanArray(total + 1) }

    LaunchedEffect(beat, reduceMotion) {
        if (reduceMotion) {
            clock.longValue = beat.endAt
            fired.fill(true)
            if (anyWon) Haptics.confirm(context)
            return@LaunchedEffect
        }
        var startNs = -1L
        var shifted = false
        while (isActive) {
            withFrameNanos { now ->
                if (startNs < 0) startNs = now
                if (skipReq.value && !shifted) {
                    shifted = true
                    val cur = (now - startNs) / 1_000_000
                    if (cur < beat.endAt - Stage3D.T_RESULT) {
                        startNs = now - (beat.endAt - Stage3D.T_RESULT) * 1_000_000
                    }
                    for (i in 0 until total) fired[i] = true
                }
                val t = ((now - startNs) / 1_000_000).coerceIn(0, beat.endAt)
                clock.longValue = t
                for (i in 0 until total) {
                    if (!fired[i] && t >= dropStartOf(i) + Stage3D.T_DROP) {
                        fired[i] = true
                        Haptics.tick(context)
                    }
                }
                if (!fired[total] && t >= beat.endAt - Stage3D.T_RESULT) {
                    fired[total] = true
                    if (anyWon) Haptics.confirm(context)
                }
            }
            if (clock.longValue >= beat.endAt) break
        }
    }

    val t = clock.longValue
    val blueOn = t >= beat.swapAt
    val drawnCount = run {
        var k = 0
        for (i in 0 until total) if (t >= dropStartOf(i) + Stage3D.T_DROP + 140L) k++
        k
    }
    val flashNumber = run {
        for (i in 0 until total) {
            val land = dropStartOf(i) + Stage3D.T_DROP
            if (t >= land && t < land + Stage3D.T_LAND) {
                return@run if (i < n1) result.zone1[i] else result.zone2[i - n1]
            }
        }
        null
    }
    val ejectNumber = run {
        for (i in 0 until total) {
            val ds = dropStartOf(i)
            if (t >= ds && t < ds + Stage3D.T_DROP) {
                return@run if (i < n1) result.zone1[i] else result.zone2[i - n1]
            }
        }
        null
    }
    val ended = t >= beat.endAt - 1
    val drawnSet = buildSet {
        for (i in 0 until total) if (t >= dropStartOf(i) + Stage3D.T_DROP) {
            add(if (i < n1) result.zone1[i] else result.zone2[i - n1])
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 舞台背景（深蓝演播厅 + 聚光灯）
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(ThStageHi, ThStage, Color(0xFF05080F)),
                ),
            )
            listOf(-1f, 1f).forEach { side ->
                val cx = size.width / 2f + side * size.width * 0.32f
                val coneW = size.width * 0.13f
                val path = Path().apply {
                    moveTo(cx - coneW, 0f)
                    lineTo(cx + coneW, 0f)
                    lineTo(cx + coneW * 2.4f, size.height * 0.62f)
                    lineTo(cx - coneW * 2.4f, size.height * 0.62f)
                    close()
                }
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.62f,
                    ),
                )
            }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            BroadcastTopBar(result, ended, onSkip = { skipReq.value = true }, onClose = vm::finishReplay)

            Box(Modifier.fillMaxWidth().weight(0.58f)) {
                DrawStageCanvas(
                    t = t,
                    blue = blueOn,
                    game = result.game,
                    poolCount = if (blueOn) result.game.poolZone2 else result.game.poolZone1,
                    drawNumbers = drawnSet,
                    ejectNumber = ejectNumber,
                    seedTag = "${result.game}|${result.issue}|${if (blueOn) "b" else "r"}",
                    ballBase = if (blueOn) c.zone2(result.game) else c.zone1(result.game),
                    ballHi = if (blueOn) c.zone2Hi(result.game) else c.zone1Hi(result.game),
                    modifier = Modifier.fillMaxSize(),
                )
                MachineChip(
                    result.game,
                    blue = blueOn,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 8.dp),
                )
            }

            ResultPanel(
                Modifier.fillMaxWidth().weight(0.42f),
                result = result,
                t = t,
                blueOn = blueOn,
                drawnCount = drawnCount,
                flashNumber = flashNumber,
                ended = ended,
                total = total,
                n1 = n1,
                dropStartOf = ::dropStartOf,
            )
        }

        if (ended) {
            Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    VerdictPanel(
                        verdicts = verdicts,
                        result = result,
                        reduceMotion = reduceMotion,
                        onClose = vm::finishReplay,
                    )
                }
            }
        }

        if (anyWon && ended && !reduceMotion) {
            Confetti(result)
        }
    }
}

@Composable
private fun BroadcastTopBar(
    result: DrawResult,
    ended: Boolean,
    onSkip: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(!ended)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "开奖直播",
            style = MaterialTheme.typography.titleMedium,
            color = ThInk,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${result.game.label} · 第 ${result.issue} 期",
            style = MaterialTheme.typography.bodyMedium,
            color = ThFaint,
            modifier = Modifier.weight(1f),
        )
        MiniChip("演示数据 · 非官方", borderColor = ThHairline, textColor = ThFaint)
        TextButton(onClick = if (ended) onClose else onSkip) {
            Text(if (ended) "关闭" else "跳过", color = ThInk)
        }
    }
}

@Composable
private fun LiveDot(pulse: Boolean) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "live")
        t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "liveA",
        ).value
    } else {
        0.8f
    }
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color(0xFFE8362B)),
    )
}

@Composable
private fun MachineChip(game: com.leo.lottery.core.Game, blue: Boolean, modifier: Modifier) {
    val label = if (blue) "${game.zone2Label}机" else "${game.zone1Label}机"
    val color = if (blue) LotteryPalette.SsqBlue else LotteryPalette.SsqRed
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultPanel(
    modifier: Modifier,
    result: DrawResult,
    t: Long,
    blueOn: Boolean,
    drawnCount: Int,
    flashNumber: Int?,
    ended: Boolean,
    total: Int,
    n1: Int,
    dropStartOf: (Int) -> Long,
) {
    val c = LocalLotteryColors.current

    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val caption = broadcastCaption(result, t, blueOn, ended, total, n1, dropStartOf)
        Text(
            text = caption,
            style = MaterialTheme.typography.titleMedium,
            color = ThInk,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Box(Modifier.weight(0.9f), contentAlignment = Alignment.Center) {
            if (flashNumber != null) {
                val rm = Motion.reduceMotion()
                key(flashNumber, blueOn) {
                    val s = remember { Animatable(if (rm) 1f else 0.45f) }
                    LaunchedEffect(flashNumber) {
                        if (!rm) s.animateTo(1f, Motion.pop())
                    }
                    Text(
                        text = flashNumber.toString(),
                        style = StageReadout.copy(fontSize = 52.sp),
                        color = if (blueOn) c.zone2Hi(result.game) else c.zone1Hi(result.game),
                        modifier = Modifier.graphicsLayer {
                            scaleX = s.value
                            scaleY = s.value
                        },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val slot = 44.dp
            val gap = 7.dp
            repeat(n1) { i ->
                RailSlot(
                    number = result.zone1[i],
                    filled = drawnCount > i,
                    base = c.zone1(result.game),
                    hi = c.zone1Hi(result.game),
                    size = slot,
                )
                if (i < n1 - 1) Spacer(Modifier.width(gap))
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(slot)
                    .background(ThHairline),
            )
            Spacer(Modifier.width(14.dp))
            repeat(result.zone2.size) { j ->
                RailSlot(
                    number = result.zone2[j],
                    filled = drawnCount > n1 + j,
                    base = c.zone2(result.game),
                    hi = c.zone2Hi(result.game),
                    size = slot,
                )
                if (j < result.zone2.size - 1) Spacer(Modifier.width(gap))
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (ended) "开奖结束 · 共 $total 球" else "已出 $drawnCount / $total 球",
            style = MaterialTheme.typography.bodySmall,
            color = ThFaint,
        )
    }
}

@Composable
private fun RailSlot(number: Int, filled: Boolean, base: Color, hi: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!filled) {
            Box(
                Modifier
                    .size(size)
                    .border(1.5.dp, ThGold.copy(alpha = 0.34f), CircleShape),
            )
        } else {
            val rm = Motion.reduceMotion()
            key(number) {
                val s = remember { Animatable(if (rm) 1f else 0.3f) }
                LaunchedEffect(number) {
                    if (!rm) s.animateTo(1f, Motion.bouncy())
                }
                Box(Modifier.graphicsLayer {
                    scaleX = s.value
                    scaleY = s.value
                }) {
                    Ball(number = number, base = base, highlight = hi, size = size, glow = true)
                }
            }
        }
    }
}

private fun broadcastCaption(
    result: DrawResult,
    t: Long,
    blueOn: Boolean,
    ended: Boolean,
    total: Int,
    n1: Int,
    dropStartOf: (Int) -> Long,
): String {
    val z1 = result.game.zone1Label
    val z2 = result.game.zone2Label
    val swapAt = Stage3D.beat(n1, result.zone2.size).swapAt
    if (ended) return "开奖结束"
    return when {
        t < Stage3D.T_OPEN -> "开奖直播 · 马上开始"
        t < Stage3D.T_OPEN + Stage3D.T_WARM -> "摇奖机预热 · 气流启动"
        t in swapAt..(swapAt + Stage3D.T_SWAP) -> "切换${z2}摇奖机"
        else -> {
            var cap = "摇奖进行中"
            for (k in 0 until total) {
                val ds = dropStartOf(k)
                val land = ds + Stage3D.T_DROP
                val zone = if (k < n1) z1 else z2
                val idx = if (k < n1) k + 1 else k - n1 + 1
                val num = if (k < n1) result.zone1[k] else result.zone2[k - n1]
                cap = when {
                    t >= land + Stage3D.T_LAND -> "${zone}已出 $idx 个"
                    t >= land -> "$zone $num"
                    t >= ds -> "第 $idx 个$zone · 出球"
                    else -> return cap
                }
            }
            cap
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
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE60B1428))
            .border(1.dp, ThGold.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
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
                        .border(2.5.dp, LotteryPalette.StageGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (bestLabel != null) "喜中 $bestLabel" else "中奖",
                        color = LotteryPalette.StageGold,
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
        TextButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LotteryPalette.AccentLight),
        ) {
            Text("关闭", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
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
                color = LotteryPalette.StageGold,
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

/** 中奖彩纸：仅中奖时播放；reduce-motion 不画。 */
@Composable
private fun Confetti(result: DrawResult) {
    val c = LocalLotteryColors.current
    val started = remember { System.nanoTime() }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            tick.longValue = (System.nanoTime() - started) / 1_000_000
            withFrameNanos { }
        }
    }
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
        LotteryPalette.AccentLight,
        c.zone1(result.game),
        c.zone2(result.game),
        ThInk,
        LotteryPalette.StageGold,
        Color.White,
    )

    Canvas(Modifier.fillMaxSize()) {
        val t = tick.longValue
        if (t > 1300L) return@Canvas
        particles.forEach { p ->
            val lp = (t - p.delay).toFloat() / p.life
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
                    size = androidx.compose.ui.geometry.Size(wPx, hPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                )
            }
        }
    }
}
