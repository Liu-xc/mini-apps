package com.leo.wardrobe.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W3 衣橱页（US-01/02/03/13）：品类分组 + 标签筛选 + 点编辑 + 滑动删除（确认）+ FAB。
 */
@Composable
fun WardrobeScreen(
    vm: AppViewModel,
    onEditItem: (String?) -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    var filterTag by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Item?>(null) }

    val personId = person?.id
    val allItems = if (personId != null) data.itemsOf(personId) else emptyList()
    val filtered = if (filterTag == null) allItems else allItems.filter { filterTag!! in it.tags }
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
                    "衣橱 · ${person?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = editorialColors().ink,
                )
                Text(
                    "共 ${allItems.size} 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }

            if (tags.isNotEmpty()) {
                FilterChipsRow(
                    options = tags,
                    selected = filterTag,
                    onSelect = { filterTag = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            if (allItems.isEmpty()) {
                EmptyState(
                    title = "衣橱还空着",
                    hint = "点右下角 ＋ 拍照录入第一件衣物",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WardrobeCategory.entries.forEach { category ->
                        val catItems = filtered.filter { it.category == category }
                        if (catItems.isEmpty()) return@forEach
                        item(key = "header-${category.name}") {
                            Text(
                                "▍${category.label}（${catItems.size}）",
                                style = MaterialTheme.typography.labelMedium,
                                color = editorialColors().inkFaint,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                            )
                        }
                        catItems.forEach { item ->
                            item(key = item.id) { ItemRow(vm, item, onEdit = { onEditItem(item.id) }, onSwipeDelete = { pendingDelete = item }) }
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

@Composable
private fun ItemRow(
    vm: AppViewModel,
    item: Item,
    onEdit: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                false // 弹确认框，不真正滑走（specs/05 动效#5）
            } else true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onEdit() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            PhotoCard(
                file = vm.imageFileOf(item.imageFile),
                contentDescription = item.name,
                corner = 10.dp,
                modifier = Modifier.size(width = 52.dp, height = 64.dp),
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = editorialColors().ink,
                )
                if (item.color.isNotBlank()) {
                    Text(
                        item.color,
                        style = MaterialTheme.typography.labelSmall,
                        color = editorialColors().inkFaint,
                    )
                }
                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TagRow(item.tags)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }
        }
    }
}
