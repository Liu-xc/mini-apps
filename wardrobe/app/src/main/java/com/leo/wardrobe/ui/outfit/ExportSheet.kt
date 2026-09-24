@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.outfit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.domain.usecase.PromptPresets
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.ConfettiBurst
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * W6 导出面板（it-002 改版；it-011 O8 高频优先；it-012 R2 三修）：
 * 结构 = 滚动区（标题/自适应预览/场景常驻/四维折叠/人物/Prompt/收藏）+ **底部固定动作栏**
 * （复制长图/分享/只复制文本永远可见，展开维度不再挤走）；维度选择记忆经 ready 标志修竞态。
 */
@Composable
fun ExportSheet(
    vm: AppViewModel,
    items: List<Item>,
    existingOutfit: Outfit?,
    refPhotoFile: String? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedNote by vm.personNote.collectAsState()
    val savedSelections by vm.exportSelections.collectAsState()
    val savedSelectionsReady by vm.exportSelectionsReady.collectAsState()
    val savedCustomPrompt by vm.customPrompt.collectAsState()

    var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectionsInit by remember { mutableStateOf(false) }
    var personNote by remember { mutableStateOf("") }
    var customPrompt by remember { mutableStateOf("") }
    var promptEdit by remember { mutableStateOf<String?>(null) } // 用户手改覆盖，维度变化时重置
    // it-017：参考照开关——默认使用，不持久化（关闭仅本次有效，下次打开面板仍默认开）
    var attachRef by remember { mutableStateOf(true) }
    val composedRefPhoto = refPhotoFile?.takeIf { attachRef }
    var composedFile by remember { mutableStateOf<File?>(null) }
    var composing by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    var savedToGallery by remember { mutableStateOf(false) }
    var collected by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var composeJob by remember { mutableStateOf<Job?>(null) }
    var advancedOpen by remember { mutableStateOf(false) }

    LaunchedEffect(savedNote) { if (personNote.isBlank() && savedNote.isNotBlank()) personNote = savedNote }
    LaunchedEffect(savedCustomPrompt) { if (customPrompt.isBlank() && savedCustomPrompt.isNotBlank()) customPrompt = savedCustomPrompt }
    // it-012：恢复上次维度选择——等 DataStore 首发射完成（ready）或值非空才 init，
    // 避免首帧空 map 把记忆标记为已初始化而永久丢弃（R2 实测竞态 bug）
    LaunchedEffect(savedSelections, savedSelectionsReady) {
        if (!selectionsInit && (savedSelectionsReady || savedSelections.isNotEmpty())) {
            if (savedSelections.isNotEmpty()) selections = savedSelections
            selectionsInit = true
        }
    }

    /** it-013：自定义要求追加在生成文案末尾；it-017：附参考照时先追加形象还原要求（it-017 修订：prompt 已在长图顶部） */
    fun promptWithCustom(includeItems: Boolean): String {
        var p = vm.promptBuilder(items, selections, personNote, includeItems = includeItems)
        if (composedRefPhoto != null) {
            p += "\n图中已附本人形象参考照（标注「本人形象参考」处），生成时请保持其五官、发型与身形还原，仅将服装替换为本套穿搭。"
        }
        if (customPrompt.isNotBlank()) p += "\n另外要求：${customPrompt.trim()}"
        return p
    }

    /** 长图通道文案：不含单品清单（照片标签已承载） */
    val imagePrompt = promptEdit ?: promptWithCustom(includeItems = false)

    fun regenerate() {
        composeJob?.cancel()
        composing = true
        composeJob = scope.launch {
            delay(200) // 去抖：快速点选维度时避免重复拼长图
            composedFile = vm.imageComposer.composeToExportFile(items, imagePrompt, composedRefPhoto)
            composing = false
        }
    }

    LaunchedEffect(items) { regenerate() }
    LaunchedEffect(selections, personNote, customPrompt, attachRef) {
        promptEdit = null
        vm.setExportSelections(selections)
        regenerate()
    }
    // 人物描述持久化（去抖）
    LaunchedEffect(personNote) {
        if (personNote != savedNote) {
            delay(600)
            vm.setPersonNote(personNote)
        }
    }
    // 自定义要求持久化（去抖，it-013）
    LaunchedEffect(customPrompt) {
        if (customPrompt != savedCustomPrompt) {
            delay(600)
            vm.setCustomPrompt(customPrompt)
        }
    }

    val pickEffectImage = rememberPhotoPicker { uri ->
        if (uri != null) vm.importEffectImage(existingOutfit, items.map { it.id }, uri)
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenH = LocalConfiguration.current.screenHeightDp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenH * 0.92f).dp),
            ) {
                // ---- 滚动区 ----
                // it-036 C8：内容列底距 40dp = 视口底缘 24dp 渐隐带 + 16dp 动作栏安全余量
                // （内容列经 weight 钉在动作栏之上，安全间距落在 contentPadding），
                // 保证「文案（实时生成，可编辑）」整块可滚出到完整可见；
                // 未滚到底时视口底缘叠 24dp 渐隐（透明→弹层背景色），提示下方还有内容
                val scroll = rememberScrollState()
                val sheetBg = MaterialTheme.colorScheme.surfaceContainer
                Column(
                    Modifier
                        .weight(1f)
                        .drawWithContent {
                            drawContent()
                            if (scroll.value < scroll.maxValue) {
                                val fadeH = 24.dp.toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color.Transparent, sheetBg),
                                    ),
                                    topLeft = Offset(0f, size.height - fadeH),
                                    size = Size(size.width, fadeH),
                                )
                            }
                        }
                        .verticalScroll(scroll)
                        .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("导出生图素材", style = MaterialTheme.typography.titleLarge, color = editorialColors().ink)

                    // 长图预览（it-012：高自适应屏高 42%；ContentScale.Fit 整图全貌一屏可见
                    // ——修历史空白 bug：内层 verticalScroll 的无限高度约束使 Coil 请求尺寸失效）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = (screenH * 0.42f).dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (composedFile != null) {
                            AsyncImage(
                                model = composedFile,
                                contentDescription = "穿搭长图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                if (composing) "正在按穿搭顺序拼长图…" else "暂无可拼合的单品照片",
                                style = MaterialTheme.typography.bodySmall,
                                color = editorialColors().inkFaint,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }

                    // it-017：附形象参考照开关——仅该角色已设置照片时出现；默认开，关闭只影响本次
                    if (refPhotoFile != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Face,
                                    contentDescription = null,
                                    tint = editorialColors().accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "附形象参考照",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = editorialColors().ink,
                                    )
                                    Text(
                                        "生图更像本人 · 关闭仅本次有效",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = editorialColors().inkFaint,
                                    )
                                }
                                Switch(checked = attachRef, onCheckedChange = { attachRef = it })
                            }
                        }
                    }

                    // it-013：五维全部收进一个可选折叠区（场景不再常驻）——自由写 prompt 是主路径，维度只是可选微调
                    val selCount = PromptPresets.dimensions.count { selections[it.key] != null }
                    Surface(
                        onClick = { advancedOpen = !advancedOpen },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "风格与场景维度（全部可选）",
                                style = MaterialTheme.typography.labelLarge,
                                color = editorialColors().ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (selCount > 0) {
                                Surface(shape = RoundedCornerShape(50), color = editorialColors().accent) {
                                    Text(
                                        "$selCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Icon(
                                if (advancedOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = editorialColors().inkFaint,
                            )
                        }
                    }
                    AnimatedVisibility(visible = advancedOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PromptPresets.dimensions.forEach { dim ->
                                DimensionChips(
                                    dim = dim,
                                    selected = selections,
                                    onSelect = { opt ->
                                        selections = if (selections[dim.key] == opt) {
                                            selections - dim.key
                                        } else {
                                            selections + (dim.key to opt)
                                        }
                                    },
                                )
                            }
                            Text(
                                "一个都不选也可以——直接在下面写你的要求",
                                style = MaterialTheme.typography.labelSmall,
                                color = editorialColors().inkFaint,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = personNote,
                        onValueChange = { personNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        // it-036 C8②：两输入框统一占位符式——去 floating label（线框 W6②）
                        placeholder = { Text("人物描述（记住上次）") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    // it-013：自定义要求（可选，自由撰写，追加到文案末尾，记住上次）
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        // it-036 C8②：原 floating label 缺口式 → 与人物描述同占位符式
                        placeholder = { Text("自定义要求（可选，记住上次）") },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    // Prompt 文案：深底等宽高对比单层容器（it-011 O8——导出的灵魂）
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { promptEdit = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text("文案（实时生成，可编辑）") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = editorialColors().ink,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            cursorColor = editorialColors().accent,
                            focusedBorderColor = editorialColors().accent,
                            unfocusedBorderColor = editorialColors().hairline,
                        ),
                    )

                    HorizontalDivider(color = editorialColors().hairline)
                    // it-019：含愿望单品的组合不能收藏为正式 Outfit（心愿组合走「存为心愿」）
                    val hasWishItem = items.any { it.isWishSlot }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                // it-004：去重保存（已存在则复用并更新标签，不重复创建）
                                vm.saveOutfitDedup(items.map { it.id }, selections.values.toList(), existing = existingOutfit)
                                collected = true
                            },
                            enabled = !hasWishItem,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (collected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = null,
                                tint = editorialColors().accent,
                            )
                            Text(if (hasWishItem) "心愿组合 · 仅预览" else "收藏这套")  // it-030：去 emoji
                        }
                        Button(onClick = { pickEffectImage() }, modifier = Modifier.weight(1f)) {
                            Text("＋ 录入成品图")
                        }
                    }
                    // it-036 C8：原尾随 Spacer(6dp) 废止——动作栏安全余量统一走内容列 40dp 底距
                }

                // ---- it-012 底部固定动作栏：三个复制动作钉住，展开维度/滚动都推不走 ----
                Surface(shadowElevation = 6.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // it-014：复制｜存相册｜分享 三动作并列
                        val haptics = rememberHaptics()  // it-027：确认动作触感（DESIGN.md §4）
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val file = composedFile
                                    scope.launch {
                                        val ok = file != null && vm.share.copyImage(file)
                                        if (ok) {
                                            copied = true
                                            confettiTrigger++
                                            haptics.confirm()
                                            vm.toast("长图已复制，去生图 Agent 里粘贴")
                                        } else {
                                            vm.toast("复制失败，试试「分享」")
                                        }
                                    }
                                },
                                enabled = composedFile != null,
                                modifier = Modifier.weight(1.25f),
                            ) {
                                Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, contentDescription = null)
                                Text(if (copied) "已复制 ✓" else "复制长图", maxLines = 1)
                            }
                            // it-014：snackbar 会被 sheet 遮挡，成功反馈直接落在按钮上
                            OutlinedButton(
                                onClick = {
                                    val file = composedFile ?: return@OutlinedButton
                                    scope.launch {
                                        val ok = vm.share.saveToGallery(file)
                                        if (ok) {
                                            savedToGallery = true
                                            haptics.confirm()
                                            vm.toast("已保存到相册 Pictures/Wardrobe")
                                        } else {
                                            vm.toast("当前系统不支持直存，可用「分享」保存")
                                        }
                                    }
                                },
                                enabled = composedFile != null,
                                modifier = Modifier.weight(0.95f),
                            ) {
                                Icon(
                                    if (savedToGallery) Icons.Rounded.Check else Icons.Rounded.Download,
                                    contentDescription = "保存长图到相册",
                                )
                                Text(if (savedToGallery) "已存相册 ✓" else "存相册", maxLines = 1)
                            }
                            LaunchedEffect(savedToGallery) {
                                if (savedToGallery) {
                                    delay(2000)
                                    savedToGallery = false
                                }
                            }
                            LaunchedEffect(copied) {  // it-028：与存相册同拍，2s 复位避免常驻「已复制 ✓」
                                if (copied) {
                                    delay(2000)
                                    copied = false
                                }
                            }
                            OutlinedButton(
                                onClick = { composedFile?.let { vm.share.shareImage(it) } },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(0.8f),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("分享", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                // 文本通道附带单品清单；复制后明确反馈
                                haptics.tick()
                                vm.share.copyText(promptWithCustom(includeItems = true))
                                vm.toast("文本已复制")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("只复制文本（含单品清单）") }
                    }
                }
            }
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

/** 单维度选择器（单选、可再点取消） */
@Composable
private fun DimensionChips(
    dim: com.leo.wardrobe.domain.usecase.PromptDimension,
    selected: Map<String, String>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            dim.label,
            style = MaterialTheme.typography.labelMedium,
            color = editorialColors().inkFaint,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dim.options.forEach { opt ->
                FilterChip(
                    selected = selected[dim.key] == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) },
                )
            }
        }
    }
}
