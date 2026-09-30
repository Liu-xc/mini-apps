package com.leo.lottery.ui.tickets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.core.Ticket
import com.leo.lottery.core.Verify
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.common.EmptyState
import com.leo.lottery.ui.common.MiniChip
import com.leo.lottery.ui.theme.LocalLotteryColors

/**
 * W3 票夹（US-4.1）：按时间倒序列表 + 开奖状态；空态引导去选号。
 */
@Composable
fun TicketsScreen(
    vm: LotteryViewModel,
    state: LotteryViewModel.UiState,
    @Suppress("UNUSED_PARAMETER") snackbar: SnackbarHostState,
) {
    val c = LocalLotteryColors.current
    val tickets = remember(state.tickets) { state.tickets.sortedByDescending { it.createdAt } }

    Scaffold(containerColor = c.paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("票夹", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("攒着的运气", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            Spacer(Modifier.height(20.dp))

            if (tickets.isEmpty()) {
                EmptyState(
                    illustration = { EmptyIllustration() },
                    text = "票夹还空着\n选一张图当种子，生成第一张票",
                    actionText = "去选号",
                    onAction = { vm.selectTab(LotteryViewModel.Tab.GENERATE) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        val status = remember(ticket) { statusOf(vm, ticket) }
                        TicketListItem(ticket = ticket, status = status, onClick = { vm.openDetail(ticket) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun statusOf(vm: LotteryViewModel, ticket: Ticket): Status {
    val result = vm.resultOf(ticket.game, ticket.targetIssue) ?: return Status.Upcoming
    val verdict = Verify.verify(ticket, result)
    val best = verdict.best
    return if (verdict.won && best != null) {
        if (verdict.totalWinning > 1) {
            Status.Won("${best.label}×${verdict.totalWinning}")
        } else {
            Status.Won(best.label)
        }
    } else {
        Status.Lost
    }
}

private sealed interface Status {
    data object Upcoming : Status
    data object Lost : Status
    data class Won(val label: String) : Status
}

@Composable
private fun TicketListItem(ticket: Ticket, status: Status, onClick: () -> Unit) {
    val c = LocalLotteryColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${ticket.game.label} · 第${ticket.targetIssue}期",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                when (status) {
                    Status.Upcoming -> MiniChip("未开奖")
                    Status.Lost -> MiniChip("陪跑")
                    is Status.Won -> MiniChip(status.label, borderColor = c.accent, textColor = c.accent)
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ticket.zone1.forEach { n ->
                    Ball(
                        number = n,
                        base = c.zone1(ticket.game),
                        highlight = c.zone1Hi(ticket.game),
                        size = 22.dp,
                    )
                }
                Spacer(Modifier.width(2.dp))
                ticket.zone2.forEach { n ->
                    Ball(
                        number = n,
                        base = c.zone2(ticket.game),
                        highlight = c.zone2Hi(ticket.game),
                        size = 22.dp,
                    )
                }
            }
            Text(
                text = if (ticket.isCombo) {
                    "复式 ${ticket.combos}注 · 种子 ${ticket.seed.take(8)}… · 第${ticket.take}批"
                } else {
                    "种子 ${ticket.seed.take(8)}… · 第${ticket.take}批"
                },
                style = MaterialTheme.typography.labelSmall,
                color = c.inkFaint,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.inkFaint,
        )
    }
}

@Composable
private fun EmptyIllustration() {
    val c = LocalLotteryColors.current
    Canvas(Modifier.size(96.dp)) {
        val r = size.minDimension / 2f - 8f
        drawCircle(
            color = c.hairline,
            radius = r,
            center = center,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)),
            ),
        )
        drawCircle(color = c.accent, radius = r * 0.32f, center = center)
        drawCircle(
            color = c.inkFaint.copy(alpha = 0.35f),
            radius = r * 0.6f,
            center = Offset(center.x + r * 0.72f, center.y - r * 0.72f),
        )
    }
}
