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
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.sin

// ---- 剧场固定色（不随主题）----
private val ThStage = LotteryPalette.Theater
private val ThStageHi = LotteryPalette.TheaterHi
private val ThGold = LotteryPalette.StageGold
private val ThInk = LotteryPalette.StageInk
private val ThFaint = LotteryPalette.StageFaint
private val ThSurface = Color.White.copy(alpha = 0.07f)
private val ThHairline = Color.White.copy(alpha = 0.16f)

/** 语义状态：仅在值变化时触发重组（时钟只在绘制层读取）。 */
private data class ReplayUi(
    val caption: String = "",
    val blueOn: Boolean = false,
    val drawnCount: Int = 0,
    val flashNumber: Int? = null,
    val ended: Boolean = false,
)

/**
 * W2-剧场（it-002）：直播式开奖复现。
 * 单一时钟（State<Long>）只在 Canvas/graphicsLayer 里读——每帧重绘不重组；
 * 出球旅程：机内吸顶 → 冲出出球口 → 弧线飞落对应槽位（飞行层）→ 大号读数。
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
    val numbers = remember(result) { result.zone1 + result.zone2 }
    val dropStarts = remember(beat, numbers) {
        LongArray(total) { k ->
            val base = if (k < n1) Stage3D.T_OPEN + Stage3D.T_WARM else beat.swapAt + Stage3D.T_SWAP
            base + (if (k < n1) k else k - n1) * Stage3D.T_BALL + Stage3D.T_PRE
        }
    }

    val clock = remember { mutableLongStateOf(0L) }
    val skipReq = remember { mutableStateOf(false) }
    val ui = remember { mutableStateOf(ReplayUi()) }

    LaunchedEffect(beat) {
        // 剧场是内容而非装饰：animator=0 也照常播；reduceMotion 只关装饰（it-002 hotfix）。
        var startNs = -1L
        var shifted = false
        var lastTick = -1
        while (isActive) {
            withFrameNanos { now ->
                if (startNs < 0) startNs = now
                if (skipReq.value && !shifted) {
                    shifted = true
                    val cur = (now - startNs) / 1_000_000
                    if (cur < beat.endAt - Stage3D.T_RESULT) {
                        startNs = now - (beat.endAt - Stage3D.T_RESULT) * 1_000_000
                    }
                }
                val t = ((now - startNs) / 1_000_000).coerceIn(0, beat.endAt)
                clock.longValue = t

                var drawn = 0
                var flash: Int? = null
                for (k in 0 until total) {
                    val land = dropStarts[k] + Stage3D.T_DROP + Stage3D.T_FLIGHT
                    if (t >= land) drawn++
                    if (t >= land && t < land + Stage3D.T_LAND) flash = numbers[k]
                }
                val ended = t >= beat.endAt - 1
                val next = ReplayUi(
                    caption = caption(result, t, ended, total, n1, dropStarts),
                    blueOn = t >= beat.swapAt,
                    drawnCount = drawn,
                    flashNumber = flash,
                    ended = ended,
                )
                if (next != ui.value) ui.value = next

                if (drawn > lastTick) {
                    if (lastTick >= 0) Haptics.tick(context)
                    lastTick = drawn
                }
                if (ended && lastTick != -2) {
                    lastTick = -2
                    if (anyWon) Haptics.confirm(context)
                }
            }
            if (clock.longValue >= beat.endAt) break
        }
    }

    val u = ui.value

    // 几何捕获（飞行层用）
    var stageRect by remember { mutableStateOf<Rect?>(null) }
    val slotCenters = remember { arrayOfNulls<Offset>(total) }

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
            BroadcastTopBar(result, u.ended, !u.ended && !reduceMotion, onSkip = { skipReq.value = true }, onClose = vm::finishReplay)

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .onGloballyPositioned { stageRect = it.boundsInRoot() },
            ) {
                DrawStageCanvas(
                    tState = clock,
                    swapAt = beat.swapAt,
                    poolCount = if (u.blueOn) result.game.poolZone2 else result.game.poolZone1,
                    seedTag = "${result.game}|${result.issue}|${if (u.blueOn) "b" else "r"}",
                    numbers = numbers,
                    dropStarts = dropStarts,
                    n1 = n1,
                    ballBase = if (u.blueOn) c.zone2(result.game) else c.zone1(result.game),
                    ballHi = if (u.blueOn) c.zone2Hi(result.game) else c.zone1Hi(result.game),
                    modifier = Modifier.fillMaxSize(),
                )
                MachineChip(
                    result.game,
                    blue = u.blueOn,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 8.dp),
                )
            }

            ResultPanel(
                Modifier.fillMaxWidth().weight(0.42f),
                result = result,
                u = u,
                total = total,
                n1 = n1,
                slotCenters = slotCenters,
            )
        }

        // 出球飞行层：出球口 → 弧线 → 对应槽位（最顶层）
        FlightLayer(
            clock = clock,
            numbers = numbers,
            dropStarts = dropStarts,
            n1 = n1,
            game = result.game,
            stageRect = stageRect,
            slotCenters = slotCenters,
        )

        if (u.ended) {
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

        if (anyWon && u.ended && !reduceMotion) {
            Confetti(result)
        }
    }
}

@Composable
private fun BroadcastTopBar(
    result: DrawResult,
    ended: Boolean,
    livePulse: Boolean,
    onSkip: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(livePulse)
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
    u: ReplayUi,
    total: Int,
    n1: Int,
    slotCenters: Array<Offset?>,
) {
    val c = LocalLotteryColors.current

    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = u.caption,
            style = MaterialTheme.typography.titleMedium,
            color = ThInk,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Box(Modifier.weight(0.9f), contentAlignment = Alignment.Center) {
            if (u.flashNumber != null) {
                val rm = Motion.reduceMotion()
                key(u.flashNumber, u.blueOn) {
                    val s = remember { Animatable(if (rm) 1f else 0.45f) }
                    LaunchedEffect(u.flashNumber) {
                        if (!rm) s.animateTo(1f, Motion.pop())
                    }
                    Text(
                        text = u.flashNumber.toString(),
                        style = StageReadout.copy(fontSize = 52.sp),
                        color = if (u.blueOn) c.zone2Hi(result.game) else c.zone1Hi(result.game),
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
                    filled = u.drawnCount > i,
                    base = c.zone1(result.game),
                    hi = c.zone1Hi(result.game),
                    size = slot,
                    onCenter = { slotCenters[i] = it },
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
                    filled = u.drawnCount > n1 + j,
                    base = c.zone2(result.game),
                    hi = c.zone2Hi(result.game),
                    size = slot,
                    onCenter = { slotCenters[n1 + j] = it },
                )
                if (j < result.zone2.size - 1) Spacer(Modifier.width(gap))
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (u.ended) "开奖结束 · 共 $total 球" else "已出 ${u.drawnCount} / $total 球",
            style = MaterialTheme.typography.bodySmall,
            color = ThFaint,
        )
    }
}

@Composable
private fun RailSlot(
    number: Int,
    filled: Boolean,
    base: Color,
    hi: Color,
    size: androidx.compose.ui.unit.Dp,
    onCenter: (Offset) -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .onGloballyPositioned { onCenter(it.boundsInRoot().center) },
        contentAlignment = Alignment.Center,
    ) {
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

/**
 * 出球飞行层：每球在 [ds+0.55·T_DROP, ds+T_DROP+T_FLIGHT) 内，
 * 从出球口经三次贝塞尔（先上抛出管、再弧线落槽）飞到对应槽位；带拖尾残影。
 */
