package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.leo.wardrobe.domain.model.WishOutfit
import com.leo.wardrobe.domain.model.WISH_SLOT_PREFIX
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.wishItemsOf
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.SlotCell
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * W1 搭配页（it-003：一屏 3×3 网格；it-004：底部 ☆ 保存这套 + 去重检测；
 * it-019：🌟 混入心愿——愿望单品以伪 Item 混进槽位做上身预览，含愿望件时按钮变「🌟 存为心愿」）。
 */
@Composable
fun OutfitScreen(
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenOutfit: (String) -> Unit,
    onOpenWishlist: () -> Unit = {},
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    val slotSel by vm.slotSelections.collectAsState()
    val mixWishes by vm.mixWishes.collectAsState()
    val scope = rememberCoroutineScope()
    var showPersonSheet by remember { mutableStateOf(false) }
    var exportItems by remember { mutableStateOf<List<Item>?>(null) }
    // it-015 修订：添加单品弹层（当前待选品类列表；null = 关闭）
    var addSheetCats by remember { mutableStateOf<List<WardrobeCategory>?>(null) }

    val personId = person?.id
    val allItems = if (personId != null) data.itemsOf(personId) else emptyList()
    // it-019：混入开启时，各品类槽位数据源附加未购愿望单品（伪 Item）
    val wishSlotItems: List<Item> = if (mixWishes && personId != null) {
        data.wishItemsOf(personId).filter { !it.purchased }.map { it.asSlotItem() }
    } else {
        emptyList()
    }
    val effectiveItems = allItems + wishSlotItems
    val catItems: Map<WardrobeCategory, List<Item>> = remember(effectiveItems) {
        WardrobeCategory.entries.associateWith { c -> effectiveItems.filter { it.category == c } }
    }

    // 当前组合中的愿望单品（原 WishItem），用于「存为心愿」与已存判定

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
     * it-015 修订（Leo）：组合只含「已加入的品类」——移除即清记忆、加入即写记忆，
     * 不再对未加入品类回退第一件；上身件数由用户增删决定（夏天可只 1 件短袖）。
     */
    val activeCategories: List<WardrobeCategory> = WardrobeCategory.entries.filter {
        slotSel.containsKey(it.name) && catItems[it].orEmpty().isNotEmpty()
    }
    val currentItemsFromMemory: List<Item> = activeCategories.mapNotNull { c ->
        val items = catItems[c].orEmpty()
        items.firstOrNull { it.id == slotSel[c.name] } ?: items.firstOrNull()
    }
    val currentIdsFromMemory = currentItemsFromMemory.map { it.id }
    // it-019：当前组合中的愿望单品（剥前缀即真实 WishItem id）
    val currentWishIds = currentItemsFromMemory
        .filter { it.isWishSlot }
        .map { it.id.removePrefix(WISH_SLOT_PREFIX) }
    val hasWishInMix = currentWishIds.isNotEmpty()
    val savedOutfit = if (personId != null && !hasWishInMix) vm.savedOutfitFor(currentIdsFromMemory) else null
    val savedWishOutfit: WishOutfit? = if (personId != null && hasWishInMix) {
        vm.savedWishOutfitFor(
            currentItemsFromMemory.filter { !it.isWishSlot }.map { it.id },
            currentWishIds,
        )
    } else {
        null
    }

    /** 区内可添加的品类：尚未加入组合、且衣橱里有该品类衣物 */
    fun addableCats(zone: List<WardrobeCategory>): List<WardrobeCategory> =
        zone.filter { it !in activeCategories && catItems[it].orEmpty().isNotEmpty() }

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
                onClick = { vm.setMixWishes(!mixWishes) },
            ) {
                // it-030：🌟 emoji → Material Star/StarBorder（DESIGN.md §5.2）
                Icon(
                    if (mixWishes) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (mixWishes) editorialColors().accent else editorialColors().inkFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "混入心愿",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mixWishes) editorialColors().accent else editorialColors().inkFaint,
                )
            }
            TextButton(
                onClick = {
                    scope.launch {
                        pagerStates.entries.toList()
                            .sortedBy { it.key.ordinal }
                            .filter { it.key in activeCategories && it.value.pageCount > 1 }
                            .forEachIndexed { index, (_, state) ->
                                launch {
                                    delay(index * 100L)
                                    state.animateScrollToPage(Random.nextInt(state.pageCount))
                                }
                            }
                    }
                },
                enabled = effectiveItems.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("随机一套", style = MaterialTheme.typography.titleSmall)
            }
        }

        if (allItems.isEmpty() && wishSlotItems.isEmpty()) {
            EmptyState(
                title = "衣橱还空着",
                hint = "先添加几件衣物，回来滑动组合穿搭",
                actionLabel = "＋ 添加衣物",
                onAction = onAddItem,
            )
        } else {
            // it-015 修订（Leo）：每区只渲染已加入的品类格，件数由用户增删决定——
            // 夏天上身可只 1 件短袖，冬天内搭+外套两件，不再固定每行三格；
            // 区尾「＋」弹层添加品类（写入组合记忆），格底 ✕ 移除（清记忆）
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (activeCategories.isEmpty()) {
                    // 组合为空：引导添加第一件
                    Button(onClick = { addSheetCats = addableCats(WardrobeCategory.entries) }) {
                        Text("＋ 添加单品")
                    }
                } else {
                    // 头区：帽子
                    ZoneRow(
                        zone = listOf(WardrobeCategory.HAT), activeCats = activeCategories,
                        catItems = catItems, pagerStates = pagerStates, vm = vm,
                        onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                        coach = coachPhase,
                        aspectOf = { 1f },
                        onAdd = { zone -> addSheetCats = addableCats(zone) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // 上身区：外套 | 上装 | 连衣裙
                    ZoneRow(
                        zone = listOf(WardrobeCategory.OUTERWEAR, WardrobeCategory.TOP, WardrobeCategory.DRESS),
                        activeCats = activeCategories, catItems = catItems, pagerStates = pagerStates, vm = vm,
                        onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                        coach = coachPhase,
                        aspectOf = { 0.78f },
                        onAdd = { zone -> addSheetCats = addableCats(zone) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // 腿行：包(左挂) | 下装（窄长） | 配饰(右挂)
                    ZoneRow(
                        zone = listOf(WardrobeCategory.BAG, WardrobeCategory.BOTTOM, WardrobeCategory.ACCESSORY),
                        activeCats = activeCategories, catItems = catItems, pagerStates = pagerStates, vm = vm,
                        onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                        coach = coachPhase,
                        aspectOf = { if (it == WardrobeCategory.BOTTOM) 0.6f else 0.85f },
                        onAdd = { zone -> addSheetCats = addableCats(zone) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // 脚区：鞋
                    ZoneRow(
                        zone = listOf(WardrobeCategory.SHOES), activeCats = activeCategories,
                        catItems = catItems, pagerStates = pagerStates, vm = vm,
                        onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                        coach = coachPhase,
                        aspectOf = { 2.6f },
                        onAdd = { zone -> addSheetCats = addableCats(zone) },
                    )
                }
            }
        }

        // 底部常驻：复制长图（主）+ ☆保存这套 / 🌟存为心愿（次，随组合内容切换，it-019）
        val haptics = rememberHaptics()  // it-027：保存成功触感（DESIGN.md §4）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { exportItems = currentItemsFromMemory },
                enabled = effectiveItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("复制长图", style = MaterialTheme.typography.titleSmall)            }
            if (hasWishInMix) {
                OutlinedButton(
                    onClick = {
                        val wish = savedWishOutfit
                        if (wish != null) {
                            onOpenWishlist()
                        } else {
                            vm.saveWishOutfit(
                                currentItemsFromMemory.filter { !it.isWishSlot }.map { it.id },
                                currentWishIds,
                            )
                            haptics.confirm()
                        }
                    },
                    enabled = effectiveItems.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = editorialColors().accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (savedWishOutfit != null) "已存心愿" else "存为心愿",
                        style = MaterialTheme.typography.titleSmall,
                        color = editorialColors().accent,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val outfit = savedOutfit
                        if (outfit != null) {
                            onOpenOutfit(outfit.id)
                        } else {
                            haptics.confirm()
                            vm.saveOutfitDedup(currentIdsFromMemory)
                        }
                    },
                    enabled = effectiveItems.isNotEmpty(),
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
    }

    if (showPersonSheet) {
        PersonSheet(vm = vm, onDismiss = { showPersonSheet = false })
    }
    addSheetCats?.let { cats ->
        AddSlotSheet(
            cats = cats,
            catItems = catItems,
            onPick = { cat ->
                catItems[cat]?.firstOrNull()?.let { vm.setSlot(cat, it.id) }
                addSheetCats = null
            },
            onDismiss = { addSheetCats = null },
        )
    }
    exportItems?.let { items ->
        ExportSheet(
            vm = vm,
            items = items,
            existingOutfit = null,
            refPhotoFile = person?.refImageFile,
            onDismiss = { exportItems = null },
        )
    }
}

/** pager 未就绪时的占位（空品类不会用到 pager，仅为非空类型兜底） */
@Composable
private fun PlaceholderPager(): PagerState = rememberPagerState(pageCount = { 0 })

/** 区行（it-015 修订二）：渲染区内已加入的品类格 + 「＋」添加钮（区内可加品类已尽时隐藏）。
 *  格宽用固定比例（fillMaxWidth 分数）+ Center 排布——激活件数变化时格子大小恒定、整行居中，
 *  不再用 weight 均分（件数少时格子会被拉爆，比例失真）。 */
@Composable
private fun ZoneRow(
    zone: List<WardrobeCategory>,
    activeCats: List<WardrobeCategory>,
    catItems: Map<WardrobeCategory, List<Item>>,
    pagerStates: Map<WardrobeCategory, PagerState>,
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenWishlist: () -> Unit,
    coach: Boolean,
    aspectOf: (WardrobeCategory) -> Float,
    onAdd: (List<WardrobeCategory>) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 基准格宽 = (行宽 - 区内间距) / 3：上身三件时占满，一件时居中且大小恒定
        val cell = (maxWidth - 16.dp) / 3f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            zone.filter { it in activeCats }.forEach { cat ->
                val w = when (cat) {
                    WardrobeCategory.HAT -> cell
                    WardrobeCategory.BOTTOM -> cell * 1.12f
                    WardrobeCategory.BAG, WardrobeCategory.ACCESSORY -> cell * 0.55f
                    WardrobeCategory.SHOES -> cell * 1.25f
                    else -> cell
                }
                OutfitSlot(
                    category = cat,
                    catItems = catItems,
                    pagerStates = pagerStates,
                    vm = vm,
                    onOpenItem = onOpenItem,
                    onAddItem = onAddItem,
                    onOpenWishlist = onOpenWishlist,
                    modifier = Modifier.width(w),
                    aspect = aspectOf(cat),
                    coach = coach,
                    onRemove = { vm.setSlot(cat, null) },
                )
            }
            if (zone.any { it !in activeCats && !catItems[it].orEmpty().isEmpty() }) {
                FilledTonalIconButton(
                    onClick = { onAdd(zone) },
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 6.dp)
                        .size(40.dp),
                ) {
                    Text("＋", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** 「添加单品」弹层（it-015 修订）：列出可加入的品类，点选即加入组合（默认第一件，格内可滑换） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotSheet(
    cats: List<WardrobeCategory>,
    catItems: Map<WardrobeCategory, List<Item>>,
    onPick: (WardrobeCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("添加单品", style = MaterialTheme.typography.titleLarge, color = editorialColors().ink)
            Spacer(Modifier.height(8.dp))
            cats.forEach { cat ->
                val n = catItems[cat]?.size ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(cat) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.label, style = MaterialTheme.typography.titleMedium, color = editorialColors().ink)
                    Spacer(Modifier.weight(1f))
                    Text("$n 件可选", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
                }
                androidx.compose.material3.HorizontalDivider(color = editorialColors().hairline)
            }
        }
    }
}

/** 人体布局的着装位卡片（it-005；it-011 O6 透传 coach；it-019 愿望卡路由；it-015 修订透传移除） */
@Composable
private fun OutfitSlot(
    category: WardrobeCategory,
    catItems: Map<WardrobeCategory, List<Item>>,
    pagerStates: Map<WardrobeCategory, PagerState>,
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenWishlist: () -> Unit = {},
    modifier: Modifier = Modifier,
    aspect: Float = 0.8f,
    coach: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    SlotCell(
        category = category,
        items = catItems[category].orEmpty(),
        pagerState = pagerStates[category] ?: PlaceholderPager(),
        imageFileOf = vm::imageFileOf,
        // it-019：愿望单品卡点击跳心愿页（无详情路由）
        onCardTap = { item -> if (item.isWishSlot) onOpenWishlist() else onOpenItem(item.id) },
        onAddEmpty = onAddItem,
        modifier = modifier,
        aspect = aspect,
        coach = coach,
        onRemove = onRemove,
    )
}

