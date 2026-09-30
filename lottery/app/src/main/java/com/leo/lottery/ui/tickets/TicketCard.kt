package com.leo.lottery.ui.tickets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.lottery.core.IssueCalendar
import com.leo.lottery.core.SeedHash
import com.leo.lottery.core.Ticket
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.common.MiniChip
import com.leo.lottery.ui.theme.LocalLotteryColors
import com.leo.lottery.ui.theme.LotteryPalette
import com.leo.lottery.ui.theme.Motion
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 购票卡：W1 生成结果预览、W4 详情、导出位图三处同一渲染器（US-4.2）。
 * 恒浅纸（导出印刷语义，不随主题翻转）。rollKey 非空时球号滚码落定动画。
 */
@Composable
fun TicketCard(
    ticket: Ticket,
    modifier: Modifier = Modifier,
    rollKey: Long? = null,
) {
    val reduceMotion = Motion.reduceMotion()
    val paper = LotteryPalette.CardPaper
    val ink = LotteryPalette.CardInk
    val inkFaint = LotteryPalette.CardInkFaint
    val hairline = LotteryPalette.CardHairline

    val entrance = remember(rollKey) { Animatable(if (rollKey == null || reduceMotion) 1f else 0f) }
    LaunchedEffect(rollKey) {
        if (rollKey != null && !reduceMotion) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(260))
        }
    }

    Column(
        modifier = modifier
            .alpha(entrance.value)
            .clip(RoundedCornerShape(18.dp))
            .background(paper)
            .border(1.dp, hairline, RoundedCornerShape(18.dp)),
    ) {
        // 红头带（票头）
        Row(
            Modifier
                .fillMaxWidth()
                .background(paper)
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ticket.game.label,
                style = MaterialTheme.typography.titleLarge,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (ticket.isCombo) "复式 · ${ticket.combos}注" else "单式 · 1注",
                style = MaterialTheme.typography.labelSmall,
                color = inkFaint,
                modifier = Modifier
                    .border(1.dp, hairline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        val issueDate = remember(ticket.game, ticket.targetIssue) {
            IssueCalendar.dateOfIssue(ticket.game, ticket.targetIssue)?.toString()
                ?: run {
                    val d = Instant.ofEpochMilli(ticket.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    d.toString()
                }
        }
        Text(
            text = "第${ticket.targetIssue}期 · $issueDate",
            style = MaterialTheme.typography.labelSmall,
            color = inkFaint,
        )
        Spacer(Modifier.height(14.dp))

        // 号码区
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BallRow(
                numbers = ticket.zone1,
                base = zoneColor(ticket, zone = 1),
                hi = zoneHiColor(ticket, zone = 1),
                rollKey = rollKey,
                reduceMotion = reduceMotion,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", color = inkFaint, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                BallRow(
                    numbers = ticket.zone2,
                    base = zoneColor(ticket, zone = 2),
                    hi = zoneHiColor(ticket, zone = 2),
                    rollKey = rollKey,
                    reduceMotion = reduceMotion,
                    staggerOffset = 6,
                )
            }
        }

        if (ticket.isCombo) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "复式 · ${ticket.game.zone1Label}${ticket.zone1.size}码 · 共${ticket.combos}注",
                style = MaterialTheme.typography.bodySmall,
                color = inkFaint,
            )
        }

        Spacer(Modifier.height(14.dp))
        // 撕线
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(1.dp),
        ) {
            drawLine(
                color = hairline,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
        Spacer(Modifier.height(10.dp))

        // 页脚：种子指纹 + 批次 + 品牌条码
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("种子 ${ticket.seed}", style = MaterialTheme.typography.labelSmall, color = inkFaint)
                Spacer(Modifier.height(2.dp))
                Text(
                    "第${ticket.take}批 · ${formatTime(ticket.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = inkFaint,
                )
            }
            Text("拾彩", style = MaterialTheme.typography.titleMedium, color = ink)
            Spacer(Modifier.width(8.dp))
            SeedBarcode(ticket.seed, Modifier.width(48.dp).height(24.dp))
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BallRow(
    numbers: List<Int>,
    base: Color,
    hi: Color,
    rollKey: Long?,
    reduceMotion: Boolean,
    staggerOffset: Int = 0,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.forEachIndexed { i, n ->
            RollingBall(
                final = n,
                base = base,
                hi = hi,
                size = 34.dp,
                index = i + staggerOffset,
                rollKey = rollKey,
                reduceMotion = reduceMotion,
            )
        }
    }
}

@Composable
private fun RollingBall(
    final: Int,
    base: Color,
    hi: Color,
    size: Dp,
    index: Int,
    rollKey: Long?,
    reduceMotion: Boolean,
) {
    var display by remember(rollKey) { mutableStateOf(final) }
    val scale = remember(rollKey) { Animatable(if (rollKey == null || reduceMotion) 1f else 0.55f) }
    val rng = remember(rollKey) { Random(rollKey ?: 0) }

    LaunchedEffect(rollKey) {
        if (rollKey == null || reduceMotion) return@LaunchedEffect
        delay(index * 22L)
        val rollUntil = System.currentTimeMillis() + 260
        while (System.currentTimeMillis() < rollUntil && isActive) {
            display = rng.nextInt(1, 40)
            delay(45)
        }
        display = final
        scale.snapTo(.92f)
        scale.animateTo(1f, tween(180))
    }

    Box(Modifier.size(size)) {
        Ball(
            number = display,
            base = base,
            highlight = hi,
            size = size,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** 由种子指纹推出的装饰条码（纯装饰，不承载信息）。 */
@Composable
private fun SeedBarcode(seed: String, modifier: Modifier = Modifier) {
    val ink = LotteryPalette.CardInk
    val widths = remember(seed) {
        val h = SeedHash.sha256(seed.toByteArray())
        h.take(16).map { 1 + (it.toInt() and 0xFF) % 3 }
    }
    Canvas(modifier) {
        var x = 0f
        val h = size.height
        widths.forEachIndexed { i, w ->
            val bw = w * (size.width / 40f)
            if (i % 2 == 0) {
                drawRect(
                    color = ink,
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(bw, h),
                )
            }
            x += bw
        }
    }
}

private fun zoneColor(ticket: Ticket, zone: Int): Color =
    if (ticket.game == com.leo.lottery.core.Game.SSQ) {
        if (zone == 1) LotteryPalette.SsqRed else LotteryPalette.SsqBlue
    } else {
        if (zone == 1) LotteryPalette.DltFront else LotteryPalette.DltBack
    }

private fun zoneHiColor(ticket: Ticket, zone: Int): Color =
    if (ticket.game == com.leo.lottery.core.Game.SSQ) {
        if (zone == 1) LotteryPalette.SsqRedHi else LotteryPalette.SsqBlueHi
    } else {
        if (zone == 1) LotteryPalette.DltFrontHi else LotteryPalette.DltBackHi
    }

private fun formatTime(epochMs: Long): String {
    val d = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
}
