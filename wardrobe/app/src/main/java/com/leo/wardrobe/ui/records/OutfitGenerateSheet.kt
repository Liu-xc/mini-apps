package com.leo.wardrobe.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ParamType
import com.leo.libs.agent.image.GeneratedImage
import com.leo.wardrobe.data.gen.OutfitImageGenerator
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WISH_SLOT_PREFIX
import com.leo.wardrobe.domain.usecase.PromptPresets
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * it-077 修订八：连接解析上提到页面级（打开前已完成）——sheet 内异步解析会造成
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

/** 生成工作台四态（it-040 W5 四态语言） */
sealed interface GeneratePhase {
    data object Setup : GeneratePhase
    data class Running(val progress: String) : GeneratePhase
    data class Done(val candidates: List<GeneratedImage>, val model: String) : GeneratePhase
    data class Error(val message: String) : GeneratePhase
}

/**
 * it-077 US-64a · W8/W7「生成效果图」全屏工作台（十三次修订：FullscreenSheet 承载，
 * 弃 ModalBottomSheet——长表单+钉底动作栏的 92% 屏工作流不再与 sheet 拖拽/嵌套滚动纠缠）。
 */
@Composable
fun OutfitGenerateSheet(
    vm: AppViewModel,
    // null = 组合未保存（生成时自动建穿搭）
    outfit: Outfit?,
    items: List<Item>,
    person: Person?,
    personNote: String,
    connection: OutfitImageGenerator.Connection,
    onDismiss: () -> Unit,
) {
    var phase by remember { mutableStateOf<GeneratePhase>(GeneratePhase.Setup) }
    com.leo.wardrobe.ui.components.FullscreenSheet(
        title = "生成效果图",
        onDismiss = onDismiss,
        dismissGuard = phase is GeneratePhase.Running,
    ) {
        GenerateWorkbench(
            vm = vm,
            outfit = outfit,
            items = items,
            person = person,
            personNote = personNote,
            connection = connection,
            phase = phase,
            onPhaseChange = { phase = it },
            onDone = onDismiss,
        )
    }
}

/**
 * 生成工作台本体（it-077 十三次修订）：滚动装配区 + 钉底动作栏，宿主为全屏容器
 * （FullscreenSheet 的 weight 区，或穿搭预览面板的「生成」模式）。
 * 装配（参考长图 + 描述 + 高级参数）→ 生成（进度可取消）→ 结果（候选挑选）→ 保存挂 effectImages。
 * 照片上传去向一行静态提示（拍板①：自用不做确认弹窗）；开销不拦截。
 * phase 由宿主持有（FullscreenSheet 关闭守卫需要知道 Running 态）。
 */
@Composable
fun GenerateWorkbench(
    vm: AppViewModel,
    outfit: Outfit?,
    items: List<Item>,
    person: Person?,
    personNote: String,
    connection: OutfitImageGenerator.Connection,
    phase: GeneratePhase,
    onPhaseChange: (GeneratePhase) -> Unit,
    onDone: () -> Unit,
) {
    val ec = editorialColors()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val generator = vm.imageGenerator

    // 候选挑选序（随 phase 变化重置；动作栏与滚动区共用）
    var chosen by remember(phase) { mutableStateOf(0) }
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
    var job by remember { mutableStateOf<Job?>(null) }

    fun startGenerate() {
        val size = paramValues["size"]
        val extra = paramValues.filterKeys { it != "size" }
        onPhaseChange(GeneratePhase.Running("提交生成请求…"))
        haptics.tick()
        job = scope.launch {
            generator.saveLastParams(connection.model, paramValues)
            when (
                val r = generator.run(
                    connection = connection,
                    prompt = prompt,
                    items = items,
                    personRefFile = person?.refImageFile,
                    resolution = size?.takeIf { it.isNotBlank() && it != "auto" },
                    extra = extra,
                    onProgress = { onPhaseChange(GeneratePhase.Running(it)) },
                )
            ) {
                is OutfitImageGenerator.RunOutcome.Candidates -> {
                    haptics.confirm()
                    onPhaseChange(GeneratePhase.Done(r.images, r.model))
                }
                is OutfitImageGenerator.RunOutcome.Failed ->
                    onPhaseChange(GeneratePhase.Error(r.error.userMessage))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 滚动装配区（全屏容器内 weight 无撑满歧义）----
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${connection.spec.displayName} · ${connection.model}" +
                    (modelSpec?.costPerImage?.let { " · 参考价 ¥$it/张" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = ec.inkFaint,
            )

            when (val p = phase) {
                is GeneratePhase.Setup -> {
                    // ---- 装配：参考长图（十二次修订：合成一张长图整张发送，无多选/配额）----
                    Text("参考长图", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (composedPreview != null) {
                            AsyncImage(
                                model = composedPreview,
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
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
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
                                    contentScale = ContentScale.Fit,
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
        }

        // ---- 钉底动作栏（全屏容器结构保证钉底；48dp 硬等高修「按钮压扁」）----
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
                            onPhaseChange(GeneratePhase.Setup)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
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
                                                itemIds = items.map { it.id }.filterNot { it.startsWith(WISH_SLOT_PREFIX) },
                                                chosen = candidate,
                                                model = p.model,
                                                prompt = prompt,
                                            )
                                        ) {
                                            is OutfitImageGenerator.SaveOutcome.Saved -> {
                                                haptics.confirm()
                                                vm.toast("效果图已录入成品图")
                                                onDone()
                                            }
                                            is OutfitImageGenerator.SaveOutcome.Failed ->
                                                vm.toast(s.error.userMessage)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("保存到这套穿搭") }
                        OutlinedButton(
                            onClick = { onPhaseChange(GeneratePhase.Setup) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("重新生成") }
                    }

                    is GeneratePhase.Error -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = { onPhaseChange(GeneratePhase.Setup) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("返回调整") }
                        Button(
                            onClick = { startGenerate() },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("重试") }
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
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
