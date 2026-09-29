package com.leo.darkroom.ui.result

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.PhotoCardPainter
import com.leo.darkroom.card.PhotoLook
import com.leo.darkroom.card.ShareFormat
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.editorialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** W3: a share-first print preview with understated controls. */
@Composable
fun ResultScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    val photo = state.photo
    // it-009：成品是主视觉——宽度优先给足（竖幅吃 94% 可用宽），高度以 46% 屏高限幅
    val configuration = LocalConfiguration.current
    val availableWidth = (configuration.screenWidthDp.dp - 40.dp)
    var previewWidth = minOf(availableWidth * 0.94f, 384.dp)
    var previewHeight = previewWidth * (state.exportFormat.height.toFloat() / state.exportFormat.width)
    val maxPreviewHeight = configuration.screenHeightDp.dp * 0.46f
    if (previewHeight > maxPreviewHeight) {
        previewHeight = maxPreviewHeight
        previewWidth = previewHeight * (state.exportFormat.width.toFloat() / state.exportFormat.height)
    }
    val density = LocalDensity.current
    val previewWidthPx = with(density) { previewWidth.roundToPx() }.coerceAtLeast(180)
    val previewHeightPx = with(density) { previewHeight.roundToPx() }.coerceAtLeast(180)
    val artwork = produceState<ImageBitmap?>(
        null, photo, state.spec, state.exportFormat, state.photoLook, state.mode,
    ) {
        if (photo != null) {
            val bitmap = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(previewWidthPx, previewHeightPx, Bitmap.Config.ARGB_8888).also { preview ->
                    PhotoCardPainter.paintShareFrame(
                        canvas = Canvas(preview),
                        widthPx = previewWidthPx.toFloat(),
                        heightPx = previewHeightPx.toFloat(),
                        photo = photo,
                        spec = state.spec,
                        visual = com.leo.darkroom.develop.DevelopSpec.visualAt(state.mode, 1f),
                        mode = state.mode,
                        grain = vm.grain,
                        format = state.exportFormat,
                        look = state.photoLook,
                    )
                }
            }
            value = bitmap.asImageBitmap()
        }
    }
    val lookThumbnails = produceState<List<ImageBitmap>>(emptyList(), photo) {
        if (photo != null) {
            value = withContext(Dispatchers.Default) {
                PhotoLook.entries.map { look ->
                    PhotoCardPainter.renderLookThumbnail(photo, vm.grain, look, 144, 92).asImageBitmap()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::backToPick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("成片", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                Text("一张可以带走的回忆", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "PRINTED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                color = colors.inkFaint,
            )
        }

        Spacer(Modifier.height(10.dp))
        // it-008 M2.6 成片亮相：rise 16dp + fade + pop，每次新会话到达 W3 播一次；
        // 光泽扫归 W2 定影（避免 750ms 内连扫两遍），此处只做亮相。
        val reduceMotion = EditorialMotion.reduceMotion()
        val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (entrance.value < 1f) entrance.animateTo(1f, EditorialMotion.pop())
        }
        // it-009：点按成品进全屏大图查看
        var viewerOpen by remember { mutableStateOf(false) }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(previewHeight),
            contentAlignment = Alignment.Center,
        ) {
            val maxW = maxWidth
            Box(
                Modifier.graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * 16.dp.toPx()
                },
            ) {
                val imageModifier = Modifier
                    .width(previewWidth.coerceAtMost(maxW))
                    .height(previewHeight)
                    .clip(RoundedCornerShape(4.dp))
                if (artwork.value != null) {
                    Box(
                        Modifier
                            .width(previewWidth.coerceAtMost(maxW))
                            .height(previewHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { viewerOpen = true },
                    ) {
                        Image(
                            bitmap = artwork.value!!,
                            contentDescription = "${state.exportFormat.label}成片预览",
                            contentScale = ContentScale.Fit,
                            modifier = imageModifier,
                        )
                        // 大图入口角标（视觉 16dp，热区随卡面整块可点）
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp)
                                .background(colors.surface.copy(alpha = 0.9f), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.OpenInFull,
                                contentDescription = "查看大图",
                                tint = colors.inkFaint,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .width(previewWidth.coerceAtMost(maxW))
                            .height(previewHeight)
                            .background(colors.surface, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在整理相纸…", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("成片预览", style = MaterialTheme.typography.labelSmall, color = colors.inkFaint)
            Spacer(Modifier.weight(1f))
            Text("点按可查看大图", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("照片风格", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(state.photoLook.note, style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PhotoLook.entries.forEachIndexed { index, look ->
                val selected = state.photoLook == look
                Column(
                    modifier = Modifier
                        .width(78.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(
                            BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.accent else colors.hairline),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            enabled = !state.exporting,
                            role = Role.RadioButton,
                            onClick = { vm.setPhotoLook(look) },
                        )
                        .semantics { this.selected = selected }
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val thumbnail = lookThumbnails.value.getOrNull(index)
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = "${look.label}效果预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.hairline),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                            look.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.accent else colors.ink,
                            maxLines = 1,
                        )
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "已选择",
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                CardEditLine(
                    label = "标题",
                    value = state.spec.title,
                    placeholder = "给这一刻起个名字",
                    onValueChange = vm::setTitle,
                )
                HorizontalDivider(color = colors.hairline)
                CardEditLine(
                    label = "日期章",
                    value = state.spec.dateText,
                    placeholder = "1988 07 21",
                    tabularNumbers = true,
                    onValueChange = vm::setDate,
                )
                HorizontalDivider(color = colors.hairline)
                Row(
                    Modifier
                    .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("卡脚水印", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                        Text("显影 DARKROOM", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
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

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("分享画幅", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(state.exportFormat.ratioLabel, style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShareFormat.entries.forEach { format ->
                val selected = state.exportFormat == format
                Column(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.ink else colors.surface)
                        .clickable { vm.setExportFormat(format) }
                        .padding(horizontal = 4.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        format.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.paper else colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        format.ratioLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) colors.paper.copy(alpha = 0.72f) else colors.inkFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (state.exporting) {
            LinearProgressIndicator(
                progress = { state.exportProgress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.hairline,
            )
            Spacer(Modifier.height(4.dp))
            Text("正在冲洗 ${(state.exportProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.exportImage(share = false) },
                enabled = !state.exporting,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.paper),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("存图片")
            }
            Button(
                onClick = vm::exportVideo,
                enabled = !state.exporting,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.paper),
            ) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("存视频")
            }
        }

        Spacer(Modifier.height(8.dp))
        val hasImage = state.savedImageUri != null
        val hasVideo = state.savedVideoUri != null
        if (hasImage || hasVideo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasImage) {
                    OutlinedButton(onClick = vm::shareSavedImage, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("分享图片")
                    }
                }
                if (hasVideo) {
                    OutlinedButton(onClick = vm::shareSavedVideo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("分享视频")
                    }
                }
                TextButton(onClick = vm::backToPick, modifier = Modifier.weight(1f)) { Text("再洗一张") }
            }
        } else {
            TextButton(onClick = vm::backToPick, modifier = Modifier.fillMaxWidth()) { Text("再洗一张") }
        }
        Spacer(Modifier.height(20.dp))

        // it-009 全屏大图：暗底 Fit 全屏，点按任意处关闭（返回键同）
        if (viewerOpen) {
            artwork.value?.let { art ->
                Dialog(
                    onDismissRequest = { viewerOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xF0151515))
                            .clickable { viewerOpen = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = art,
                            contentDescription = "${state.exportFormat.label}大图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 28.dp),
                        )
                        Text(
                            "点按任意处关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0x99FBFBFA),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardEditLine(
    label: String,
    value: String,
    placeholder: String,
    tabularNumbers: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = editorialColors()
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.inkFaint, modifier = Modifier.width(62.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colors.ink,
                fontFeatureSettings = if (tabularNumbers) "tnum" else null,
            ),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.inkFaint.copy(alpha = 0.72f))
                    }
                    innerTextField()
                }
            },
        )
    }
}
