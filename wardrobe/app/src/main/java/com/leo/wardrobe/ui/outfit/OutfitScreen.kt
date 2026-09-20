package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.SlotCell
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * W1 搭配页（it-003：一屏 3×3 网格；it-004：底部 ☆ 保存这套 + 去重检测）。
 */
@Composable
fun OutfitScreen(
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenOutfit: (String) -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    val slotSel by vm.slotSelections.collectAsState()
    val scope = rememberCoroutineScope()
    var showPersonSheet by remember { mutableStateOf(false) }
    var exportItems by remember { mutableStateOf<List<Item>?>(null) }

    val personId = person?.id
    val allItems = if (personId != null) data.itemsOf(personId) else emptyList()
    val catItems: Map<WardrobeCategory, List<Item>> = remember(allItems) {
        WardrobeCategory.entries.associateWith { c -> allItems.filter { it.category == c } }
    }

    // it-011 O6：首次进入做格位滑动 coach（仅一次，Prefs 落标记）
    val coachShown by vm.coachSlotsShown.collectAsState()
    var coachPhase by remember { mutableStateOf(false) }
    LaunchedEffect(coachShown, allItems) {
        if (!coachShown && allItems.isNotEmpty()) {
            delay(700) // 等首帧与 pager 就位
            coachPhase = true
            vm.markCoachSlotsShown()
            delay(1100)
            coachPhase = false
        }
    }

    val pagerStates = remember { mutableStateMapOf<WardrobeCategory, PagerState>() }
    WardrobeCategory.entries.forEach { category ->
        val items = catItems[category].orEmpty()
        if (items.isEmpty()) {
            pagerStates.remove(category)
            return@forEach
        }
        key(items.map { it.id }) {
            val state = rememberPagerState(initialPage = 0, pageCount = { items.size })
            LaunchedEffect(state) { pagerStates[category] = state }
            // 组合记忆（US-06）：先恢复后持久化，消除竞态
            LaunchedEffect(state, items) {
                val saved = vm.firstSlotSelection(personId, category)
                val idx = items.indexOfFirst { it.id == saved }
                if (idx > 0) state.scrollToPage(idx)
                snapshotFlow { state.settledPage }.collect { page ->
                    items.getOrNull(page)?.let { vm.setSlot(category, it.id) }
                }
            }
        }
    }

    fun currentSelection(): List<Item> = WardrobeCategory.entries.mapNotNull { c ->
        val items = catItems[c].orEmpty()
        val state = pagerStates[c]
        if (items.isEmpty() || state == null) null
        else items.getOrNull(state.currentPage)
    }

    /**
     * 基于组合记忆（slotSel，落定值）推导当前组合（it-004）：
     * 与 pager 注册时机解耦、随记忆变化响应式更新；记忆未覆盖时回退该品类第一件。
     */
    val currentItemsFromMemory: List<Item> = catItems.entries.mapNotNull { (_, items) ->
        items.firstOrNull { it.id == slotSel[it.category.name] } ?: items.firstOrNull()
    }
    val currentIdsFromMemory = currentItemsFromMemory.map { it.id }
    val savedOutfit = if (personId != null) vm.savedOutfitFor(currentIdsFromMemory) else null

    Column(Modifier.fillMaxSize()) {
        // 顶栏：角色名 + 随机一套
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showPersonSheet = true }) {
                Text(
                    "${person?.emoji ?: ""} ${person?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = editorialColors().ink,
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = "切换角色",
                    tint = editorialColors().inkFaint,
                )
            }
            TextButton(
                onClick = {
                    scope.launch {
                        pagerStates.entries.toList()
                            .sortedBy { it.key.ordinal }
                            .filter { it.value.pageCount > 1 }
                            .forEachIndexed { index, (_, state) ->
                                launch {
                                    delay(index * 100L)
                                    state.animateScrollToPage(Random.nextInt(state.pageCount))
                                }
                            }
                    }
                },
                enabled = allItems.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("随机一套", style = MaterialTheme.typography.titleSmall)
            }
        }

        if (allItems.isEmpty()) {
            EmptyState(
                title = "衣橱还空着",
                hint = "先添加几件衣物，回来滑动组合穿搭",
                actionLabel = "＋ 添加衣物",
                onAction = onAddItem,
            )
        } else {
            // it-008 真人比例布局：头小 / 上身行全宽三卡 / 腿窄长（两侧挂件利用留白）/ 脚小扁
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 头：帽子（小卡居中）
                OutfitSlot(WardrobeCategory.HAT, catItems, pagerStates, vm, onOpenItem, onAddItem,
                    Modifier.fillMaxWidth(0.34f), aspect = 1f, coach = coachPhase)
                Spacer(Modifier.height(8.dp))
                // 上身行：外套 | 上装 | 连衣裙 全宽三等分（it-008：不再被挂件挤占）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutfitSlot(WardrobeCategory.OUTERWEAR, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(1f), aspect = 0.78f, coach = coachPhase)
                    OutfitSlot(WardrobeCategory.TOP, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(1f), aspect = 0.78f, coach = coachPhase)
                    OutfitSlot(WardrobeCategory.DRESS, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(1f), aspect = 0.78f, coach = coachPhase)
                }
                Spacer(Modifier.height(8.dp))
                // 腿行：包(左挂) | 下装（窄长，真人腿型） | 配饰(右挂)——挂件利用腿两侧留白
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutfitSlot(WardrobeCategory.BAG, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(0.5f), aspect = 0.85f, coach = coachPhase)
                    OutfitSlot(WardrobeCategory.BOTTOM, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(1.12f), aspect = 0.6f, coach = coachPhase)
                    OutfitSlot(WardrobeCategory.ACCESSORY, catItems, pagerStates, vm, onOpenItem, onAddItem,
                        Modifier.weight(0.5f), aspect = 0.85f, coach = coachPhase)
                }
                Spacer(Modifier.height(8.dp))
                // 脚：鞋（小扁居中）
                OutfitSlot(WardrobeCategory.SHOES, catItems, pagerStates, vm, onOpenItem, onAddItem,
                    Modifier.fillMaxWidth(0.58f), aspect = 2.6f, coach = coachPhase)
            }
        }

        // 底部常驻：复制长图（主）+ ☆ 保存这套（次）（it-011 O6 按钮主次）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { exportItems = currentItemsFromMemory },
                enabled = allItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("📋 复制长图", style = MaterialTheme.typography.titleSmall) }
            OutlinedButton(
                onClick = {
                    val outfit = savedOutfit
                    if (outfit != null) {
                        onOpenOutfit(outfit.id)
                    } else {
                        vm.saveOutfitDedup(currentIdsFromMemory)
                    }
                },
                enabled = allItems.isNotEmpty(),
            ) {
                Icon(
                    if (savedOutfit != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = editorialColors().accent,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (savedOutfit != null) "已保存" else "保存这套")
            }
        }
    }

    if (showPersonSheet) {
        PersonSheet(vm = vm, onDismiss = { showPersonSheet = false })
    }
    exportItems?.let { items ->
        ExportSheet(
            vm = vm,
            items = items,
            existingOutfit = null,
            onDismiss = { exportItems = null },
        )
    }
}

/** pager 未就绪时的占位（空品类不会用到 pager，仅为非空类型兜底） */
@Composable
private fun PlaceholderPager(): PagerState = rememberPagerState(pageCount = { 0 })

/** 人体布局的着装位卡片（it-005；it-011 O6 透传 coach） */
@Composable
private fun OutfitSlot(
    category: WardrobeCategory,
    catItems: Map<WardrobeCategory, List<Item>>,
    pagerStates: Map<WardrobeCategory, PagerState>,
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 0.8f,
    coach: Boolean = false,
) {
    SlotCell(
        category = category,
        items = catItems[category].orEmpty(),
        pagerState = pagerStates[category] ?: PlaceholderPager(),
        imageFileOf = vm::imageFileOf,
        onCardTap = { onOpenItem(it.id) },
        onAddEmpty = onAddItem,
        modifier = modifier,
        aspect = aspect,
        coach = coach,
    )
}

