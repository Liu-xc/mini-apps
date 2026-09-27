package com.leo.darkroom.ui.result

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.ui.develop.DevelopCard
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.editorialColors

/**
 * W3 成片页：结构化卡面（可编辑字段）+ 图/视频双导出 + 分享。
 */
@Composable
fun ResultScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    // 落定轻弹入场
    val settle = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, EditorialMotion.pop()) }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
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
            Text("成片", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(
                "PRINTED",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 卡片（定影完成态）——it-003 O5：卡高设上限，CTA/进度留在首屏
        BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val layout = com.leo.darkroom.card.CardLayout.solve(
                with(density) { maxWidth.toPx() },
                with(density) { 360.dp.toPx() },
            )
            DevelopCard(
                photo = state.photo,
                spec = state.spec,
                progress = 1f,
                cardWidthPx = layout.width,
                grain = vm.grain,
                modifier = Modifier.graphicsLayer {
                    scaleX = settle.value
                    scaleY = settle.value
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // —— 卡面编辑 ——
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = state.spec.title,
                    onValueChange = vm::setTitle,
                    label = { Text("手写标题（可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.spec.dateText,
                    onValueChange = vm::setDate,
                    label = { Text("日期章 · 如 1988 07 21") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = colors.hairline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("卡脚水印", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                        Text(
                            "显影 DARKROOM",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkFaint,
                        )
                    }
                    Switch(
                        checked = state.spec.showWatermark,
                        onCheckedChange = vm::setWatermark,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.accent,
                            checkedThumbColor = colors.surface,
                            uncheckedTrackColor = colors.hairline,
                            uncheckedThumbColor = colors.surface,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // —— 视频规格 ——
        Text(
            "视频规格",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.exportFormat == DarkroomViewModel.ExportFormat.SQUARE,
                onClick = { vm.setExportFormat(DarkroomViewModel.ExportFormat.SQUARE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("方形 1080×1080") }
            SegmentedButton(
                selected = state.exportFormat == DarkroomViewModel.ExportFormat.PORTRAIT,
                onClick = { vm.setExportFormat(DarkroomViewModel.ExportFormat.PORTRAIT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("竖版 1080×1350") }
        }

        Spacer(Modifier.height(8.dp))

        // —— 导出（it-003 O5：进度条贴 CTA 上方，导出反馈进首屏）——
        if (state.exporting) {
            LinearProgressIndicator(
                progress = { state.exportProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "冲洗中 ${(state.exportProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { vm.exportImage(share = false) },
                enabled = !state.exporting,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("存图片")
            }
            Button(
                onClick = vm::exportVideo,
                enabled = !state.exporting,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("存视频")
            }
        }

        Spacer(Modifier.height(10.dp))

        // 分享入口（已存过才有）+ 再洗一张：收为一行次级操作（it-003 O5）
        val hasImage = state.savedImageUri != null
        val hasVideo = state.savedVideoUri != null
        if (hasImage || hasVideo) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasImage) {
                    OutlinedButton(
                        onClick = vm::shareSavedImage,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享图片")
                    }
                }
                if (hasVideo) {
                    OutlinedButton(
                        onClick = vm::shareSavedVideo,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享视频")
                    }
                }
                OutlinedButton(
                    onClick = vm::backToPick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("再洗一张", fontWeight = FontWeight.Medium)
                }
            }
        } else {
            OutlinedButton(
                onClick = vm::backToPick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("再洗一张", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
