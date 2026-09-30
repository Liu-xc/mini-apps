package com.leo.wardrobe.ui.outfit

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.leo.wardrobe.ui.components.StaggeredEntrance
import com.leo.wardrobe.ui.components.fadingBottomEdge
import com.leo.wardrobe.ui.components.pressScale
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.EditorialMotion
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.Job
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
    // it-066：本会话复制过长图的组合（真实单品 id）——W1 回程提示条的数据源
    val lastExportedIds by vm.lastExportedItemIds.collectAsState()
    val scope = rememberCoroutineScope()
    var showPersonSheet by remember { mutableStateOf(false) }
    var exportItems by remember { mutableStateOf<List<Item>?>(null) }
    // it-077 修订（Leo 反馈×2）：生成入口收进出图面板（ExportSheet），页面按钮不合并堆挤
    var showGenerate by remember { mutableStateOf(false) }
    // it-015 修订：添加单品弹层（当前待选品类列表；null = 关闭）
    var addSheetCats by remember { mutableStateOf<List<WardrobeCategory>?>(null) }

    // it-066：回程提示条的相册入口——录入走 importEffectImage(null=自动建穿搭, 复制时的组合)
    val pickReturnPhoto = rememberPhotoPicker { uri ->
        if (uri != null) {
            val ids = vm.lastExportedItemIds.value
            if (ids != null) vm.importEffectImage(null, ids, uri)
        }
    }

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
    // it-071 P1：推导全部 remember 化——savedOutfitFor/savedWishOutfitFor 是全量扫描，
    // 原先每次体级重组（槽位翻页动画、提示条等）都重跑；键相等即跳过，结构相等级联短路。
    val activeCategories: List<WardrobeCategory> = remember(slotSel, catItems) {
        WardrobeCategory.entries.filter {
            slotSel.containsKey(it.name) && catItems[it].orEmpty().isNotEmpty()
        }
    }
    val currentItemsFromMemory: List<Item> = remember(activeCategories, slotSel, catItems) {
        activeCategories.mapNotNull { c ->
            val items = catItems[c].orEmpty()
            items.firstOrNull { it.id == slotSel[c.name] } ?: items.firstOrNull()
        }
    }
    val currentIdsFromMemory = remember(currentItemsFromMemory) { currentItemsFromMemory.map { it.id } }
    // it-019：当前组合中的愿望单品（剥前缀即真实 WishItem id）
    val currentWishIds = remember(currentItemsFromMemory) {
        currentItemsFromMemory
            .filter { it.isWishSlot }
            .map { it.id.removePrefix(WISH_SLOT_PREFIX) }
    }
    val hasWishInMix = remember(currentWishIds) { currentWishIds.isNotEmpty() }
    val savedOutfit = remember(currentIdsFromMemory, hasWishInMix, personId) {
        if (personId != null && !hasWishInMix) vm.savedOutfitFor(currentIdsFromMemory) else null
    }
    val savedWishOutfit: WishOutfit? = remember(currentItemsFromMemory, currentWishIds, hasWishInMix, personId) {
        if (personId != null && hasWishInMix) {
            vm.savedWishOutfitFor(
                currentItemsFromMemory.filter { !it.isWishSlot }.map { it.id },
                currentWishIds,
            )
        } else {
            null
        }
    }

    // it-036（走查 P2）：混入心愿开关开启、但当前槽位组合里没有任何愿望件——
    // 给一条一次性轻提示（accent 文字小条，Material Star 无 emoji；会话内只出一次，
    // 组合已含愿望件或开关关闭即不显示；仅 UI 提示，不动 it-019 数据流）
    var wishHintShown by remember { mutableStateOf(false) }
    var wishHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(mixWishes, hasWishInMix, wishSlotItems.isEmpty()) {
        if (mixWishes && !hasWishInMix && wishSlotItems.isNotEmpty() && !wishHintShown) {
            wishHintShown = true
            wishHintVisible = true
        }
    }
    LaunchedEffect(wishHintVisible) {
        if (wishHintVisible) {
            delay(5_000)
            wishHintVisible = false
        }
    }
    // it-066：回程提示条在场时让位（两条同屏噪；回程条可操作性更高）
    val showWishHint = wishHintVisible && mixWishes && !hasWishInMix && wishSlotItems.isNotEmpty() &&
        lastExportedIds == null

    /** 区内可添加的品类：尚未加入组合、且衣橱里有该品类衣物 */
    fun addableCats(zone: List<WardrobeCategory>): List<WardrobeCategory> =
        zone.filter { it !in activeCategories && catItems[it].orEmpty().isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：角色名 + 随机一套
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // it-045：三 Tab 标题行距状态栏统一 12dp（W3/W8 同值）
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { showPersonSheet = true },
                // it-069 修4：顶栏 TextButton 触控提到 48dp（it-033 基线）
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
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
                // it-069 修4：顶栏 TextButton 触控提到 48dp（it-033 基线）
                modifier = Modifier.heightIn(min = 48.dp),
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
                    color = if (mixWishes) editorialColors().accentContent else editorialColors().inkFaint,
                )
            }
            // it-058 C5：随机进行中的忙碌态（图标旋转）+ 防连点（进行中忽略点击，
            // 连点会重叠排新一轮 animateScrollToPage，槽位运动互相打断观感撕裂）
            var rolling by remember { mutableStateOf(false) }
            val casinoSpin = rememberInfiniteTransition(label = "casino")
            val casinoAngle by casinoSpin.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
                label = "casino-angle",
            )
            TextButton(
                onClick = {
                    if (rolling) return@TextButton
                    rolling = true
                    scope.launch {
                        try {
                            val rollable = pagerStates.entries.toList()
                                .sortedBy { it.key.ordinal }
                                .filter { it.key in activeCategories && it.value.pageCount > 1 }
                            // it-069 修1：全部品类只有 1 件时不再静默——给轻提示
                            if (rollable.isEmpty()) {
                                vm.toast("每类只有一件，无需随机")
                                return@launch
                            }
                            rollable.forEachIndexed { index, (_, state) ->
                                launch {
                                    delay(index * 100L)
                                    // it-047 #9：跳页弹簧显式收敛进 EditorialMotion（05 #2 老虎机）
                                    state.animateScrollToPage(
                                        Random.nextInt(state.pageCount),
                                        animationSpec = EditorialMotion.smooth(),
                                    )
                                }
                            }
                        } finally {
                            // 略过尾停顿（最后槽落定即止），节奏与老虎机一致
                            rolling = false
                        }
                    }
                },
                enabled = effectiveItems.isNotEmpty(),
                // it-069 修4：顶栏 TextButton 触控提到 48dp（it-033 基线）
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    Icons.Rounded.Casino,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (rolling) casinoAngle else 0f),
                )
                Spacer(Modifier.width(6.dp))
                Text("随机一套", style = MaterialTheme.typography.titleSmall)
            }
        }

        // it-036（走查 P2）：顶栏开关下方的轻提示——「愿望件已附加，滑到候选最后可见」
        AnimatedVisibility(visible = showWishHint) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = editorialColors().accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "愿望件已附加，滑到候选最后可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().accentContent,
                )
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
            val slotScroll = rememberScrollState()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(slotScroll)
                    // it-042 C8：行卡视口底缘渐隐（缓解拦腰裁切，下方还有内容的提示）
                    .fadingBottomEdge(active = { slotScroll.value < slotScroll.maxValue })
                    // it-064 修1：top 16→12——四区一屏预算（鞋槽初始完整可见，Leo 实测需上滚）
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (activeCategories.isEmpty()) {
                    // 组合为空：引导添加第一件
                    Button(onClick = { addSheetCats = addableCats(WardrobeCategory.entries) }) {
                        Text("＋ 添加单品")
                    }
                } else {
                    // it-058 C7：四分区首进错峰入场（对齐 W3/W8 的 StaggeredEntrance 惯例；
                    // rememberSaveable 一次性，Tab 往返不重播——DESIGN.md §3 二次进入走快路径）
                    var entranceDone by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) { entranceDone = true }
                    // 头区：帽子
                    StaggeredEntrance(index = 0, animate = !entranceDone) {
                        ZoneRow(
                            zone = listOf(WardrobeCategory.HAT), activeCats = activeCategories,
                            catItems = catItems, pagerStates = pagerStates, vm = vm,
                            onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                            coach = coachPhase,
                            aspectOf = { 1.03f }, // it-064 修1：1.0→1.03 一屏预算微收
                            onAdd = { zone -> addSheetCats = addableCats(zone) },
                        )
                    }
                    Spacer(Modifier.height(6.dp)) // it-064 修1：分区间 8→6
                    // 上身区：外套 | 上装 | 连衣裙
                    StaggeredEntrance(index = 1, animate = !entranceDone) {
                        ZoneRow(
                            zone = listOf(WardrobeCategory.OUTERWEAR, WardrobeCategory.TOP, WardrobeCategory.DRESS),
                            activeCats = activeCategories, catItems = catItems, pagerStates = pagerStates, vm = vm,
                            onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                            coach = coachPhase,
                            aspectOf = { 0.8f }, // it-064 修1：0.78→0.80 一屏预算微收
                            onAdd = { zone -> addSheetCats = addableCats(zone) },
                        )
                    }
                    Spacer(Modifier.height(6.dp)) // it-064 修1：分区间 8→6
                    // 腿行：包(左挂) | 下装（窄长） | 配饰(右挂)
                    StaggeredEntrance(index = 2, animate = !entranceDone) {
                        ZoneRow(
                            zone = listOf(WardrobeCategory.BAG, WardrobeCategory.BOTTOM, WardrobeCategory.ACCESSORY),
                            activeCats = activeCategories, catItems = catItems, pagerStates = pagerStates, vm = vm,
                            onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                            coach = coachPhase,
                            aspectOf = { if (it == WardrobeCategory.BOTTOM) 0.63f else 0.85f }, // it-064 修1：0.6→0.63 一屏预算微收
                            onAdd = { zone -> addSheetCats = addableCats(zone) },
                        )
                    }
                    Spacer(Modifier.height(6.dp)) // it-064 修1：分区间 8→6
                    // 脚区：鞋
                    StaggeredEntrance(index = 3, animate = !entranceDone) {
                        ZoneRow(
                            zone = listOf(WardrobeCategory.SHOES), activeCats = activeCategories,
                            catItems = catItems, pagerStates = pagerStates, vm = vm,
                            onOpenItem = onOpenItem, onAddItem = onAddItem, onOpenWishlist = onOpenWishlist,
                            coach = coachPhase,
                            // it-061 修1 2.6→2.2（Trim 后贴内容比例）；it-064 修1 2.2→2.3（一屏预算微收）
                        aspectOf = { 2.3f },
                            onAdd = { zone -> addSheetCats = addableCats(zone) },
                        )
                    }
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
                // it-069 修1：组合为空即禁用（防打开空面板/保存空穿搭）
                // it-077 修订（Leo）：更名「生成预览穿搭」——入口即出图工作台（AI 生成/存相册/分享）
                enabled = currentItemsFromMemory.isNotEmpty(),
                // it-058 C3：主 CTA 按压反馈
                modifier = Modifier.weight(1f).pressScale(0.96f),
            ) {
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("生成预览穿搭", style = MaterialTheme.typography.titleSmall)
            }
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
                    // it-069 修1：同上——组合为空即禁用
                    enabled = currentItemsFromMemory.isNotEmpty(),
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
                        color = editorialColors().accentContent,
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
                    // it-069 修1：同上——组合为空即禁用（防创建空穿搭）
                    enabled = currentItemsFromMemory.isNotEmpty(),
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

    if (showGenerate) {
        com.leo.wardrobe.ui.records.OutfitGenerateSheet(
            vm = vm,
            outfit = savedOutfit,
            items = currentItemsFromMemory,
            person = vm.currentPerson.collectAsState().value,
            personNote = vm.personNote.collectAsState().value,
            onDismiss = { showGenerate = false },
        )
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
            // it-077：面板内直生成（关闭本面板 → 打开生成 sheet；未保存组合保存时自动建穿搭）
            onGenerate = {
                exportItems = null
                showGenerate = true
            },
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
        // 基准格宽 = (行宽 - 两个 12dp 格间距) / 3：上身三件精确占满，一件时居中且大小恒定
        // it-031 C5：格间距 12dp（审查：相邻格名称条连成深色长带）
        // it-031 C5 rev2：仅小格 0.55→0.85（按审查线框补宽）——原 61dp 物理塞不下
        // 「完整名称+角标+✕」，违「杜绝截断」验收；帽/下/鞋维持原比例，不动一屏高度
        val cell = (maxWidth - 24.dp) / 3f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            zone.filter { it in activeCats }.forEach { cat ->
                val w = when (cat) {
                    WardrobeCategory.HAT -> cell
                    WardrobeCategory.BOTTOM -> cell * 1.12f
                    WardrobeCategory.BAG, WardrobeCategory.ACCESSORY -> cell * 0.85f
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
            Spacer(Modifier.height(6.dp)) // it-064 修1：分区间 8→6
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
        // it-072：长按已内建为打开品类清单（SlotCell），原 toast 读全名路径废止（US-39 修订）
    )
}
