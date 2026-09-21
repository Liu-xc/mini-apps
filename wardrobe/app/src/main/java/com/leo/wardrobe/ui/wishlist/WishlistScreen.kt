package com.leo.wardrobe.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.wishItemById
import com.leo.wardrobe.domain.model.wishItemsOf
import com.leo.wardrobe.domain.model.wishOutfitsOf
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.outfit.ExportSheet
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * W9 心愿页（it-019）：🌟 想买单品 / 👗 心愿穿搭 两段。
 * 单品段：种草（仅名称+品类必填）→ 浏览 → 「已买到」转正；
 * 穿搭段：心愿组合回看、再次导出长图预览、买齐后一键升级为正式穿搭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit = {},
) {
    val data by vm.data.collectAsState()
    val person by vm.currentPerson.collectAsState()
    var section by remember { mutableStateOf(0) } // 0=想买单品 1=心愿穿搭

    var editTarget by remember { mutableStateOf<WishItem?>(null) }     // null+editOpen=true=新建
    var editOpen by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<WishItem?>(null) }
    var purchaseTarget by remember { mutableStateOf<WishItem?>(null) }
    var outfitDetail by remember { mutableStateOf<WishOutfit?>(null) }
    var exportItems by remember { mutableStateOf<List<Item>?>(null) }
    var deleteWishTarget by remember { mutableStateOf<WishItem?>(null) }
    var deleteOutfitTarget by remember { mutableStateOf<WishOutfit?>(null) }

    val personId = person?.id
    val wishes = if (personId != null) data.wishItemsOf(personId) else emptyList()
    val wishOutfits = if (personId != null) data.wishOutfitsOf(personId) else emptyList()
    val unpurchased = wishes.filter { !it.purchased }
    val purchased = wishes.filter { it.purchased }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("心愿") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            if (section == 0) {
                ExtendedFloatingActionButton(
                    onClick = { editTarget = null; editOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("种草一件") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 分段：想买单品 / 心愿穿搭
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = section == 0,
                    onClick = { section = 0 },
                    label = { Text("🌟 想买单品 ${unpurchased.size}") },
                )
                FilterChip(
                    selected = section == 1,
                    onClick = { section = 1 },
                    label = { Text("👗 心愿穿搭 ${wishOutfits.size}") },
                )
            }

            if (section == 0) {
                WishItemsSection(
                    vm = vm,
                    unpurchased = unpurchased,
                    purchased = purchased,
                    onOpenEdit = { editTarget = it; editOpen = true },
                    onOpenDetail = { detailTarget = it },
                    onOpenItem = onOpenItem,
                )
            } else {
                WishOutfitsSection(
                    vm = vm,
                    outfits = wishOutfits,
                    onOpenDetail = { outfitDetail = it },
                )
            }
        }
    }

    // ---- 种草 / 编辑弹层 ----
    if (editOpen) {
        WishEditSheet(
            vm = vm,
            existing = editTarget,
            onDismiss = { editOpen = false },
        )
    }

    // ---- 单品详情弹层 ----
    detailTarget?.let { wish ->
        WishDetailSheet(
            vm = vm,
            wish = wish,
            onEdit = { detailTarget = null; editTarget = wish; editOpen = true },
            onDelete = { deleteWishTarget = wish },
            onPurchase = { purchaseTarget = wish; detailTarget = null },
            onDismiss = { detailTarget = null },
        )
    }

    // ---- 转正表单 ----
    purchaseTarget?.let { wish ->
        PurchaseSheet(
            vm = vm,
            wish = wish,
            onDone = { purchaseTarget = null },
            onDismiss = { purchaseTarget = null },
        )
    }

    // ---- 心愿穿搭详情 ----
    outfitDetail?.let { wishOutfit ->
        WishOutfitDetailSheet(
            vm = vm,
            wishOutfit = wishOutfit,
            onCopyLongImage = { pseudo ->
                outfitDetail = null
                exportItems = pseudo
            },
            onDelete = { deleteOutfitTarget = wishOutfit },
            onDismiss = { outfitDetail = null },
        )
    }

    // ---- 心愿穿搭导出长图（复用 W6 导出面板） ----
    exportItems?.let { items ->
        ExportSheet(
            vm = vm,
            items = items,
            existingOutfit = null,
            refPhotoFile = person?.refImageFile,
            onDismiss = { exportItems = null },
        )
    }

    deleteWishTarget?.let { wish ->
        AlertDialog(
            onDismissRequest = { deleteWishTarget = null },
            title = { Text("删除「${wish.name}」？") },
            text = { Text("商品图一并删除；它在心愿穿搭中的位置将被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWishItem(wish.id)
                    deleteWishTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteWishTarget = null }) { Text("取消") } },
        )
    }

    deleteOutfitTarget?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteOutfitTarget = null },
            title = { Text("删除这套心愿穿搭？") },
            text = { Text("预览图一并删除；单品与愿望不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWishOutfit(w.id)
                    deleteOutfitTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteOutfitTarget = null }) { Text("取消") } },
        )
    }
}

