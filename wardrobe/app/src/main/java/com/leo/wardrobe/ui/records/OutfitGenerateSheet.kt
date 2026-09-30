@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CircularProgressIndicator
import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ParamType
import com.leo.libs.agent.image.GeneratedImage
import com.leo.wardrobe.data.gen.OutfitImageGenerator
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.usecase.PromptPresets
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


/**
 * it-077 修订（Leo 反馈×4）：生图连接未配置（无厂商/模型/Key）时隐藏 AI 入口；配置后自动出现。
 * it-077 修订八：连接解析上提到页面级（打开 sheet 前已完成）——sheet 内异步解析会造成
 * 开栏先加载条后内容弹跳，且曾因重构丢失赋值导致永远卡加载条（gen6 回归）。
 */
@Composable
fun rememberImageGenConnection(vm: AppViewModel): OutfitImageGenerator.Connection? =
    androidx.compose.runtime.produceState<OutfitImageGenerator.Connection?>(initialValue = null) {
        value = vm.imageGenerator.connection()
    }.value

/** 兼容旧调用（布尔就绪态） */
@Composable
fun rememberImageGenReady(vm: AppViewModel): Boolean = rememberImageGenConnection(vm) != null

/** 生成 sheet 四态（it-040 W5 四态语言） */
private sealed interface GeneratePhase {
    data object Setup : GeneratePhase
    data class Running(val progress: String) : GeneratePhase
    data class Done(val candidates: List<GeneratedImage>, val model: String) : GeneratePhase
    data class Error(val message: String) : GeneratePhase
}

/**
 * it-077 US-64a · W1/W7/W8「生成效果图」全屏生成 sheet（生成即录入成品图）（it-040 W5 四态交互语言）：
 * 装配（参考图勾选 + 描述 + 高级参数面板）→ 生成（进度可取消）→ 结果（候选挑选）→ 保存挂 effectImages。
 * 照片上传去向一行静态提示（拍板①：自用不做确认弹窗）；开销不拦截。
 */
