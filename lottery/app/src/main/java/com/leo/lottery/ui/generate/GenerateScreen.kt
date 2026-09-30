package com.leo.lottery.ui.generate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.platform.Haptics
import com.leo.lottery.ui.common.PrimaryButton
import com.leo.lottery.ui.common.SecondaryButton
import com.leo.lottery.ui.common.SegmentedPill
import com.leo.lottery.ui.common.Stepper
import com.leo.lottery.ui.common.captureLayer
import com.leo.lottery.ui.common.rememberTicketCapture
import com.leo.lottery.ui.tickets.TicketCard
import com.leo.lottery.ui.theme.LocalLotteryColors
import kotlinx.coroutines.launch

/**
 * W1 选号：玩法 → 单式/复式 → 图片种子 → 生成（滚码落定）→ 存票/导出/再换一批。
 */
@Composable
fun GenerateScreen(
    vm: LotteryViewModel,
    state: LotteryViewModel.UiState,
    snackbar: SnackbarHostState,
) {
    val g = state.generate
    val c = LocalLotteryColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberTicketCapture()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.pickSeed(uri) }

    Scaffold(containerColor = c.paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("选号", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("把今天的灵感，留在一张票里", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(20.dp))
            SectionLabel("灵感图片")
            Spacer(Modifier.height(8.dp))
            val seed = g.seed
            if (seed == null) {
                SeedEmptyCard(
                    busy = g.seedBusy,
                    onPick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
            } else {
                SeedCard(
                    thumb = { Image(seed.thumb.asImageBitmap(), contentDescription = null, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) },
                    fingerprint = seed.fingerprint,
                    busy = g.seedBusy,
                    onReplace = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
            }


            Spacer(Modifier.height(24.dp))
            SectionLabel("玩法")
            Spacer(Modifier.height(8.dp))
            SegmentedPill(
                options = listOf("双色球 6+1", "大乐透 5+2"),
                selectedIndex = if (g.game == com.leo.lottery.core.Game.SSQ) 0 else 1,
                onSelect = { vm.setGame(if (it == 0) com.leo.lottery.core.Game.SSQ else com.leo.lottery.core.Game.DLT) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("投注方式")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedPill(
                    options = listOf("单式", "复式"),
                    selectedIndex = if (g.combo) 1 else 0,
                    onSelect = { vm.setCombo(it == 1) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (g.combo) {
                    Stepper(
                        value = g.comboZone1,
                        min = g.game.comboMinZone1,
                        max = g.game.comboMaxZone1,
                        onChange = vm::setComboZone1,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (g.combo) {
                    "${g.game.zone1Label}${g.zone1Size}码 · ${g.comboNote}注 · ${g.game.zone2Label}单式"
                } else {
                    "${g.game.zone1Label}${g.game.baseZone1} + ${g.game.zone2Label}${g.game.baseZone2} · 1注"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkFaint,
            )


            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (g.seedBusy) "读图中…" else "生成号码",
                onClick = vm::generate,
                enabled = g.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            )

            val gen = g.generated
            if (gen != null) {
                Spacer(Modifier.height(24.dp))
                if (gen.stale) {
                    Text(
                        "设置已变 · 重新生成以更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkFaint,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (gen.stale) Modifier.alpha(0.4f) else Modifier)
                        .captureLayer(capture),
                ) {
                    TicketCard(ticket = gen.ticket, rollKey = gen.animKey)
                }

                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = if (gen.saved) "已存入 ✓" else "存入票夹",
                        onClick = {
                            val saved = vm.saveCurrent()
                            if (saved != null) {
                                Haptics.confirm(context)
                            }
                        },
                        enabled = !gen.saved && !gen.stale,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "导出图片",
                        onClick = {
                            val ticket = vm.saveCurrent() ?: return@SecondaryButton
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
                        enabled = !gen.stale,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "再换一批",
                        onClick = vm::regenerateBatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalLotteryColors.current
    Text(text, style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
}

@Composable
private fun SeedEmptyCard(busy: Boolean, onPick: () -> Unit) {
    val c = LocalLotteryColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(188.dp)
            .drawBehind {
                drawRoundRect(
                    color = c.hairline,
                    topLeft = Offset(1f, 1f),
                    size = Size(size.width - 2f, size.height - 2f),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
            .clip(RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = c.inkFaint,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("从一张喜欢的照片开始", style = MaterialTheme.typography.bodyMedium, color = c.inkFaint)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onPick) {
                    Text("选择图片", color = c.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SeedCard(
    thumb: @Composable () -> Unit,
    fingerprint: String,
    busy: Boolean,
    onReplace: () -> Unit,
) {
    val c = LocalLotteryColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        thumb()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("这一张照片的幸运签", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
            Text(
                fingerprint.take(8) + " · " + fingerprint.takeLast(4),
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onReplace) {
                Text("换一张", color = c.accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
