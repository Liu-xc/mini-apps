package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
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
import com.leo.wardrobe.ui.components.SlotCarousel
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * W1 搭配页（首页）：8 品类槽位轮播 + 🎲 老虎机随机 + 组合记忆（US-04/05/06）。
 */
@Composable
fun OutfitScreen(
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
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

    // pager 状态提升到页面级，🎲 与导出都需要读取当前选中
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
            // 组合记忆（US-06）：先等待 DataStore 首值并 snap 恢复，再开始持久化翻页——
            // 顺序执行消除竞态（否则 page0 的首次持久化会覆盖记忆）
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

    /** 当前各槽位选中（导出/收藏用） */
    fun currentSelection(): List<Item> = WardrobeCategory.entries.mapNotNull { c ->
        val items = catItems[c].orEmpty()
        val state = pagerStates[c]
        if (items.isEmpty() || state == null) null
        else items.getOrNull(state.currentPage)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：角色名（衬线大字）+ 随机一套
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
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
                    // 🎲 老虎机：各非空槽位连滚，100ms stagger 依次停下（US-05）
                    scope.launch {
                        val rolls = pagerStates.entries.toList()
                            .sortedBy { it.key.ordinal }
                            .filter { it.value.pageCount > 1 }
                        rolls.forEachIndexed { index, (_, state) ->
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
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                WardrobeCategory.entries.forEach { category ->
                    item(key = category.name) {
                        SlotCarousel(
                            category = category,
                            items = catItems[category].orEmpty(),
                            pagerState = pagerStates[category] ?: return@item,
                            imageFileOf = vm::imageFileOf,
                            onCardTap = { onOpenItem(it.id) },
                            onAddEmpty = onAddItem,
                        )
                    }
                }
            }
        }

        // 底部常驻：复制图片+文本 / 分享（W6 入口）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { exportItems = currentSelection() },
                enabled = allItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("📋 复制图片+文本") }
            OutlinedButton(
                onClick = { exportItems = currentSelection() },
                enabled = allItems.isNotEmpty(),
            ) { Text("↗") }
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
