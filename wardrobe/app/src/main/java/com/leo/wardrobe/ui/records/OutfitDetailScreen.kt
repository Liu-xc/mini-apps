@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.notesOf
import com.leo.wardrobe.domain.model.outfitById
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.CommentTimeline
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.SegmentedToggleRow
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.outfit.ExportSheet
import com.leo.wardrobe.ui.theme.EditorialMotion
import com.leo.wardrobe.ui.theme.editorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * W7 穿搭详情（US-09/10/13/14）：成品图横滑 + 组成单品 + 录入成品图 + 复制素材 + 评论。
 */
@Composable
fun OutfitDetailScreen(
    vm: AppViewModel,
    outfitId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenOutfit: (String) -> Unit,
) {
    val data by vm.data.collectAsState()
    val outfit = remember(outfitId, data) { data.outfitById(outfitId) }

    if (outfit == null) {
        onBack()
        return
    }

    val notes = remember(data, outfit) { data.notesOf(NoteParent.OUTFIT, outfit.id) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }

    var showDelete by remember { mutableStateOf(false) }
    var showTagEdit by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    // it-077：生成效果图 sheet
    var showGenerate by remember { mutableStateOf(false) }
    var actionMenuOpen by remember { mutableStateOf(false) }
    var editingItems by remember(outfit.id) { mutableStateOf(false) }
    // it-070 US-49：主视图切到「单品布局」的会话态（仅成品图存在时可切，默认成品图）
    var showCollage by rememberSaveable(outfit.id) { mutableStateOf(false) }
    var draftItemIds by remember(outfit.id) { mutableStateOf(outfit.itemIds) }
    var pickerCategories by remember { mutableStateOf<List<WardrobeCategory>?>(null) }
    var tagDraft by remember { mutableStateOf(outfit.tags) }

    val shownItemIds = if (editingItems) draftItemIds else outfit.itemIds
    val items = remember(data, shownItemIds) { shownItemIds.mapNotNull { data.itemById(it) } }

    fun enterItemEdit(categories: List<WardrobeCategory>? = null) {
        if (!editingItems) {
            draftItemIds = outfit.itemIds
            editingItems = true
        }
        // it-070：调整单品的空槽/点选交互都在拼贴上，进编辑即切到单品布局
        showCollage = true
        pickerCategories = categories
    }

    fun selectItem(itemId: String) {
        val selected = data.itemById(itemId) ?: return
        val replaced = draftItemIds.filterNot { currentId ->
            data.itemById(currentId)?.category == selected.category
        }
        draftItemIds = (replaced + selected.id).distinct()
        pickerCategories = null
    }

    fun saveItemEdit() {
        vm.updateOutfitItems(outfit.id, draftItemIds) { saved ->
            if (saved) editingItems = false
        }
    }

    val pickEffect = rememberPhotoPicker { uri ->
        if (uri != null) vm.importEffectImage(outfit, items.map { it.id }, uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
        // it-069 修3：日期并入全站 updatedAt 口径（与列表缩略/评论一致）——
        // 原 createdAt 会在编辑后与列表日期打架
        title = { Text("穿搭 · ${dateFormat.format(Date(outfit.updatedAt))}") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (editingItems) {
                    IconButton(onClick = { editingItems = false; draftItemIds = outfit.itemIds; pickerCategories = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "取消调整")
                    }
                    IconButton(
                        onClick = { saveItemEdit() },
                        enabled = draftItemIds.isNotEmpty() && draftItemIds != outfit.itemIds,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "保存调整")
                    }
                } else {
                    Box {
                        IconButton(onClick = { actionMenuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多穿搭操作")
                        }
                        DropdownMenu(
                            expanded = actionMenuOpen,
                            onDismissRequest = { actionMenuOpen = false },
                        ) {
                            // it-068：放开「调整单品」——有成品图也可换季改一件（Leo 拍板），
                            // 防误导改由成品图区「调整前组合」标注承担（it-049 限制解除）
                            DropdownMenuItem(
                                text = { Text("调整单品") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    actionMenuOpen = false
                                    enterItemEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑标签") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    actionMenuOpen = false
                                    tagDraft = outfit.tags
                                    showTagEdit = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("生成效果图") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AutoFixHigh,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    actionMenuOpen = false
                                    showGenerate = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("创建副本") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    actionMenuOpen = false
                                    vm.duplicateOutfit(outfit) { duplicate ->
                                        duplicate?.let { onOpenOutfit(it.id) }
                                    }
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showDelete = true }) {
                        // it-034 C5：破坏性操作警示红（执行前确认对话框已有，见下方 showDelete）
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除穿搭",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // it-045：顶栏下缘→内容 20dp 全站节奏
                .padding(top = 20.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 主视觉（it-011 O7）：成品图横滑优先；无成品图时人体叙事拼贴兜底，
            // 「录入成品图」合并为拼贴右下角标，不再整行重复两个入口。
            // it-070 US-49：有成品图时可切「单品布局」——两种视图主动对照查看；
            // 无成品图时拼贴本就是唯一视图，不出开关
            val hasEffectImages = outfit.effectImages.isNotEmpty()
            if (hasEffectImages) {
                SegmentedToggleRow(
                    selectedIndex = if (showCollage) 1 else 0,
                    onSelect = { showCollage = it == 1 },
                    count = 2,
                    modifier = Modifier.fillMaxWidth(),
                ) { i ->
                    Text(
                        if (i == 0) "成品图" else "单品布局",
                        style = MaterialTheme.typography.labelMedium,
                        color = editorialColors().ink,
                    )
                }
            }
            if (hasEffectImages && !showCollage) {
                val pagerState = rememberPagerState(pageCount = { outfit.effectImages.size })
                Box {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                        // it-047 #9/#11：吸附收敛进 EditorialMotion.pagerFling（含「移除动画」瞬时降级）
                        // + 预取防入屏白块（W7）
                        flingBehavior = EditorialMotion.pagerFling(
                            state = pagerState,
                            reduce = EditorialMotion.reduceMotion(),
                        ),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val img = outfit.effectImages[page]
                        // it-064 修2a：撤销 it-058 C2 的轮播共享元素（W8 hero 端已撤，
                        // 无配对即无动画；卡组场景的切换流畅优先，见 RecordsScreen 注记）
                        // it-077：AI 生成的成品图带角标（自用区分生成图与手动录入）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.86f),
                        ) {
                            PhotoCard(
                                file = vm.imageFileOf(img.file),
                                contentDescription = "成品效果图 ${page + 1}",
                                contentScale = ContentScale.Fit,
                                mat = true,
                                modifier = Modifier.matchParentSize(),
                            )
                            if (img.source == com.leo.wardrobe.data.gen.OutfitImageGenerator.SOURCE_AI) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    color = editorialColors().accent,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (outfit.effectImages.size > 1) {
                        // 当前页删除小按钮
                        IconButton(
                            onClick = {
                                vm.removeEffectImage(outfit.id, outfit.effectImages[pagerState.currentPage].file)
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除这张成品图",
                                tint = editorialColors().inkFaint,
                            )
                        }
                    }
                }
                // 指示点
                if (outfit.effectImages.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        repeat(outfit.effectImages.size) { i ->
                            Box(
                                Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage) editorialColors().accent
                                        else editorialColors().hairline,
                                    ),
                            )
                        }
                    }
                }
                // it-068：单品调整后的旧效果标注——防误导轻注记（不抢主体、灰阶、录入新成品图即解除）
                if (outfit.effectStale) {
                    Text(
                        "成品图为调整前组合",
                        style = MaterialTheme.typography.labelSmall,
                        color = editorialColors().inkFaint,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 2.dp),
                    )
                }
            } else {
                Box {
                    com.leo.wardrobe.ui.components.BodyCollage(
                        items = items,
                        imageFileOf = vm::imageFileOf,
                        onEmptySlotClick = { categories -> enterItemEdit(categories) },
                        onItemClick = if (editingItems) {
                            { item -> pickerCategories = listOf(item.category) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f),
                    )
                    // it-012：角标移拼贴右上，远离「未配X」空槽语义区
                    if (!editingItems) {
                        Surface(
                            onClick = { pickEffect() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    "录入成品图",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (editingItems) {
                Text(
                    "点衣物可替换，点虚线空槽可补齐",
                    style = MaterialTheme.typography.bodySmall,
                    color = editorialColors().inkFaint,
                )
                OutlinedButton(
                    onClick = { pickerCategories = WardrobeCategory.entries },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加单品")
                }
            }

            if (outfit.tags.isNotEmpty()) {
                Row { TagRow(outfit.tags) }
            }

            Text("这套包含", style = MaterialTheme.typography.titleMedium, color = editorialColors().ink)
            items.forEach { item ->
                // it-011 O7：单品行可点直达衣物详情（spec US-10），chevron + 按压反馈
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    onClick = {
                        if (editingItems) pickerCategories = listOf(item.category)
                        else onOpenItem(item.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        PhotoCard(
                            file = vm.imageFileOf(item.imageFile),
                            contentDescription = item.name,
                            corner = 10.dp,
                            mat = true,
                            modifier = Modifier.size(width = 44.dp, height = 54.dp),
                        )
                        Text(
                            "${item.category.label} · ${item.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = editorialColors().ink,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        if (editingItems) {
                            IconButton(onClick = { draftItemIds = draftItemIds.filterNot { it == item.id } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "移除${item.name}")
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = editorialColors().inkFaint,
                            )
                        }
                    }
                }
            }

            // it-018 阶段A：打卡主按钮（未打卡=去打卡；已打卡=再记一次（换装）/ 撤销今日）
            val todayWearCount = remember(data, outfit) {
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now()
                val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                data.wearLogs.count { it.outfitId == outfit.id && it.at >= from && it.at < to }
            }
            val haptics = rememberHaptics()  // it-027：打卡确认触感（DESIGN.md §4）
            if (todayWearCount == 0) {
                Button(
                    onClick = {
                        haptics.confirm()
                        vm.checkinOutfit(outfit.id)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("今天穿了这套")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            vm.checkinOutfit(outfit.id)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("今日已穿 · 再记一次")
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            vm.undoTodayWear(outfit.id)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("撤销今日")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // it-031 C7：次按钮降为描边，打卡主按钮保持页面唯一实底；it-030：图标 Material 化
                OutlinedButton(onClick = { showExport = true }, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复制长图")  // it-012：与搭配页同一套词
                }
                // it-077 修订（Leo 反馈）：定位为「录入成品图」的 AI 兄弟功能，名「生成效果图」
                OutlinedButton(onClick = { showGenerate = true }, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("生成效果图")
                }
            }

            HorizontalDivider(color = editorialColors().hairline)
            Text("评论（${notes.size}）", style = MaterialTheme.typography.titleMedium, color = editorialColors().ink)
            CommentTimeline(
                notes = notes,
                onSend = { vm.addNote(NoteParent.OUTFIT, outfit.id, it) },
                onDelete = { vm.deleteNote(it) },
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    pickerCategories?.let { categories ->
        val candidates = data.items.filter { item ->
            item.personId == outfit.personId && item.category in categories
        }
        AlertDialog(
            onDismissRequest = { pickerCategories = null },
            title = {
                Text(
                    when {
                        categories.size == 1 -> "选择${categories.first().label}"
                        categories == WardrobeCategory.entries -> "添加单品"
                        else -> "补齐上身"
                    },
                )
            },
            text = {
                if (candidates.isEmpty()) {
                    Text("当前角色没有可选单品", color = editorialColors().ink)
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        candidates.forEach { candidate ->
                            Surface(
                                onClick = { selectItem(candidate.id) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    PhotoCard(
                                        file = vm.imageFileOf(candidate.imageFile),
                                        contentDescription = candidate.name,
                                        corner = 10.dp,
                                        mat = true,
                                        modifier = Modifier.size(width = 44.dp, height = 54.dp),
                                    )
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(
                                            candidate.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = editorialColors().ink,
                                        )
                                        Text(
                                            "${candidate.category.label} · ${candidate.color}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = editorialColors().inkFaint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerCategories = null }) { Text("取消") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这套穿搭？") },
            text = { Text("成品图与该穿搭的评论将一并删除，单品不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteOutfit(outfit.id)
                    showDelete = false
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }

    if (showTagEdit) {
        AlertDialog(
            onDismissRequest = { showTagEdit = false },
            title = { Text("穿搭标签") },
            text = {
                Column {
                    TagInput(tags = tagDraft, onChange = { tagDraft = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        TextButton(onClick = { showTagEdit = false }) { Text("取消") }
                        TextButton(onClick = {
                            vm.updateOutfitTags(outfit.id, tagDraft)
                            showTagEdit = false
                        }) { Text("保存") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    if (showExport) {
        ExportSheet(
            vm = vm,
            items = items,
            existingOutfit = outfit,
            refPhotoFile = vm.currentPerson.value?.refImageFile,
            onDismiss = { showExport = false },
        )
    }

    // it-077：生成效果图 sheet
    if (showGenerate) {
        OutfitGenerateSheet(
            vm = vm,
            outfit = outfit,
            items = items,
            person = vm.currentPerson.collectAsState().value,
            personNote = vm.personNote.collectAsState().value,
            onDismiss = { showGenerate = false },
        )
    }
}
