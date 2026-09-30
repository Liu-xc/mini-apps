package com.leo.lottery.ui.tickets

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.core.Verify
import com.leo.lottery.platform.Haptics
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.common.MiniChip
import com.leo.lottery.ui.common.PrimaryButton
import com.leo.lottery.ui.common.SecondaryButton
import com.leo.lottery.ui.common.captureLayer
import com.leo.lottery.ui.common.rememberTicketCapture
import com.leo.lottery.ui.theme.LocalLotteryColors
import kotlinx.coroutines.launch

/**
 * W4 票详情（US-4.3）：完整票卡 + 开奖状态 + 导出/去验票/删除（撤销走全局 snackbar）。
 */
@Composable
fun TicketDetailScreen(
    vm: LotteryViewModel,
    state: LotteryViewModel.UiState,
    snackbar: SnackbarHostState,
) {
    val ticket = state.detail ?: return
    val c = LocalLotteryColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberTicketCapture()
    val result = remember(ticket) { vm.resultOf(ticket.game, ticket.targetIssue) }
    val verdict = remember(ticket, result) { result?.let { Verify.verify(ticket, it) } }

    Scaffold(containerColor = c.paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeDetail, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.ink)
                }
                Text("票详情", style = MaterialTheme.typography.titleLarge, color = c.ink)
            }
            Spacer(Modifier.height(8.dp))

            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .captureLayer(capture),
            ) {
                TicketCard(ticket = ticket)
            }

            Spacer(Modifier.height(16.dp))
            when {
                result == null -> {
                    MiniChip("未开奖 · 开奖后可验票")
                }
                verdict != null -> {
                    Text("第 ${result.issue} 期开奖号码", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        result.zone1.forEach {
                            Ball(number = it, base = c.zone1(result.game), highlight = c.zone1Hi(result.game), size = 28.dp)
                        }
                        Spacer(Modifier.width(2.dp))
                        result.zone2.forEach {
                            Ball(number = it, base = c.zone2(result.game), highlight = c.zone2Hi(result.game), size = 28.dp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val best = verdict.best
                    if (verdict.won && best != null) {
                        val label = if (verdict.totalWinning > 1) {
                            "喜中 ${best.label} × ${verdict.totalWinning}注"
                        } else {
                            "喜中 ${best.label}"
                        }
                        Text(label, color = c.accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "命中 ${verdict.setHits} · 本期陪跑，下期再来",
                            color = c.inkFaint,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "导出图片",
                    onClick = {
                        scope.launch {
                            val ok = capture.export(context, ticket)
                            if (ok) {
                                Haptics.confirm(context)
                                snackbar.showSnackbar("已存入相册 Pictures/拾彩")
                            } else {
                                Haptics.warn(context)
                                snackbar.showSnackbar("导出失败，再试一次")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = "去验票",
                    onClick = { vm.goToDraw(ticket) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = "删除",
                    onClick = { vm.deleteTicket(ticket) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
