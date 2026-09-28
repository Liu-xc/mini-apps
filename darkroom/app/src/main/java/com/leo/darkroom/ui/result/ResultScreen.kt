package com.leo.darkroom.ui.result

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.CardPalette
import com.leo.darkroom.card.PhotoCardPainter
import com.leo.darkroom.card.PhotoLook
import com.leo.darkroom.card.ShareFormat
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.editorialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** W3: a share-first print preview with understated controls. */
@Composable
fun ResultScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    val photo = state.photo
    val previewHeight = 250.dp
    val previewWidth = previewHeight * (state.exportFormat.width.toFloat() / state.exportFormat.height)
    val density = LocalDensity.current
    val previewWidthPx = with(density) { previewWidth.roundToPx() }.coerceAtLeast(180)
    val previewHeightPx = with(density) { previewHeight.roundToPx() }.coerceAtLeast(180)
    val artwork = produceState<ImageBitmap?>(null, photo, state.spec, state.exportFormat, state.photoLook) {
        if (photo != null) {
            val bitmap = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(previewWidthPx, previewHeightPx, Bitmap.Config.ARGB_8888).also { preview ->
                    PhotoCardPainter.paintShareFrame(
                        canvas = Canvas(preview),
                        widthPx = previewWidthPx.toFloat(),
                        heightPx = previewHeightPx.toFloat(),
                        photo = photo,
                        spec = state.spec,
                        visual = com.leo.darkroom.develop.DevelopSpec.visualAt(1f),
                        palette = CardPalette.Default,
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
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork.value != null) {
                Image(
                    bitmap = artwork.value!!,
                    contentDescription = "${state.exportFormat.label}成片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(previewWidth.coerceAtMost(maxWidth))
                        .height(previewHeight)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    Modifier
                        .width(previewWidth.coerceAtMost(maxWidth))
                        .height(previewHeight)
                        .background(colors.surface, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("正在整理相纸…", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("成片预览", style = MaterialTheme.typography.labelSmall, color = colors.inkFaint)
            Spacer(Modifier.weight(1f))
            Text("风格与编辑同步到图片和视频", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
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
