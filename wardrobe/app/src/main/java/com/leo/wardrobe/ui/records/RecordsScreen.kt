package com.leo.wardrobe.ui.records

import androidx.compose.foundation.clickable
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
                // it-011 R3-P1：此页是随机「翻看」已有记录，与 W1 随机生成搭配区分
                Text("随机翻一套")
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
                // ---- 卡组：快速浏览 + 随机翻（it-007/it-010；it-011 增 ‹n/m› 卡序） ----
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
                    // it-011 C1：卡序常驻，可滑动可视；it-015 修订二：两侧 ‹ › 双向循环翻张
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                        ) {
                            Text(
                                "‹",
                                style = MaterialTheme.typography.titleMedium,
                                color = editorialColors().ink,
                                modifier = Modifier
                                    .clickable { controller.previous() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Text(
                                "‹ ${(deck?.currentIndex ?: 0).coerceIn(0, filtered.lastIndex) + 1}/${filtered.size} ›",
                                style = MaterialTheme.typography.labelLarge,
                                color = editorialColors().ink,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                        ) {
                            Text(
                                "›",
                                style = MaterialTheme.typography.titleMedium,
                                color = editorialColors().ink,
                                modifier = Modifier
                                    .clickable { controller.next() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
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
 * 卡组穿搭卡（it-010 成品图优先；it-011 O7 人体叙事拼贴 + 缺失空槽）。
 */
@Composable
private fun OutfitDeckCard(vm: AppViewModel, outfit: Outfit, onOpen: () -> Unit) {
    val data by vm.data.collectAsState()
    val items = remember(data, outfit) { outfit.itemIds.mapNotNull { data.itemById(it) } }
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
                    // 人体叙事拼贴：淡色人形轮廓底 + 缺失品类虚线空槽（it-011 O7）
                    com.leo.wardrobe.ui.components.BodyCollage(
                        items = items,
                        imageFileOf = vm::imageFileOf,
                        modifier = Modifier.fillMaxSize(),
                    )
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

