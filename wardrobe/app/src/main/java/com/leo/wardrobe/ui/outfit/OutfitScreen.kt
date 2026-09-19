package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
 * W1 搭配页（it-003：一屏 3×3 网格，一套尽收 + 格内滑动换衣 + 🎲 + 组合记忆）。
 */
@Composable
fun OutfitScreen(
    vm: AppViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    val scope = rememberCoroutineScope()
    var showPersonSheet by remember { mutableStateOf(false) }
    var exportItems by remember { mutableStateOf<List<Item>?>(null) }

    val personId = person?.id
    val allItems = if (personId != null) data.itemsOf(personId) else emptyList()
    val catItems: Map<WardrobeCategory, List<Item>> = remember(allItems) {
        WardrobeCategory.entries.associateWith { c -> allItems.filter { it.category == c } }
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
            // 3×3 一屏网格（8 品类格 + 第 9 格添加）；极小屏时允许滚动兜底
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                (0..2).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        (0..2).forEach { col ->
                            val index = row * 3 + col
                            when {
                                index < WardrobeCategory.entries.size -> {
                                    val category = WardrobeCategory.entries[index]
                                    SlotCell(
                                        category = category,
                                        items = catItems[category].orEmpty(),
                                        pagerState = pagerStates[category] ?: PlaceholderPager(),
                                        imageFileOf = vm::imageFileOf,
                                        onCardTap = { onOpenItem(it.id) },
                                        onAddEmpty = onAddItem,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                index == WardrobeCategory.entries.size -> AddCell(
                                    onAdd = onAddItem,
                                    modifier = Modifier.weight(1f),
                                )
                                else -> Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 底部常驻：复制长图 / 分享（W6 入口）
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
            ) { Text("📋 复制长图") }
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

/** pager 未就绪时的占位（空品类不会用到 pager，仅为非空类型兜底） */
@Composable
private fun PlaceholderPager(): PagerState = rememberPagerState(pageCount = { 0 })

/** 第 9 格：＋ 添加衣物 */
@Composable
private fun AddCell(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                editorialColors().hairline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = editorialColors().accent,
                )
                Text(
                    "添加衣物",
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }
        }
        Spacer(Modifier.padding(top = 5.dp))
        Text("", style = MaterialTheme.typography.labelMedium)
    }
}
