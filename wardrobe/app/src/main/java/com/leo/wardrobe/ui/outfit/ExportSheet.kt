@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.ConfettiBurst
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * W6 导出面板（US-07/08/09）：合成图预览 + 可编辑文案 + 复制/分享 + 收藏 + 录入成品图。
 */
@Composable
fun ExportSheet(
    vm: AppViewModel,
    items: List<Item>,
    existingOutfit: Outfit?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast by vm.toast.collectAsState()

    // 合成图 + 文案（打开即生成；标签取各单品标签并集前 3 个）
    var composedFile by remember { mutableStateOf<File?>(null) }
    var prompt by remember { mutableStateOf("") }
    var styleTags by remember(items) {
        mutableStateOf(items.flatMap { it.tags }.distinct().take(3))
    }
    var copied by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var collected by remember { mutableStateOf(false) }

    LaunchedEffect(items, styleTags) {
        composedFile = withContext(Dispatchers.IO) {
            vm.imageComposer.composeToExportFile(items)
        }
        prompt = withContext(Dispatchers.Default) {
            vm.promptBuilder(items, styleTags)
        }
    }

    val pickEffectImage = rememberPhotoPicker { uri ->
        if (uri != null) vm.importEffectImage(existingOutfit, items.map { it.id }, uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "导出生图素材",
                    style = MaterialTheme.typography.titleLarge,
                    color = editorialColors().ink,
                )

                // 合成图预览
                val painter = rememberAsyncImagePainter(composedFile)
                if (composedFile != null) {
                    Image(
                        painter = painter,
                        contentDescription = "穿搭合成图",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Text(
                        "正在拼合 ${items.size} 件单品…",
                        style = MaterialTheme.typography.bodySmall,
                        color = editorialColors().inkFaint,
                    )
                }

                // 可编辑文案
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    label = { Text("文案（自动生成，可编辑）") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )

                // 风格标签（进文案）
                TagInput(tags = styleTags, onChange = { styleTags = it })

                // 主操作：复制图片+文本 / 分享
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val file = composedFile
                            scope.launch {
                                val ok = file != null &&
                                    vm.share.copyImageAndText(file, prompt)
                                if (!ok) vm.share.copyText(prompt)
                                copied = true
                                confettiTrigger++
                                vm.toast("已复制，去生图 Agent 里粘贴吧")
                            }
                        },
                        enabled = composedFile != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                        )
                        Text(
                            if (copied) "已复制 ✓" else "复制图片+文本",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(onClick = {
                        composedFile?.let { vm.share.shareImage(it) }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                        Text("分享")
                    }
                }
                OutlinedButton(onClick = { vm.share.copyText(prompt) }, modifier = Modifier.fillMaxWidth()) {
                    Text("只复制文本")
                }

                // 收藏 + 录入成品图
                androidx.compose.material3.HorizontalDivider(color = editorialColors().hairline)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (existingOutfit != null) {
                                vm.updateOutfitTags(existingOutfit.id, styleTags)
                            } else {
                                vm.createOutfit(items.map { it.id }, styleTags)
                            }
                            collected = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (collected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = editorialColors().accent,
                        )
                        Text(if (collected) "已收藏" else "收藏这套")
                    }
                    Button(onClick = { pickEffectImage() }, modifier = Modifier.weight(1f)) {
                        Text("＋ 录入成品图")
                    }
                }
                if (toast != null) {
                    Text(
                        toast!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = editorialColors().accent,
                    )
                }
            }
            // 复制成功彩屑（specs/05 动效#4）
            ConfettiBurst(
                trigger = confettiTrigger,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}