@Composable
fun OutfitGenerateSheet(
    vm: AppViewModel,
    // null = 组合未保存（W1 直生成，保存时自动建穿搭）
    outfit: Outfit?,
    items: List<Item>,
    person: Person?,
    personNote: String,
    // it-077 修订八：连接由调用方解析传入（入口已按就绪态把关，这里非空直达装配态）
    connection: OutfitImageGenerator.Connection,
    onDismiss: () -> Unit,
) {
    val ec = editorialColors()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val generator = vm.imageGenerator

    var phase by remember { mutableStateOf<GeneratePhase>(GeneratePhase.Setup) }
    var job by remember { mutableStateOf<Job?>(null) }
    // it-077 修订：候选挑选序（sheet 级，随 phase 变化重置；动作栏与滚动区共用）
    var chosen by remember(phase) { mutableStateOf(0) }

    // 装配态输入
    var prompt by remember(outfit?.id) {
        mutableStateOf(OutfitImageGenerator.promptOf(items, scene = "", personNote = personNote))
    }

    // 高级参数面板（按模型记忆，拍板①）
    var paramValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var paramsExpanded by remember { mutableStateOf(false) }
    val modelSpec = connection.modelSpec
    LaunchedEffect(connection.model) {
        val remembered = generator.lastParams()[connection.model].orEmpty()
        val defaults = connection.modelSpec?.params?.associate { it.key to it.default.content }.orEmpty()
        paramValues = defaults + remembered
    }

    // it-077 十二次修订：参考长图预览（开栏合成一次；生成时另以当前描述现合成）
    var composedPreview by remember(outfit?.id) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(outfit?.id, items) {
        composedPreview = vm.imageComposer.composeToExportFile(items, prompt, person?.refImageFile)
    }

    val modelTakesImages = (connection.modelSpec?.inputImages?.first ?: 1) > 0

    fun startGenerate() {
        val conn = connection
        val size = paramValues["size"]
        val extra = paramValues.filterKeys { it != "size" }
        phase = GeneratePhase.Running("提交生成请求…")
        haptics.tick()
        job = scope.launch {
            generator.saveLastParams(conn.model, paramValues)
            when (
                val r = generator.run(
                    connection = conn,
                    prompt = prompt,
                    items = items,
                    personRefFile = person?.refImageFile,
                    resolution = size?.takeIf { it.isNotBlank() && it != "auto" },
                    extra = extra,
                    onProgress = { phase = GeneratePhase.Running(it) },
                )
            ) {
                is OutfitImageGenerator.RunOutcome.Candidates -> {
                    haptics.confirm()
                    phase = GeneratePhase.Done(r.images, r.model)
                }
                is OutfitImageGenerator.RunOutcome.Failed -> phase = GeneratePhase.Error(r.error.userMessage)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (phase !is GeneratePhase.Running) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // it-077 修订八（Leo 反馈×7 抽屉分层/抖动）：去掉 weight 撑满——
        // weight 会把滚动视口强制拉到 0.92 屏高，表单短于视口时动作栏上方悬出空白带，
        // 拖动抽屉时「表面（含空白带+按钮）在动、内容看似不动」即分层感的来源。
        // 改为滚动区贴合内容、仅设高度上限（留出钉底动作栏预算），sheet 高度始终 = 实际内容。
        Column(Modifier.fillMaxWidth()) {
            // it-077 修订（Leo 反馈×5）：内容区滚动、动作按钮钉底——
            // 整列滚动会让按钮跟着滚、下滑关闭手势先滚内容再拖抽屉（不连贯/分层感的根源）
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.80f).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text("生成效果图", style = MaterialTheme.typography.titleLarge, color = ec.ink)
            }

            Text(
                "${connection.spec.displayName} · ${connection.model}" +
                    (modelSpec?.costPerImage?.let { " · 参考价 ¥$it/张" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = ec.inkFaint,
            )

                when (val p = phase) {
                    is GeneratePhase.Setup -> {
                        // ---- 装配：参考长图（it-077 十二次修订：合成一张长图整张发送，撤多选/配额）----
                        // 长图与「穿搭预览」同构（描述置顶 + 人物参考照 + 人体比例拼贴），
                        // 生成时以当前描述现合成一份发模型；此处仅作预览（开栏合成一次）。
                        Text("参考长图", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        val previewFile by remember(composedPreview) { mutableStateOf(composedPreview) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.34f).dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewFile != null) {
                                AsyncImage(
                                    model = previewFile,
                                    contentDescription = "参考长图（人物+全部单品，合成后整张发给模型）",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = ec.inkFaint,
                                )
                            }
                        }
                        Text(
                            if (modelTakesImages) "人物参考照与全部单品合成一张长图整张发送"
                            else "当前模型不吃参考图（纯文生图），将只按描述生成",
                            style = MaterialTheme.typography.labelSmall,
                            color = ec.inkFaint,
                        )

                        // ---- 描述（预填模板 + 场景快捷 chips） ----
                        Text("描述", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            label = { Text("生成描述（可改）") },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PromptPresets.SCENE.options.size) { i ->
                                val option = PromptPresets.SCENE.options[i]
                                FilterChip(
                                    selected = prompt.contains("场景：$option"),
                                    onClick = {
                                        prompt = if (prompt.contains("场景：$option")) {
                                            prompt.replace("\n场景：$option", "")
                                        } else {
                                            prompt.trimEnd() + "\n场景：$option"
                                        }
                                    },
                                    label = { Text(option) },
                                )
                            }
                        }

                        // ---- 高级参数面板（声明式渲染，拍板①） ----
                        val specParams = modelSpec?.params.orEmpty()
                        if (specParams.isNotEmpty()) {
                            TextButton(
                                onClick = { paramsExpanded = !paramsExpanded },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                Text(
                                    if (paramsExpanded) "收起高级参数" else "高级参数（${specParams.size}）",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ec.ink,
                                )
                                Icon(
                                    Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ec.inkFaint,
                                )
                            }
                            if (paramsExpanded) {
                                specParams.forEach { spec ->
                                    ParamRow(
                                        spec = spec,
                                        value = paramValues[spec.key] ?: spec.default.content,
                                        onChange = { paramValues = paramValues + (spec.key to it) },
                                    )
                                }
                            }
                        }

                    }

                    is GeneratePhase.Running -> {
                        // 已等待计时（进入 Running 分支才组合，remember 随分支重建，计时即请求起点）
                        val startedAt = remember { System.currentTimeMillis() }
                        var elapsed by remember { mutableStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                                kotlinx.coroutines.delay(1_000)
                            }
                        }
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "${p.progress}（已等待 ${elapsed}s）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ec.ink,
                        )
                        Text(
                            "通常 1~2 分钟，厂商高峰排队会更久；超过约 3 分钟自动失败可重试",
                            style = MaterialTheme.typography.labelSmall,
                            color = ec.inkFaint,
                        )
                    }

                    is GeneratePhase.Done -> {
                        Text("挑一张保存", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(p.candidates.size) { i ->
                                val selected = i == chosen
                                Surface(
                                    onClick = { chosen = i },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else ec.paper,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else ec.hairline,
                                    ),
                                ) {
                                    AsyncImage(
                                        model = p.candidates[i].bytes,
                                        contentDescription = "候选 ${i + 1}",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier
                                            .size(width = 200.dp, height = 260.dp)
                                            .padding(6.dp),
                                    )
                                }
                            }
                        }
                    }

                    is GeneratePhase.Error -> {
                        Text(
                            "生成失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(p.message, style = MaterialTheme.typography.bodyMedium, color = ec.ink)
                    }
                }
            }  // 内层滚动区闭合（it-077 修订：动作栏须在滚动区外、外层 Column 内）

            // ---- 钉底动作栏（it-077 修订：按钮不随内容滚动；ExportSheet 同构） ----
            Surface(shadowElevation = 6.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (val p = phase) {
                        is GeneratePhase.Setup -> {
                            Button(
                                onClick = { startGenerate() },
                                enabled = prompt.isNotBlank() && items.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.large,
                            ) { Text("生成效果图") }
                            Text(
                                "照片将上传至 ${connection.spec.displayName} 用于本次生成，结果保存在本机。",
                                style = MaterialTheme.typography.labelSmall,
                                color = ec.inkFaint,
                            )
                        }

                        is GeneratePhase.Running -> OutlinedButton(
                            onClick = {
                                job?.cancel()
                                phase = GeneratePhase.Setup
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) { Text("取消") }

                        is GeneratePhase.Done -> Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    val candidate = p.candidates.getOrNull(chosen)
                                    if (candidate != null) {
                                        scope.launch {
                                            when (
                                                val s = generator.save(
                                                    personId = outfit?.personId ?: person?.id ?: "",
                                                    outfitId = outfit?.id,
                                                    itemIds = items.map { it.id }.filterNot { it.startsWith(com.leo.wardrobe.domain.model.WISH_SLOT_PREFIX) },
                                                    chosen = candidate,
                                                    model = p.model,
                                                    prompt = prompt,
                                                )
                                            ) {
                                                is OutfitImageGenerator.SaveOutcome.Saved -> {
                                                    haptics.confirm()
                                                    vm.toast("效果图已录入成品图")
                                                    onDismiss()
                                                }
                                                is OutfitImageGenerator.SaveOutcome.Failed ->
                                                    vm.toast(s.error.userMessage)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("保存到这套穿搭") }
                            OutlinedButton(
                                onClick = { phase = GeneratePhase.Setup },
                                modifier = Modifier.weight(1f),
                            ) { Text("重新生成") }
                        }

                        is GeneratePhase.Error -> Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { phase = GeneratePhase.Setup },
                                modifier = Modifier.weight(1f),
                            ) { Text("返回调整") }
                            Button(
                                onClick = { startGenerate() },
                                modifier = Modifier.weight(1f),
                            ) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}



/** it-077 拍板①：模型专属参数声明式控件——BOOL/ENUM/INT/FLOAT/TEXT 五型 */
@Composable
private fun ParamRow(spec: ImageParamSpec, value: String, onChange: (String) -> Unit) {
    val ec = editorialColors()
    when (spec.type) {
        ParamType.BOOL -> {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium, color = ec.ink)
                Switch(
                    checked = value.toBooleanStrictOrNull() ?: spec.default.content.toBoolean(),
                    onCheckedChange = { onChange(it.toString()) },
                )
            }
        }
        ParamType.ENUM -> {
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium, color = ec.ink)
                Box {
                    TextButton(
                        onClick = { menu = true },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(value, color = ec.ink)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        spec.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onChange(option)
                                    menu = false
                                },
                            )
                        }
                    }
                }
            }
        }
        ParamType.INT, ParamType.FLOAT, ParamType.TEXT -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(spec.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
