package com.leo.darkroom.ui.develop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.platform.ShakeDetector
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.editorialColors
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sin

/**
 * W2 显影台：有重量的出纸 → 三阶段显影 → 定影定格。
 * 彩蛋：药水条拖动重看、甩一甩推进（真陀螺仪加速度计）。
 */
@Composable
fun DevelopScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    val context = LocalContext.current
    val reduceMotion = EditorialMotion.reduceMotion()

    // 出纸动画：卡片自槽口升起（1 → 0），随后启动显影
    val eject = remember { Animatable(1f) }
    val settle = remember { Animatable(1f) } // 定影落定轻弹 scale
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            eject.snapTo(0f)
            vm.onEjectDone()
            vm.skipDevelop()
        } else {
            eject.animateTo(0f, EditorialMotion.pop())
            vm.onEjectDone()
        }
    }

    // 定影落定：scale 1→1.03→1（DESIGN.md 落定轻弹）
    LaunchedEffect(state.progress >= 1f) {
        if (state.progress >= 1f) {
            EditorialMotion.runSettlePulse(settle)
        }
    }

    // 甩一甩（仅显影中、未减弱动态时注册）
    DisposableEffect(state.shakeEnabled, reduceMotion) {
        val detector = if (state.shakeEnabled && !reduceMotion) {
            ShakeDetector(context) { vm.boost(0.10f) }.also { it.start() }
        } else null
        onDispose { detector?.stop() }
    }

    // 药水条拖动：拖起即暂停，松手续播
    var scrubbing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶行
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::backToPick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "DEVELOPING",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.speed.label} · ${DevelopSpec.phaseAt(state.progress).label}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink,
            )
        }

        Spacer(Modifier.weight(1f))

        // 显影台：槽口 + 卡片
        BoxWithConstraints(
            Modifier.weight(2.4f),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            // it-002 O3：可用高度反解卡宽（weight 槽高不足时整卡含签名区不越界）
            val layout = com.leo.darkroom.card.CardLayout.solve(
                with(density) { maxWidth.toPx() - 2.dp.toPx() },
                with(density) { maxHeight.toPx() },
            )
            val cardWidthPx = layout.width
            val cardHeightPx = layout.height

            Box(contentAlignment = Alignment.Center) {
                // 卡片（出纸位移 + 定影落定缩放）
                Box(
                    Modifier.graphicsLayer {
                        translationY = eject.value * cardHeightPx
                        rotationZ = sin((1f - eject.value) * PI.toFloat()) * 1.15f
                        scaleX = settle.value
                        scaleY = settle.value
                    },
                ) {
                    DevelopCard(
                        photo = state.photo,
                        spec = state.spec,
                        progress = state.progress,
                        cardWidthPx = cardWidthPx,
                        grain = vm.grain,
                    )
                }
                // 相机槽口：固定在卡片最终底缘之下（照片升起后完全脱出，不压卡脚）；
                // 出纸过程中卡片自其后方升起——槽口画在卡片之上
                if (eject.value > 0.001f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .offset { IntOffset(0, 12.dp.roundToPx()) }
                            .width(with(density) { (cardWidthPx * 0.92f).toDp() })
                            .height(12.dp)
                            .background(
                                // 相机机身色恒定（拟物件不随主题翻色）
                                color = Color(0xFF1E1F17),
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 进度读数 + 阶段
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                DevelopSpec.phaseAt(state.progress).label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${(state.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 药水条
        Slider(
            value = state.progress,
            onValueChange = { f ->
                if (!scrubbing) {
                    scrubbing = true
                    vm.pause()
                }
                vm.seek(f)
            },
            onValueChangeFinished = {
                scrubbing = false
                vm.play()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.hairline,
            ),
        )

        Spacer(Modifier.height(2.dp))
        Text(
            when {
                state.progress < DevelopSpec.LATENT_END -> "先让这一刻安静一会儿"
                state.progress < DevelopSpec.EMERGING_END -> "让回忆慢慢浮出来"
                state.progress < 1f -> "光影正在慢慢定住"
                else -> "这一刻，已经好好留下"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (state.shakeHint && state.shakeEnabled && !reduceMotion) {
                "轻轻晃动，亲手唤醒这张相纸"
            } else {
                "拖动药水条，可正放或倒放重看"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))
    }
}
