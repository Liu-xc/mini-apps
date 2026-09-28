package com.leo.darkroom.ui.develop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.develop.EjectStyle
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

    // 出纸动画：卡片自槽口/片盒升起，随后启动显影。
    // 由 state.ejecting 驱动而不是 LaunchedEffect(Unit)——Activity 被系统重建时
    // 显影可能已经过半，重放出纸会让 92% 的会话又「吐」一次纸（it-007 实机发现）
    val eject = remember { Animatable(if (state.ejecting) 1f else 0f) }
    val settle = remember { Animatable(1f) } // 定影落定轻弹 scale
    LaunchedEffect(state.ejecting) {
        if (state.ejecting) {
            if (reduceMotion) {
                eject.snapTo(0f)
                vm.onEjectDone()
                vm.skipDevelop()
            } else {
                // it-007 M2：分段顿挫的出纸（快推/微顿/缓出），替代原 pop 弹簧匀速滑出
                eject.animateTo(0f, tween(EditorialMotion.EJECT_MS, easing = EditorialMotion.ejectEase))
                vm.onEjectDone()
            }
        } else {
            eject.snapTo(0f)
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
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                color = colors.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.speed.label} · ${state.mode.stageLabel(DevelopSpec.phaseAt(state.progress))}",
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
                state.mode,
            )
            val cardWidthPx = layout.width
            val cardHeightPx = layout.height

            Box(contentAlignment = Alignment.Center) {
                // 卡片（出纸位移 + 定影落定缩放 + 脱纸阴影）
                Box(
                    Modifier
                        .graphicsLayer {
                            when (state.mode.eject) {
                                // 相纸自槽口升起
                                EjectStyle.SLOT_RISE -> {
                                    translationY = eject.value * cardHeightPx
                                    rotationZ = sin((1f - eject.value) * PI.toFloat()) * 1.15f
                                }
                                // 胶片自片盒口横向卷出
                                EjectStyle.FILM_WIND -> {
                                    translationX = eject.value * cardWidthPx
                                    rotationZ = sin((1f - eject.value) * PI.toFloat()) * 0.8f
                                }
                                // 数码屏原地开机点亮，不位移
                                EjectStyle.SCREEN_WAKE -> {
                                    val wake = 1f - eject.value
                                    scaleX = 0.96f + 0.04f * wake
                                    scaleY = 0.96f + 0.04f * wake
                                }
                            }
                            scaleX = scaleX * settle.value
                            scaleY = scaleY * settle.value
                        }
                        .shadow(8.dp, RectangleShape),
                ) {
                    DevelopCard(
                        photo = state.photo,
                        spec = state.spec,
                        mode = state.mode,
                        progress = state.progress,
                        cardWidthPx = cardWidthPx,
                        grain = vm.grain,
                    )
                }

                // —— 出纸素材：按模式给机器形态 ——
                when (state.mode.eject) {
                    // 相机槽口：固定在卡片最终底缘之下，出纸时被机构下压再回位
                    EjectStyle.SLOT_RISE -> if (eject.value > 0.001f) {
                        val push = (1f - eject.value).coerceIn(0f, 1f)
                        val pressPx = with(density) { (5.dp * sin(PI.toFloat() * push)).roundToPx() }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .offset { IntOffset(0, 12.dp.roundToPx() + pressPx) }
                                .width(with(density) { (cardWidthPx * 0.92f).toDp() })
                                .height(12.dp)
                                .background(
                                    // Camera body remains a neutral graphite object in either theme.
                                    color = Color(0xFF171717),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                ),
                        )
                    }

                    // 片盒口：竖直机构条，胶片自其左侧卷出
                    EjectStyle.FILM_WIND -> if (eject.value > 0.001f) {
                        val push = (1f - eject.value).coerceIn(0f, 1f)
                        val pressPx = with(density) { (5.dp * sin(PI.toFloat() * push)).roundToPx() }
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .offset { IntOffset(pressPx, 0) }
                                .width(12.dp)
                                .height(with(density) { (cardHeightPx * 0.96f).toDp() })
                                .background(
                                    color = Color(0xFF171717),
                                    shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
                                ),
                        )
                    }

                    // 开机扫描线：随点亮进度自上而下扫过整块屏
                    EjectStyle.SCREEN_WAKE -> if (eject.value > 0.001f) {
                        val sweep = 1f - eject.value.coerceIn(0f, 1f)
                        Canvas(
                            Modifier
                                .align(Alignment.Center)
                                .size(
                                    with(density) { cardWidthPx.toDp() },
                                    with(density) { cardHeightPx.toDp() },
                                ),
                        ) {
                            val y = size.height * sweep
                            drawRect(color = Color(0x12FFFFFF), size = Size(size.width, y))
                            drawLine(
                                color = Color(0x59FFFFFF),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 进度读数（阶段名由刻度条上方一排承担，此处不再重复）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "${(state.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 药水刻度条（it-007 M1：自绘化学刻度，替换 Material Slider）
        ChemicalGauge(
            progress = state.progress,
            stageLabels = state.mode.stages,
            onDragStart = {
                if (!scrubbing) {
                    scrubbing = true
                    vm.pause()
                }
            },
            onSeek = vm::seek,
            onDragEnd = {
                if (scrubbing) {
                    scrubbing = false
                    vm.play()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(2.dp))
        Text(
            state.mode.copyAt(state.progress),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (state.shakeHint && state.shakeEnabled && !reduceMotion) {
                when (state.mode) {
                    DevelopMode.POLAROID -> "轻轻晃动，亲手唤醒这张相纸"
                    DevelopMode.DIGITAL -> "轻轻晃动，让画面稳下来"
                    DevelopMode.FILM -> "轻轻晃动，让药液走匀"
                }
            } else {
                "拖动刻度条，可正放或倒放重看"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))
    }
}
