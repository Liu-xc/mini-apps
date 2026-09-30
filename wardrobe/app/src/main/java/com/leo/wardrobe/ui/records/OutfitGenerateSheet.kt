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
import androidx.compose.runtime.mutableStateMapOf
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
import com.leo.libs.agent.ImageParamSpec
import com.leo.libs.agent.ParamType
import com.leo.libs.agent.image.GeneratedImage
import com.leo.wardrobe.data.gen.OutfitImageGenerator
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.usecase.PromptPresets
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


/** it-077 修订（Leo 反馈×4）：生图连接未配置（无厂商/模型/Key）时隐藏所有 AI 生成入口；配置后自动出现 */
@Composable
fun rememberImageGenReady(vm: AppViewModel): Boolean =
    androidx.compose.runtime.produceState(initialValue = false) {
        value = vm.imageGenerator.connection() != null
    }.value

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
    onDismiss: () -> Unit,
) {
    val ec = editorialColors()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val generator = vm.imageGenerator

    var connection by remember { mutableStateOf<OutfitImageGenerator.Connection?>(null) }
    var connectionResolved by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<GeneratePhase>(GeneratePhase.Setup) }
    var job by remember { mutableStateOf<Job?>(null) }

    // 装配态输入
    var prompt by remember(outfit?.id) {
        mutableStateOf(OutfitImageGenerator.promptOf(items, scene = "", personNote = personNote))
    }
    val includeItems = remember(outfit?.id) { mutableStateMapOf(*items.map { it.id to true }.toTypedArray()) }
    val hasPersonRef = person?.refImageFile != null
    var includePerson by remember(outfit?.id) { mutableStateOf(hasPersonRef) }

    // 高级参数面板（按模型记忆，拍板①）
    var paramValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var paramsExpanded by remember { mutableStateOf(false) }
    val modelSpec = connection?.modelSpec
    LaunchedEffect(connection?.model) {
        val conn = connection ?: return@LaunchedEffect
        val remembered = generator.lastParams()[conn.model].orEmpty()
        val defaults = conn.modelSpec?.params?.associate { it.key to it.default.content }.orEmpty()
        paramValues = defaults + remembered
    }

    val selectedItems = items.filter { includeItems[it.id] == true }
    val inputLimit = connection?.let(generator::inputLimit) ?: 10
    val refCount = (if (includePerson && hasPersonRef) 1 else 0) + selectedItems.size
    val modelTakesImages = (connection?.modelSpec?.inputImages?.first ?: 1) > 0

    fun startGenerate() {
        val conn = connection ?: return
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
                    items = selectedItems,
                    personRefFile = if (includePerson && hasPersonRef) person?.refImageFile else null,
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
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
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

            if (!connectionResolved) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (connection == null) {
                // 未配置生图模型（W11 引导）
                Text(
                    "尚未配置生图模型：到「设置 → 生图模型」选择厂商、模型并粘贴 API Key 后再来生成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ec.inkFaint,
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("知道了") }
            } else {
                val conn = connection!!
                Text(
                    "${conn.spec.displayName} · ${conn.model}" +
                        (modelSpec?.costPerImage?.let { " · 参考价 ¥$it/张" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = ec.inkFaint,
                )

                when (val p = phase) {
                    is GeneratePhase.Setup -> {
                        // ---- 装配：参考图勾选 ----
                        Text("参考图", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasPersonRef) {
                                item {
                                    RefTile(
                                        selected = includePerson,
                                        onToggle = { includePerson = !includePerson },
                                        content = {
                                            PhotoCard(
                                                file = vm.imageFileOf(person!!.refImageFile!!),
                                                contentDescription = "人物参考照",
                                                corner = 14.dp,
                                                modifier = Modifier.size(width = 64.dp, height = 80.dp),
                                            )
                                        },
                                        caption = "人物",
                                    )
                                }
                            }
                            items(items.size) { i ->
                                val item = items[i]
                                RefTile(
                                    selected = includeItems[item.id] == true,
                                    onToggle = { includeItems[item.id] = !(includeItems[item.id] ?: true) },
                                    content = {
                                        PhotoCard(
                                            file = vm.imageFileOf(item.imageFile),
                                            contentDescription = item.name,
                                            corner = 14.dp,
                                            modifier = Modifier.size(width = 64.dp, height = 80.dp),
                                        )
                                    },
                                    caption = item.name,
                                )
                            }
                        }
                        Text(
                            when {
                                !modelTakesImages -> "当前模型不吃参考图（纯文生图），将只按描述生成"
                                else -> "已选 $refCount / 参考图上限 $inputLimit 张（人物 1 + 衣物 ${selectedItems.size}）"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (refCount > inputLimit && modelTakesImages) {
                                MaterialTheme.colorScheme.error
                            } else {
                                ec.inkFaint
                            },
                        )
                        if (!hasPersonRef) {
                            Text(
                                "未设置人物参考照：生成将不含人物参考（可在角色信息里设置形象参考照）",
                                style = MaterialTheme.typography.labelSmall,
                                color = ec.inkFaint,
                            )
                        }

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

                        Button(
                            onClick = { startGenerate() },
                            enabled = prompt.isNotBlank() && selectedItems.isNotEmpty() &&
                                (!modelTakesImages || refCount <= inputLimit),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.large,
                        ) { Text("生成效果图") }

                        Text(
                            "照片将上传至 ${conn.spec.displayName} 用于本次生成，结果保存在本机。",
                            style = MaterialTheme.typography.labelSmall,
                            color = ec.inkFaint,
                        )
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
                        OutlinedButton(
                            onClick = {
                                job?.cancel()
                                phase = GeneratePhase.Setup
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) { Text("取消") }
                    }

                    is GeneratePhase.Done -> {
                        var chosen by remember { mutableStateOf(0) }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
                    }

                    is GeneratePhase.Error -> {
                        Text(
                            "生成失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(p.message, style = MaterialTheme.typography.bodyMedium, color = ec.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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

/** 参考图勾选瓦片：整块可点切换，未选中盖半透明遮罩（it-040 四态语言里的选态表达） */
@Composable
private fun RefTile(
    selected: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
    caption: String,
) {
    val ec = editorialColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.clickable { onToggle() }) {
            content()
            if (!selected) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.matchParentSize(),
                ) {}
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ec.ink else ec.inkFaint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(68.dp),
        )
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