@Composable
private fun FlightLayer(
    clock: State<Long>,
    numbers: List<Int>,
    dropStarts: LongArray,
    n1: Int,
    game: com.leo.lottery.core.Game,
    stageRect: Rect?,
    slotCenters: Array<Offset?>,
) {
    val c = LocalLotteryColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val painter = remember(measurer) { BallPainter(measurer) }
    val brushesRed = remember { BallBrushes(c.zone1(game), c.zone1Hi(game)) }
    val brushesBlue = remember { BallBrushes(c.zone2(game), c.zone2Hi(game)) }
    val ballR = with(density) { 23.dp.toPx() }
    val upPx = with(density) { 150.dp.toPx() }

    Canvas(Modifier.fillMaxSize()) {
        val stage = stageRect ?: return@Canvas
        val p0 = CanvasStage.tubeTopIn(stage)
        for (k in numbers.indices) {
            val f0 = dropStarts[k] + (Stage3D.T_DROP * 0.55f).toLong()
            val f1 = dropStarts[k] + Stage3D.T_DROP + Stage3D.T_FLIGHT
            val t = clock.value
            if (t < f0 || t >= f1) continue
            val raw = (t - f0).toFloat() / (f1 - f0)
            val e = raw * raw * (3f - 2f * raw)
            val p3 = slotCenters[k] ?: Offset(size.width / 2f, size.height * 0.8f)
            val c1 = Offset(p0.x, p0.y - upPx)
            val c2 = Offset((p0.x + p3.x) / 2f + (p3.x - p0.x) * 0.18f, p3.y - upPx * 0.7f)
            fun bez(u: Float): Offset {
                val mu = 1f - u
                val x = mu * mu * mu * p0.x + 3f * mu * mu * u * c1.x + 3f * mu * u * u * c2.x + u * u * u * p3.x
                val y = mu * mu * mu * p0.y + 3f * mu * mu * u * c1.y + 3f * mu * u * u * c2.y + u * u * u * p3.y
                return Offset(x, y)
            }
            val brushes = if (k < n1) brushesRed else brushesBlue
            for (g in 3 downTo 1) {
                val gu = (e - g * 0.055f).coerceAtLeast(0.001f)
                val gp = bez(gu)
                drawCircle(
                    color = (if (k < n1) c.zone1(game) else c.zone2(game)).copy(alpha = 0.05f * (4 - g)),
                    radius = ballR * (0.85f - g * 0.08f),
                    center = gp,
                )
            }
            val pos = bez(e)
            rotate(sin(e * 18f) * 8f, pivot = pos) {
                drawGlossyBall(pos.x, pos.y, ballR, numbers[k], painter, brushes)
            }
        }
    }
}

private fun caption(
    result: DrawResult,
    t: Long,
    ended: Boolean,
    total: Int,
    n1: Int,
    dropStarts: LongArray,
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
                val ds = dropStarts[k]
                val exitAt = ds + (Stage3D.T_DROP * 0.55f).toLong()
                val land = ds + Stage3D.T_DROP + Stage3D.T_FLIGHT
                val zone = if (k < n1) z1 else z2
                val idx = if (k < n1) k + 1 else k - n1 + 1
                val num = if (k < n1) result.zone1[k] else result.zone2[k - n1]
                cap = when {
                    t >= land + Stage3D.T_LAND -> "${zone}已出 $idx 个"
                    t >= land -> "$zone $num"
                    t >= exitAt -> "第 $idx 个$zone · 出球"
                    t >= ds -> "第 $idx 个$zone · 吸出"
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
