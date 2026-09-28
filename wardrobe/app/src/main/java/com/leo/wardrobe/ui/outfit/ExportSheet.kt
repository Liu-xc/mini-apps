@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.outfit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import com.leo.wardrobe.ui.components.StaggeredEntrance
import com.leo.wardrobe.ui.components.pressScale
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.EditorialMotion
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * W6 导出面板：预览是内容主角，画面设定常驻可扫读，补充信息与 Prompt 分段编辑；
 * 底部复制/保存/分享动作始终固定可达。维度选择记忆经 ready 标志修竞态。
 */
@Composable
fun ExportSheet(
    vm: AppViewModel,
    items: List<Item>,
    existingOutfit: Outfit?,
    refPhotoFile: String? = null,
    // it-056：调用方带来的五维预选（如顾问推荐的场景），打开时以持久化记忆为底、覆盖同 key；
    // 空 map 时四处既有调用行为与此前完全一致
    presetSelections: Map<String, String> = emptyMap(),
    // it-057：对话入口传 true——初始值只反映本次推荐，不叠加历史记忆，
    // 避免上一套的「办公室」残留在新推荐的表单里冒充本次场景
    replaceSavedSelections: Boolean = false,
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
    // it-051：永远展示五维当前值；只展开正在编辑的一行，避免把“场景”藏在泛化折叠入口中。
    var activeDimensionKey by remember { mutableStateOf<String?>(null) }
    var resolvedInitialDimension by remember { mutableStateOf(false) }

    LaunchedEffect(savedNote) { if (personNote.isBlank() && savedNote.isNotBlank()) personNote = savedNote }
    LaunchedEffect(savedCustomPrompt) { if (customPrompt.isBlank() && savedCustomPrompt.isNotBlank()) customPrompt = savedCustomPrompt }
    // it-012：恢复上次维度选择——等 DataStore 首发射完成（ready）或值非空才 init，
    // 避免首帧空 map 把记忆标记为已初始化而永久丢弃（R2 实测竞态 bug）
    // it-056：init 时叠加调用方预选（覆盖同 key、保留其余记忆），顾问场景等语境随推荐带入
    // it-057：replaceSavedSelections=true（对话入口）时不叠加记忆——表单只反映本次推荐
    LaunchedEffect(savedSelections, savedSelectionsReady, presetSelections) {
        if (!selectionsInit && (savedSelectionsReady || savedSelections.isNotEmpty())) {
            val base = if (replaceSavedSelections) emptyMap() else savedSelections
            val merged = base + presetSelections
            if (merged.isNotEmpty()) selections = merged
            selectionsInit = true
        }
    }
    // 首次打开且尚未选择场景时，直接呈现“场景”候选；已有选择则保持紧凑、可扫读的常态。
    LaunchedEffect(selectionsInit, selections) {
        if (selectionsInit && !resolvedInitialDimension) {
            activeDimensionKey = PromptPresets.SCENE.key.takeIf { selections[it] == null }
            resolvedInitialDimension = true
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

                    Text(
                        "画面设定",
                        style = MaterialTheme.typography.titleMedium,
                        color = editorialColors().ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                        border = BorderStroke(1.dp, editorialColors().hairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            PromptPresets.dimensions.forEachIndexed { index, dim ->
                                // it-058 C7（05 动效#10 兑现）：sheet 弹出后设定行轻错峰淡入
                                StaggeredEntrance(index = index, animate = true) {
                                    DimensionSettingRow(
                                        dim = dim,
                                        selectedValue = selections[dim.key],
                                        expanded = activeDimensionKey == dim.key,
                                        isPrimary = dim.key == PromptPresets.SCENE.key,
                                        allowCustom = dim.key == PromptPresets.SCENE.key, // it-057：场景可自定义
                                        onToggle = {
                                            activeDimensionKey = if (activeDimensionKey == dim.key) null else dim.key
                                        },
                                        onSelect = { opt ->
                                            selections = if (selections[dim.key] == opt) {
                                                selections - dim.key
                                            } else {
                                                selections + (dim.key to opt)
                                            }
                                        },
                                    )
                                }
                                if (index != PromptPresets.dimensions.lastIndex) {
                                    HorizontalDivider(
                                        color = editorialColors().hairline,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "可只设场景；其余参数按需要补充。",
                        style = MaterialTheme.typography.bodySmall,
                        color = editorialColors().inkFaint,
                        modifier = Modifier.padding(start = 2.dp),
                    )

                    Text(
                        "补充信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = editorialColors().ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FieldLabel(title = "人物描述", meta = "会记住上次")
                    OutlinedTextField(
                        value = personNote,
                        onValueChange = { personNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：短发，偏瘦，自然站姿") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(16.dp),
                        colors = editorialTextFieldColors(),
                    )

                    // it-013：自定义要求（可选，自由撰写，追加到文案末尾，记住上次）
                    FieldLabel(title = "自定义要求", meta = "可选 · 会记住上次")
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：不要改变鞋子的颜色") },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(16.dp),
                        colors = editorialTextFieldColors(),
                    )

                    Text(
                        "生成文案",
                        style = MaterialTheme.typography.titleMedium,
                        color = editorialColors().ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FieldLabel(title = "给生图 Agent 的提示词", meta = "实时生成 · 可编辑")
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { promptEdit = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = editorialColors().ink,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = editorialTextFieldColors(),
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
                                // it-058 C3：主 CTA 按压反馈
                                modifier = Modifier.weight(1.25f).pressScale(0.96f),
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

/** W6 it-051：常驻值的画面设定行；仅展开当前编辑项，避免嵌套弹层。 */
/**
 * it-057：场景行支持自由值——预设 chips 之外带「自定义」入口；
 * 当前值为预设外文本（顾问推荐的自由场景短语）时以选中态 chip 常驻，点击即清除。
 */
@Composable
private fun DimensionSettingRow(
    dim: com.leo.wardrobe.domain.usecase.PromptDimension,
    selectedValue: String?,
    expanded: Boolean,
    isPrimary: Boolean,
    allowCustom: Boolean = false,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Surface(
        onClick = onToggle,
        color = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.055f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    dim.label,
                    style = if (isPrimary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    color = editorialColors().ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    selectedValue ?: "未设置",
                    style = if (isPrimary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                    color = if (selectedValue != null) editorialColors().accentContent else editorialColors().inkFaint,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    contentDescription = "编辑${dim.label}",
                    tint = editorialColors().inkFaint,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = EditorialMotion.smooth()) + fadeIn(animationSpec = tween(100)),
                exit = shrinkVertically(animationSpec = EditorialMotion.smooth()) + fadeOut(animationSpec = tween(90)),
            ) {
                val freeValue = selectedValue?.takeIf { it !in dim.options }
                var customEditing by remember { mutableStateOf(false) }
                var customInput by remember { mutableStateOf("") }
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = if (customEditing) 8.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dim.options.forEach { opt ->
                            FilterChip(
                                selected = selectedValue == opt,
                                onClick = { onSelect(opt) },
                                modifier = Modifier.heightIn(min = 44.dp),
                                label = { Text(opt) },
                            )
                        }
                        if (allowCustom) {
                            // it-057：顾问带入的自由场景值常驻为选中态 chip，点击清除
                            if (freeValue != null) {
                                FilterChip(
                                    selected = true,
                                    onClick = { onSelect(freeValue) }, // 与当前值相同 → 走移除分支
                                    modifier = Modifier.heightIn(min = 44.dp),
                                    label = { Text(freeValue) },
                                )
                            }
                            FilterChip(
                                selected = customEditing,
                                onClick = {
                                    customInput = ""
                                    customEditing = !customEditing
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                                label = { Text("自定义") },
                            )
                        }
                    }
                    if (customEditing) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                placeholder = { Text("输入${dim.label}…") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = editorialTextFieldColors(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                enabled = customInput.isNotBlank(),
                                onClick = {
                                    onSelect(customInput.trim())
                                    customEditing = false
                                },
                            ) { Text("确定") }
                        }
                    }
                }
            }
        }
    }
}

/** 表单标签常驻在输入框外，空字段不再依赖低对比 placeholder 说明用途。 */
@Composable
private fun FieldLabel(title: String, meta: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = editorialColors().ink,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = editorialColors().inkFaint,
        )
    }
}

@Composable
private fun editorialTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    cursorColor = editorialColors().accent,
    focusedBorderColor = editorialColors().accent,
    unfocusedBorderColor = editorialColors().hairline,
)
