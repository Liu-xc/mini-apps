package com.leo.wardrobe.ui.wardrobe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.tagsUsedIn
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.FilterChipsRow
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.iconRes
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W3 衣橱页（US-01/02/03/13）：品类分组 + 标签筛选 + 点编辑 + 滑动删除（确认）+ FAB。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    vm: AppViewModel,
    onEditItem: (String?) -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    var filterTag by remember { mutableStateOf<String?>(null) }
    var categoryTab by remember { mutableStateOf<WardrobeCategory?>(null) }
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var filterSheetOpen by remember { mutableStateOf(false) }

    val personId = person?.id
    val allItems = if (personId != null) data.itemsOf(personId) else emptyList()
    val filtered = allItems
        .filter { filterTag == null || filterTag!! in it.tags }
        .filter { categoryTab == null || it.category == categoryTab }
    val tags by remember(allItems, data.outfits) {
        mutableStateOf(if (personId != null) data.tagsUsedIn(personId) else emptyList())
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (categoryTab == null) "衣橱 · ${person?.name ?: ""}" else "${categoryTab!!.label} · ${person?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = editorialColors().ink,
                )
                Text(
                    "共 ${filtered.size} 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }

            // it-009 品类 Tab 行：3D 图标 + 单选筛选；it-011 C6 标签收进「筛选」角标
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = categoryTab == null,
                    onClick = { categoryTab = null },
                    label = { Text("全部") },
                )
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(
                        selected = categoryTab == c,
                        onClick = { categoryTab = if (categoryTab == c) null else c },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(c.iconRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(c.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        },
                    )
                }
                FilterChip(
                    selected = filterTag != null,
                    onClick = { filterSheetOpen = true },
                    label = {
                        Text(if (filterTag != null) "筛选·$filterTag" else "筛选")
                    },
                )
            }

            if (allItems.isEmpty()) {
                EmptyState(
                    title = "衣橱还空着",
                    hint = "点右下角 ＋ 拍照录入第一件衣物",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else if (filtered.isEmpty()) {
                EmptyState(
                    title = "该筛选下没有衣物",
                    hint = "换个品类或标签试试",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                // it-011 C6：两列卡片网格——首屏 4–6 件直达浏览；去行内品类小标，
                // 「全部」下保留品类小节标题；滑动删除改长按删除（网格里滑动会让位滚动）
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val categories = if (categoryTab != null) listOf(categoryTab!!) else WardrobeCategory.entries.toList()
                    categories.forEach { category ->
                        val catItems = filtered.filter { it.category == category }
                        if (catItems.isEmpty()) return@forEach
                        if (categoryTab == null) {
                            item(key = "header-${category.name}", span = { GridItemSpan(2) }) {
                                com.leo.wardrobe.ui.components.CategoryLabel(category, count = catItems.size)
                            }
                        }
                        catItems.forEach { item ->
                            item(key = item.id) {
                                ItemCard(vm, item, onEdit = { onEditItem(item.id) }, onDelete = { pendingDelete = item })
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onEditItem(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = editorialColors().accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "添加衣物")
        }
    }

    // it-011 C6：标签筛选弹层
    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = editorialColors().ink)
                Text("标签", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = filterTag == null,
                        onClick = { filterTag = null },
                        label = { Text("全部") },
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = filterTag == tag,
                            onClick = { filterTag = if (filterTag == tag) null else tag },
                            label = { Text("#$tag") },
                        )
                    }
                }
                Button(
                    onClick = { filterSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("完成") }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("照片与记录将一并删除；它参与过的穿搭会保留（移除该单品）。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(target.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 网格衣物卡（it-011 C6）：衬纸照片 + 名称/标签；点=编辑，长按=删除确认 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    vm: AppViewModel,
    item: Item,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onEdit, onLongClick = onDelete)
            .padding(8.dp),
    ) {
        PhotoCard(
            file = vm.imageFileOf(item.imageFile),
            contentDescription = item.name,
            corner = 10.dp,
            mat = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f),
        )
        Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            color = editorialColors().ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 2.dp)) {
            if (item.color.isNotBlank()) {
                Text(
                    item.color,
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                    maxLines = 1,
                )
            }
            if (item.tags.isNotEmpty()) {
                Text(
                    (if (item.color.isNotBlank()) " · " else "") + item.tags.take(2).joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
