package com.leo.wardrobe.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitsOf
import com.leo.wardrobe.domain.model.tagsUsedIn
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.FilterChipsRow
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.detail.OutfitThumb
import com.leo.wardrobe.ui.theme.editorialColors
import com.leo.libs.carddeck.CardDeck
import com.leo.libs.carddeck.CardDeckController
import kotlinx.coroutines.launch

/**
 * W8 穿搭记录页（it-007：顶部侧滑卡组快速浏览/随机抽一套 + 下方全量网格）。
 */
@Composable
fun RecordsScreen(
    vm: AppViewModel,
    onOpenOutfit: (String) -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    var filterTag by remember { mutableStateOf<String?>(null) }
    var deck by remember { mutableStateOf<CardDeckController<Outfit>?>(null) }
    var drawing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val personId = person?.id
    val outfits = if (personId != null) data.outfitsOf(personId) else emptyList()
    val filtered = if (filterTag == null) outfits else outfits.filter { filterTag!! in it.tags }
    val tags = remember(personId, data) {
        if (personId != null) data.tagsUsedIn(personId) else emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        ) {
            Text(
                "穿搭记录 · ${person?.name ?: ""}",
                style = MaterialTheme.typography.headlineMedium,
                color = editorialColors().ink,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val c = deck ?: return@Button
                    scope.launch { c.drawRandom() }
                },
                enabled = deck != null && !drawing && filtered.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("随机一套")
            }
        }

        if (tags.isNotEmpty()) {
            FilterChipsRow(
                options = tags,
                selected = filterTag,
                onSelect = { filterTag = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        when {
            outfits.isEmpty() -> EmptyState(
                title = "还没有穿搭记录",
                hint = "在搭配页「☆收藏这套」，或生成效果图后「＋录入成品图」，就会出现在这里",
            )
            filtered.isEmpty() -> EmptyState(
                title = "该标签下没有穿搭",
                hint = "换一个标签，或清除筛选",
            )
            else -> {
                // ---- 卡组：快速浏览 + 随机抽（it-007 / it-010 修层级与卡面结构） ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    val controller = CardDeck(
                        items = filtered,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        properties = com.spartapps.swipeablecards.ui.SwipeableCardsProperties(
                            stackedCardsOffset = 14.dp,
                            padding = 6.dp,
                        ),
                    ) { outfit ->
                        OutfitDeckCard(vm, outfit) { onOpenOutfit(outfit.id) }
                    }
                    deck = controller
                    drawing = controller.isDrawing
                }

                // ---- 全量网格（保留总览能力） ----
                Text(
                    "全部 ${filtered.size} 套",
                    style = MaterialTheme.typography.titleSmall,
                    color = editorialColors().inkFaint,
                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
                )
                // 卡组与网格各自滚动会打架：网格用固定高度嵌在整体滚动里
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((filtered.size + 1) / 2 * 300).dp),
                    userScrollEnabled = false,
                ) {
                    items(filtered, key = { it.id }) { outfit ->
                        Column(Modifier.padding(top = 4.dp)) {
                            OutfitThumb(vm, outfit, modifier = Modifier.fillMaxWidth()) {
                                onOpenOutfit(outfit.id)
                            }
                            if (outfit.tags.isNotEmpty()) {
                                Row(Modifier.padding(top = 4.dp)) { TagRow(outfit.tags.take(3)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 卡组穿搭卡（it-010）：成品图优先全幅；否则按真人比例迷你拼贴
 * （帽头/上身行/腿+两侧挂件/鞋），不再 2×2 罗列。
 */
@Composable
private fun OutfitDeckCard(vm: AppViewModel, outfit: Outfit, onOpen: () -> Unit) {
    val data by vm.data.collectAsState()
    val items = remember(data, outfit) { outfit.itemIds.mapNotNull { data.itemById(it) } }
    val byCat = remember(items) { items.groupBy { it.category } }
    val effect = outfit.effectImages.firstOrNull()

    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxSize(),
        onClick = onOpen,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (effect != null) {
                    // 用户导入的成品穿搭图：全幅展示
                    com.leo.wardrobe.ui.components.PhotoCard(
                        file = vm.imageFileOf(effect.file),
                        contentDescription = "穿搭成品图",
                        corner = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MiniBodyCollage(vm, byCat)
                }
            }
            if (outfit.tags.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TagRow(outfit.tags.take(4))
                }
            }
        }
    }
}

/**
 * 迷你真人比例拼贴（it-010 修正）：与 W1 选衣页同构的分段占比布局——
 * 卡内高度按 头/上身/腿/脚 weight 切分（缺失部位自动归一），照片填满各自槽位，
 * 任何卡片尺寸都不再溢出堆叠。
 */
@Composable
private fun MiniBodyCollage(vm: AppViewModel, byCat: Map<com.leo.wardrobe.domain.model.WardrobeCategory, List<com.leo.wardrobe.domain.model.Item>>) {
    val hat = byCat[com.leo.wardrobe.domain.model.WardrobeCategory.HAT]?.firstOrNull()
    val torso = listOf(
        com.leo.wardrobe.domain.model.WardrobeCategory.OUTERWEAR,
        com.leo.wardrobe.domain.model.WardrobeCategory.TOP,
        com.leo.wardrobe.domain.model.WardrobeCategory.DRESS,
    ).mapNotNull { c -> byCat[c]?.firstOrNull() }
    val bottom = byCat[com.leo.wardrobe.domain.model.WardrobeCategory.BOTTOM]?.firstOrNull()
    val bag = byCat[com.leo.wardrobe.domain.model.WardrobeCategory.BAG]?.firstOrNull()
    val acc = byCat[com.leo.wardrobe.domain.model.WardrobeCategory.ACCESSORY]?.firstOrNull()
    val shoes = byCat[com.leo.wardrobe.domain.model.WardrobeCategory.SHOES]?.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 头：帽子（小，居中）
        if (hat != null) {
            Box(Modifier.weight(0.13f).fillMaxWidth()) {
                CollagePhoto(vm, hat, Modifier.fillMaxHeight().fillMaxWidth(0.42f).align(Alignment.Center))
            }
        }
        // 上身行：外套 | 上装 | 连衣裙（存在的品类均分、填满行高）
        if (torso.isNotEmpty()) {
            Row(
                Modifier.weight(0.33f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                torso.forEach { item ->
                    CollagePhoto(vm, item, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        // 腿行：包(矮挂) | 下装（窄长主体） | 配饰(矮挂)
        if (bottom != null || bag != null || acc != null) {
            Row(
                Modifier.weight(0.42f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bag?.let { CollagePhoto(vm, it, Modifier.weight(0.26f).fillMaxHeight(0.62f)) }
                bottom?.let {
                    val w = if (bag == null && acc == null) 1f else 0.48f
                    CollagePhoto(vm, it, Modifier.weight(w).fillMaxHeight())
                }
                acc?.let { CollagePhoto(vm, it, Modifier.weight(0.26f).fillMaxHeight(0.62f)) }
            }
        }
        // 脚：鞋（扁，居中）
        if (shoes != null) {
            Box(Modifier.weight(0.12f).fillMaxWidth()) {
                CollagePhoto(vm, shoes, Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.Center))
            }
        }
    }
}

/**
 * 拼贴槽位（it-010 修正）：ContentScale.Fit 完整展示衣物并按比例缩放，
 * 淡色底槽位承载，不再裁切断衣物轮廓。
 */
@Composable
private fun CollagePhoto(vm: AppViewModel, item: com.leo.wardrobe.domain.model.Item, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(
                model = vm.imageFileOf(item.imageFile),
                contentDescription = item.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
        }
    }
}