// ---- 单品段 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishItemsSection(
    vm: AppViewModel,
    unpurchased: List<WishItem>,
    purchased: List<WishItem>,
    onOpenEdit: (WishItem) -> Unit,
    onOpenDetail: (WishItem) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    var categoryFilter by remember { mutableStateOf<WardrobeCategory?>(null) }
    var purchasedOpen by remember { mutableStateOf(false) }
    val filtered = if (categoryFilter == null) unpurchased else unpurchased.filter { it.category == categoryFilter }
    val grouped = filtered.groupBy { it.category }

    if (unpurchased.isEmpty() && purchased.isEmpty()) {
        EmptyState(
            title = "还没有心愿",
            hint = "种草的衣服/鞋/包先放进来，买前混搭预览一下",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("全部") },
                )
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(
                        selected = categoryFilter == c,
                        onClick = { categoryFilter = if (categoryFilter == c) null else c },
                        label = { Text(c.label) },
                    )
                }
            }
        }
        grouped.forEach { (category, items) ->
            item(key = "group-${category.name}") {
                Text(
                    "▍${category.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = editorialColors().ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(items.size, key = { items[it].id }) { i ->
                val w = items[i]
                WishRow(wish = w, fileOf = vm::imageFileOf, onClick = { onOpenDetail(w) })
            }
        }
        if (purchased.isNotEmpty()) {
            item(key = "purchased") {
                Column {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { purchasedOpen = !purchasedOpen }) {
                        Text(if (purchasedOpen) "▾ 已购入 (${purchased.size})" else "▸ 已购入 (${purchased.size})")
                    }
                }
            }
            if (purchasedOpen) {
                items(purchased.size, key = { "p-${purchased[it].id}" }) { i ->
                    val w = purchased[i]
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { w.purchasedItemId?.let(onOpenItem) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text("✓", color = editorialColors().accent)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                w.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = editorialColors().inkFaint,
                                modifier = Modifier.weight(1f),
                            )
                            Text("已在衣橱 →", style = MaterialTheme.typography.labelSmall, color = editorialColors().accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WishRow(wish: WishItem, fileOf: (String) -> File?, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = editorialColors().surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                val file = wish.imageFile?.let(fileOf)
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = wish.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("🌟", style = MaterialTheme.typography.titleMedium)
                        Text(
                            wish.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = editorialColors().inkFaint,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    wish.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = editorialColors().ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (wish.price != null) {
                        Text(
                            "¥${wish.price.toString().removeSuffix(".0")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = editorialColors().accent,
                        )
                    }
                    if (wish.color.isNotBlank()) {
                        Text(wish.color, style = MaterialTheme.typography.labelSmall, color = editorialColors().inkFaint)
                    }
                    if (wish.url.isNotBlank()) {
                        Text(
                            urlHost(wish.url),
                            style = MaterialTheme.typography.labelSmall,
                            color = editorialColors().inkFaint,
                        )
                    }
                }
                if (wish.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TagRow(wish.tags.take(4))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("🌟", style = MaterialTheme.typography.titleSmall)
                Text(
                    wishDaysText(wish.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }
        }
    }
}

// ---- 心愿穿搭段 ----

@Composable
private fun WishOutfitsSection(
    vm: AppViewModel,
    outfits: List<WishOutfit>,
    onOpenDetail: (WishOutfit) -> Unit,
) {
    if (outfits.isEmpty()) {
        EmptyState(
            title = "还没有心愿穿搭",
            hint = "搭配页点 🌟 混入心愿，把想买的和已有的拼成一套存下来",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(outfits.size, key = { outfits[it].id }) { i ->
            val w = outfits[i]
            val wishCount = w.wishItemIds.size
            Surface(
                shape = MaterialTheme.shapes.large,
                color = editorialColors().surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpenDetail(w) },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(editorialColors().accent.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val preview = w.previewImages.firstOrNull()?.let { vm.imageFileOf(it.file) }
                        if (preview != null) {
                            AsyncImage(
                                model = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp),
                            )
                        } else {
                            Text("👗", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${w.itemIds.size + w.wishItemIds.size} 件组合",
                            style = MaterialTheme.typography.titleMedium,
                            color = editorialColors().ink,
                        )
                        Text(
                            "$wishCount 件已有 · ${w.wishItemIds.size} 件想买",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (wishCount == 0) editorialColors().accent else editorialColors().inkFaint,
                        )
                        if (w.tags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            TagRow(w.tags.take(4))
                        }
                    }
                    if (wishCount > 0) {
                        Text(
                            "还差 ${w.wishItemIds.size} 件",
                            style = MaterialTheme.typography.labelMedium,
                            color = editorialColors().inkFaint,
                        )
                    } else {
                        Text("可升级 ✓", style = MaterialTheme.typography.labelMedium, color = editorialColors().accent)
                    }
                }
            }
        }
    }
}

// ---- 弹层们 ----

/** 种草 / 编辑（仅名称+品类必填，it-019 阶段A） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishEditSheet(
    vm: AppViewModel,
    existing: WishItem?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: WardrobeCategory.TOP) }
    var color by remember { mutableStateOf(existing?.color.orEmpty()) }
    var desc by remember { mutableStateOf(existing?.desc.orEmpty()) }
    var price by remember { mutableStateOf(existing?.price?.toString().orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(existing?.tags.orEmpty()) } }
    var photoFile by remember { mutableStateOf(existing?.imageFile) }
    val photoPicker = rememberPhotoPicker { uri -> if (uri != null) vm.importPhoto(uri) { f -> photoFile = f } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("种草一件 🌟", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = { photoPicker() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(88.dp, 66.dp),
                ) {
                    val f = photoFile?.let(vm::imageFileOf)
                    if (f != null) {
                        AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleMedium)
                            Text("商品图(可选)", style = MaterialTheme.typography.labelSmall, color = editorialColors().inkFaint)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("名称 *") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("价格 ¥（可选）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            Text("品类 *", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("颜色（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1.4f),
                    placeholder = { Text("商品链接（可选）") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("描述（可选，拼进生图文案）") },
                singleLine = true,
            )
            Text("标签", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
            TagInput(tags = tags, onChange = { t -> tags.clear(); tags.addAll(t) })
            // it-023：sheet 内 snackbar 会被遮挡——名称为空时置灰保存（校验可见化）
            val haptics = rememberHaptics()  // it-028：保存确认触感（DESIGN.md §4）
            val canSave = name.isNotBlank()
            Button(
                enabled = canSave,
                onClick = {
                    vm.saveWishItem(
                        existing = existing,
                        name = name,
                        category = category,
                        color = color,
                        desc = desc,
                        price = price.toDoubleOrNull(),
                        url = url,
                        tags = tags.toList(),
                        photoFile = photoFile,
                    ) { ok -> if (ok) { haptics.confirm(); onDismiss() } }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (existing == null) "🌟 收进想买" else "保存") }
        }
    }
}

/** 单品详情：大图/信息/链接跳转/已买到 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishDetailSheet(
    vm: AppViewModel,
    wish: WishItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPurchase: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    wish.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = editorialColors().ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "删除") }
            }
            val file = wish.imageFile?.let(vm::imageFileOf)
            if (file != null) {
                PhotoCard(
                    file = file,
                    contentDescription = wish.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wish.price != null) {
                    Text("¥${wish.price.toString().removeSuffix(".0")}", style = MaterialTheme.typography.titleMedium, color = editorialColors().accent)
                }
                Text(wish.category.label, style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
                if (wish.color.isNotBlank()) {
                    Text("· ${wish.color}", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
                }
            }
            if (wish.desc.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(wish.desc, style = MaterialTheme.typography.bodyMedium, color = editorialColors().ink)
            }
            if (wish.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TagRow(wish.tags)
            }
            if (wish.url.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    if (!vm.share.openUrl(wish.url)) vm.toast("没有可打开该链接的应用")
                }) {
                    Text("🔗 ${urlHost(wish.url)} ↗")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "种草于 ${wishDaysText(wish.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = editorialColors().inkFaint,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPurchase,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("✓ 已买到 → 收进衣橱", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** 转正表单：全字段预填，照片可沿用商品图或补拍（it-019 阶段C） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseSheet(
    vm: AppViewModel,
    wish: WishItem,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(wish.name) }
    var category by remember { mutableStateOf(wish.category) }
    var color by remember { mutableStateOf(wish.color) }
    var desc by remember { mutableStateOf(wish.desc) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(wish.tags) } }
    var photoFile by remember { mutableStateOf(wish.imageFile) } // 预填商品图，可替换
    val photoPicker = rememberPhotoPicker { uri -> if (uri != null) vm.importPhoto(uri) { f -> photoFile = f } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("已买到 · 收进衣橱 ✓", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = { photoPicker() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(88.dp, 66.dp),
                ) {
                    val f = photoFile?.let(vm::imageFileOf)
                    if (f != null) {
                        AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleMedium)
                            Text("拍实物", style = MaterialTheme.typography.labelSmall, color = editorialColors().inkFaint)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = color,
                            onValueChange = { color = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("颜色") },
                            singleLine = true,
                        )
                    }
                }
            }
            Text("品类", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("描述") },
                singleLine = true,
            )
            Text("标签", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
            TagInput(tags = tags, onChange = { t -> tags.clear(); tags.addAll(t) })
            // it-023：sheet 内 snackbar 会被遮挡——无实物照时置灰提交（校验可见化）
            val haptics = rememberHaptics()  // it-028：购入转正确认触感（DESIGN.md §4）
            val canPurchase = photoFile != null
            Button(
                enabled = canPurchase,
                onClick = {
                    vm.purchaseWishItem(
                        wishItemId = wish.id,
                        photoFile = photoFile,
                        name = name,
                        category = category,
                        color = color,
                        desc = desc,
                        tags = tags.toList(),
                    ) { ok -> if (ok) { haptics.confirm(); onDone() } }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (canPurchase) "✓ 收进衣橱" else "先拍一张实物照") }
        }
    }
}

/** 心愿穿搭详情：成员/复制长图/去预览/录预览图/一键升级 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishOutfitDetailSheet(
    vm: AppViewModel,
    wishOutfit: WishOutfit,
    onCopyLongImage: (List<Item>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val ownedMembers = wishOutfit.itemIds.mapNotNull { data.itemById(it) }
    val wishMembers = wishOutfit.wishItemIds.mapNotNull { data.wishItemById(it) }
    val readyToPromote = wishMembers.isEmpty() && ownedMembers.isNotEmpty()
    val previewPicker = rememberPhotoPicker { uri -> if (uri != null) vm.importPreviewImage(wishOutfit.id, uri) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "心愿穿搭",
                    style = MaterialTheme.typography.titleLarge,
                    color = editorialColors().ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "删除") }
            }
            // 成员列表
            ownedMembers.forEach { item ->
                MemberRow(label = "▸ ${item.category.label}·${item.name}", badge = "已有", accent = false)
            }
            wishMembers.forEach { w ->
                MemberRow(label = "▸ ${w.category.label}·${w.name}", badge = "🌟 想买", accent = true)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // 组合伪 Item 走 W6 导出面板（长图 + 文案愿望标注）
                        onCopyLongImage(ownedMembers + wishMembers.map { it.asSlotItem() })
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("📋 复制长图") }
                OutlinedButton(
                    onClick = {
                        vm.restoreWishOutfitToSlots(wishOutfit)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("→ 去预览") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { previewPicker() }, modifier = Modifier.weight(1f)) {
                    Text("＋ 录入上身预览图")
                }
                val haptics = rememberHaptics()  // it-028：升级确认触感（DESIGN.md §4）
                Button(
                    onClick = { haptics.confirm(); vm.promoteWishOutfit(wishOutfit.id); onDismiss() },
                    enabled = readyToPromote,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (readyToPromote) "👗 升级为穿搭" else "还差 ${wishMembers.size} 件")
                }
            }
            if (wishOutfit.previewImages.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("上身预览图", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    wishOutfit.previewImages.forEach { img ->
                        val f = vm.imageFileOf(img.file)
                        if (f != null) {
                            AsyncImage(
                                model = f,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(label: String, badge: String, accent: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = editorialColors().ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            badge,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) editorialColors().accent else editorialColors().inkFaint,
        )
    }
}

private fun urlHost(url: String): String =
    runCatching { android.net.Uri.parse(url).host ?: url.take(18) }.getOrDefault(url.take(18))

/** 种草至今天数（it-019） */
private fun wishDaysText(from: Long): String {
    val days = (System.currentTimeMillis() - from) / (24L * 60 * 60 * 1000)
    return if (days <= 0) "今天" else "$days 天前"
}
