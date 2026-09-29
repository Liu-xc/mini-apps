@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.wardrobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.WardrobeCategoryGrayscaleFilter
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.iconRes
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.EditorialMotion
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * W4 添加/编辑衣物（US-01/02/13）：照片必填 + 品类/颜色/标签点选录入。
 *
 * it-059 点选优先（Leo 反馈「表单太长有压力」）：主区全部点选完成（品类/颜色/常用标签 chips），
 * 名称留空自动命名（颜色+品类），手动输入（描述/自定义颜色/自定义标签）折叠进「补充细节」
 * 二次交互展开——录一件衣物可以零打字。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    vm: AppViewModel,
    itemId: String?,
    onBack: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val existing = remember(itemId, data) { itemId?.let { data.itemById(it) } }

    var importedFile by remember { mutableStateOf<String?>(null) } // 选择后立即导入落盘的文件名
    var importing by remember { mutableStateOf(false) }
    // it-016 US-15 去背景：cutoutFile 非空 ⇔ 已采用抠图版（预览与保存都走它）；
    // 原图 importedFile 保留作还原锚点，确认采用（保存成功）后即弃（Leo 定，2026-09-20）
    var cutoutFile by remember { mutableStateOf<String?>(null) }
    var cutting by remember { mutableStateOf(false) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var nameTouched by remember(existing?.id) { mutableStateOf(existing != null) } // it-059：编辑态视为已定名
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: WardrobeCategory.TOP) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color ?: "") }
    var desc by remember(existing?.id) { mutableStateOf(existing?.desc ?: "") }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags ?: emptyList()) }
    var photoMissing by remember { mutableStateOf(false) }
    var detailOpen by remember { mutableStateOf(false) } // it-059：补充细节折叠区
    val haptics = rememberHaptics()  // it-027：确认动作触感（DESIGN.md §4）

    fun runCutout(src: String) {
        if (cutting) return
        cutting = true
        vm.cutoutPhoto(src) { out ->
            cutting = false
            if (out != null) {
                cutoutFile?.let(vm::deletePhotoFile)
                cutoutFile = out
                haptics.confirm()
            } // 推理失败时保留原图，VM 提示可重试
        }
    }

    val pickPhoto = rememberPhotoPicker { uri ->
        if (uri != null) {
            importing = true
            vm.importPhoto(uri) { file ->
                importing = false
                if (file != null) {
                    // 新照片默认去背景；保留原图作候选回退，直到用户保存或还原。
                    cutoutFile?.let(vm::deletePhotoFile)
                    cutoutFile = null
                    importedFile?.let(vm::deletePhotoFile)
                    importedFile = file
                    photoMissing = false
                    runCutout(file)
                }
            }
        }
    }

    fun restoreOriginal() {
        cutoutFile?.let(vm::deletePhotoFile)
        cutoutFile = null
        haptics.tick()
    }

    val hasPhoto = importedFile != null || existing != null

    // it-059：名称自动兜底——用户没动过名称时，跟随颜色/品类生成默认名（如「米色上装」）
    val autoName = buildString {
        if (color.isNotBlank()) append(color)
        append(category.label)
    }
    val effectiveName = name.ifBlank { autoName }
    val saveReady = hasPhoto

    // it-011 O3：吸底保存两态（与 eats it-004 同模式）；顶栏只留关闭
    fun doSave() {
        if (!hasPhoto) {
            photoMissing = true
        } else {
            // 采用抠图版则保存它并删原图（原图即弃）；否则抠图版必为 null
            vm.saveItem(existing, cutoutFile ?: importedFile, effectiveName, category, color, desc, tags) { ok ->
                if (ok) {
                    haptics.confirm()
                    if (cutoutFile != null) importedFile?.let(vm::deletePhotoFile)
                    onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "添加衣物" else "编辑衣物") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                },
            )
        },
        // it-012 重构：与 eats it-005 同构——中性灰原因 + 全宽两态；点击未就绪按钮 toast 缺什么
        bottomBar = {
            val reason = if (!hasPhoto) "还差一张照片" else null
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (reason != null) {
                        Text(
                            reason,
                            style = MaterialTheme.typography.labelLarge,
                            color = editorialColors().inkFaint,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                haptics.error()
                                vm.toast("先选一张照片")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (existing == null) "保存" else "更新") }
                    } else {
                        Button(
                            onClick = { doSave() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                if (existing == null) "保存" else "更新",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // it-045：顶栏下缘→内容 20dp 全站节奏
                .padding(top = 20.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // it-012 O7'：无照片时给大虚线预览占位——进页面即知第一步
            if (!hasPhoto) {
                Surface(
                    onClick = { pickPhoto() },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (photoMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().aspectRatio(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = editorialColors().inkFaint,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("拍照 / 选照片 · 第一步（必填 *）", style = MaterialTheme.typography.bodyMedium, color = editorialColors().ink)
                    }
                }
            }
            // 照片预览（已落盘的本地文件；采用抠图版时垫棋盘格提示透明底）
            val cell = 12.dp
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            val previewFile = cutoutFile ?: importedFile
            if (previewFile != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (cutoutFile != null) Modifier.drawBehind {
                                val light = Color(0xFFF2F3F5)
                                val dark = Color(0xFFE1E3E8)
                                var row = 0
                                var y = 0f
                                while (y < size.height) {
                                    var col = row
                                    var x = 0f
                                    while (x < size.width) {
                                        drawRect(
                                            if (col % 2 == 0) dark else light,
                                            topLeft = Offset(x, y),
                                            size = Size(
                                                minOf(cellPx, size.width - x),
                                                minOf(cellPx, size.height - y),
                                            ),
                                        )
                                        x += cellPx
                                        col++
                                    }
                                    y += cellPx
                                    row++
                                }
                            } else Modifier
                        ),
                ) {
                    PhotoCard(
                        file = vm.imageFileOf(previewFile),
                        contentDescription = "新照片预览",
                        corner = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (existing != null) {
                PhotoCard(
                    file = vm.imageFileOf(existing.imageFile),
                    contentDescription = existing.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                )
            }
            // it-016 US-15：去背景操作行（仅新照片提供；编辑旧衣物不补抠）
            if (importedFile != null) {
                if (cutoutFile != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "已去背景 · 透明底",
                                style = MaterialTheme.typography.bodyMedium,
                                color = editorialColors().ink,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { restoreOriginal() }) { Text("还原") }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { importedFile?.let(::runCutout) },
                        enabled = !cutting && !importing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        if (cutting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在去背景…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(
                                Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = editorialColors().inkFaint,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "去背景 · 一键透明底",
                                style = MaterialTheme.typography.bodyMedium,
                                color = editorialColors().ink,
                            )
                        }
                    }
                }
            }
            if (hasPhoto) Surface(
                onClick = { pickPhoto() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (photoMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = editorialColors().inkFaint,
                    )
                    Text(
                        when {
                            importing -> "正在导入照片…"
                            hasPhoto -> "点击更换照片（必填）"
                            else -> "从相册选择照片（必填）"  // it-030：相机语义由左侧 Material 图标承载
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = editorialColors().ink,
                    )
                }
            }

            // it-059：名称降级为「自动命名 + 可选改」——留空保存自动用 颜色+品类
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameTouched = true
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                placeholder = { Text(if (nameTouched || autoName.isEmpty()) "如：白色牛津纺衬衫" else "留空自动命名「$autoName」") },
                supportingText = if (name.isBlank() && autoName.isNotEmpty()) {
                    { Text("留空将自动命名为「$autoName」", style = MaterialTheme.typography.labelSmall) }
                } else null,
            )

            Text("品类", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
            // it-033：品类 chips 触控高 ≥48dp（含上下 padding）、行距 ≥8dp（走查实测原 19dp）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        modifier = Modifier.height(48.dp),
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(c.iconRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = WardrobeCategoryGrayscaleFilter,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(c.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        },
                    )
                }
            }

            // it-059：颜色改预设 chips 点选（原纯文本框）——自由文本值以附加 chip 回显
            val presetColors = listOf(
                "白色", "黑色", "灰色", "米色", "卡其", "军绿",
                "藏青", "蓝色", "浅蓝", "棕色", "红色", "黄色", "粉色",
            )
            val extraColor = color.takeIf { it.isNotBlank() && it !in presetColors }
            Text("颜色", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetColors.forEach { c ->
                    FilterChip(
                        selected = color == c,
                        onClick = { color = if (color == c) "" else c },
                        modifier = Modifier.height(44.dp),
                        label = { Text(c) },
                    )
                }
                if (extraColor != null) {
                    FilterChip(
                        selected = true,
                        onClick = { color = "" },
                        modifier = Modifier.height(44.dp),
                        label = { Text("$extraColor ✕") },
                    )
                }
            }

            // it-059：常用标签 chips 多选（原手输 TagInput 主入口折叠）——已选自定义标签同样回显
            val presetTags = listOf("通勤", "休闲", "运动", "约会", "度假", "居家", "简约", "冬", "早秋", "夏")
            val extraTags = tags.filter { it !in presetTags }
            Text("标签", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetTags.forEach { t ->
                    FilterChip(
                        selected = t in tags,
                        onClick = { tags = if (t in tags) tags - t else tags + t },
                        modifier = Modifier.height(44.dp),
                        label = { Text(t) },
                    )
                }
                extraTags.forEach { t ->
                    FilterChip(
                        selected = true,
                        onClick = { tags = tags - t },
                        modifier = Modifier.height(44.dp),
                        label = { Text("$t ✕") },
                    )
                }
            }

            // it-059：补充细节折叠区——描述/自定义颜色/自定义标签的二次交互
            Surface(
                onClick = { detailOpen = !detailOpen },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, editorialColors().hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        if (detailOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = editorialColors().inkFaint,
                    )
                    Text(
                        "补充细节（描述 · 自定义颜色与标签）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = editorialColors().ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (desc.isNotBlank() || extraColor != null || extraTags.isNotEmpty()) {
                        Text(
                            "已填",
                            style = MaterialTheme.typography.labelSmall,
                            color = editorialColors().inkFaint,
                        )
                    }
                }
            }
            // it-051 折叠语言：smooth 高度过渡 + 短淡入（走框架动画缩放，减弱动态自动瞬时）
            AnimatedVisibility(
                visible = detailOpen,
                enter = expandVertically(animationSpec = EditorialMotion.smooth()) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(animationSpec = EditorialMotion.smooth()) + fadeOut(animationSpec = tween(90)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("描述（会拼进生图文案）") },
                        placeholder = { Text("如：宽松棉质、纽扣领") },
                    )
                    OutlinedTextField(
                        value = if (extraColor != null) color else "",
                        onValueChange = { color = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("自定义颜色") },
                        placeholder = { Text("如：燕麦色") },
                    )
                    Column {
                        Text(
                            "自定义标签",
                            style = MaterialTheme.typography.labelMedium,
                            color = editorialColors().inkFaint,
                        )
                        TagInput(tags = tags, onChange = { tags = it })
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
