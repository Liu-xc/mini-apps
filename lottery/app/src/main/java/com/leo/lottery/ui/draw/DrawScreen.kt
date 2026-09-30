package com.leo.lottery.ui.draw

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.core.DrawResult
import com.leo.lottery.core.Game
import com.leo.lottery.core.IssueCalendar
import com.leo.lottery.core.Ticket
import com.leo.lottery.core.TicketVerdict
import com.leo.lottery.core.Verify
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.common.MiniChip
import com.leo.lottery.ui.common.PrimaryButton
import com.leo.lottery.ui.common.SegmentedPill
import com.leo.lottery.ui.theme.LocalLotteryColors

/**
 * W2 开奖：期号导航 + 「复现本期开奖」剧场入口；已揭晓后亮出号码与逐票验票结果。
 */
@Composable
fun DrawScreen(
    vm: LotteryViewModel,
    state: LotteryViewModel.UiState,
    @Suppress("UNUSED_PARAMETER") snackbar: SnackbarHostState,
) {
    val d = state.draw
    val c = LocalLotteryColors.current
    val result = vm.resultOf(d.game, d.issue)
    val revealed = d.revealedKey in d.revealed
    val myTickets = vm.ticketsFor(d.game, d.issue)
    val idx = d.issueList.indexOf(d.issue)

    Scaffold(containerColor = c.paper) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("开奖复现", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("错过的直播，这里补回来", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            Spacer(Modifier.height(20.dp))

            SegmentedPill(
                options = listOf("双色球", "大乐透"),
                selectedIndex = if (d.game == Game.SSQ) 0 else 1,
                onSelect = { vm.setDrawGame(if (it == 0) Game.SSQ else Game.DLT) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.stepIssue(-1) }, enabled = idx > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一期",
                        tint = if (idx > 0) c.ink else c.hairline,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val date = remember(d.game, d.issue) {
                        IssueCalendar.dateOfIssue(d.game, d.issue)?.toString() ?: ""
                    }
                    Text("第 ${d.issue} 期", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text(date, style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                }
                val hasNext = idx >= 0 && idx < d.issueList.size - 1
                IconButton(onClick = { vm.stepIssue(1) }, enabled = hasNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一期",
                        tint = if (hasNext) c.ink else c.hairline,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MiniChip(text = "演示数据 · 非官方")
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (revealed) "再看一次" else "▶  复现本期开奖",
                onClick = vm::startReplay,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!revealed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (myTickets.isEmpty()) {
                        "开奖号码会在复现结束后揭晓"
                    } else {
                        "本期有 ${myTickets.size} 张票 · 复现结束自动验票"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (revealed && result != null) {
                Spacer(Modifier.height(24.dp))
                Text("本期开奖号码", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    result.zone1.forEach {
                        Ball(
                            number = it,
                            base = c.zone1(d.game),
                            highlight = c.zone1Hi(d.game),
                            size = 38.dp,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    result.zone2.forEach {
                        Ball(
                            number = it,
                            base = c.zone2(d.game),
                            highlight = c.zone2Hi(d.game),
                            size = 38.dp,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                if (myTickets.isEmpty()) {
                    EmptyNoTicket()
                } else {
                    Text("我的票", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    Spacer(Modifier.height(10.dp))
                    myTickets.forEach { ticket ->
                        val verdict = remember(ticket, result) { Verify.verify(ticket, result) }
                        TicketVerdictRow(ticket, verdict, result)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EmptyNoTicket() {
    val c = LocalLotteryColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("本期暂无票", style = MaterialTheme.typography.titleMedium, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "先去选号攒一张，下期开奖一起验",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkFaint,
        )
    }
}

@Composable
private fun TicketVerdictRow(ticket: Ticket, verdict: TicketVerdict, result: DrawResult) {
    val c = LocalLotteryColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
        PrizeBadge(verdict)
    }
}

@Composable
private fun PrizeBadge(verdict: TicketVerdict) {
    val c = LocalLotteryColors.current
    if (verdict.won && verdict.best != null) {
        val best = verdict.best!!
        val label = if (verdict.totalWinning > 1) "${best.label}×${verdict.totalWinning}" else best.label
        Box(
            Modifier
                .border(2.dp, c.accent, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                label,
                color = c.accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        Text("陪跑", style = MaterialTheme.typography.bodyMedium, color = c.inkFaint)
    }
}
