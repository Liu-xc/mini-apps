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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.leo.wardrobe.ui.components.StaggeredEntrance
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.leo.wardrobe.ui.components.rememberHaptics
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
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val personId = person?.id
    val outfits = remember(personId, data) { if (personId != null) data.outfitsOf(personId) else emptyList() }
    val filtered = remember(outfits, filterTag) {
        if (filterTag == null) outfits else outfits.filter { filterTag!! in it.tags }
    }
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
            RandomButton(
                deck = deck,
                hasItems = filtered.isNotEmpty(),
                onClick = {
                    val c = deck ?: return@RandomButton
                    if (c.isDrawing) return@RandomButton // 双击窗口内第二次点击：不再起新抽取
                    scope.launch {
                        val drew = c.drawRandom()
                        // it-047 #5：真实抽取后才 Confirm（size≤1 直接返回不算抽中落定；
                        // null=并发幂等门拦截，避免双震）
                        if (drew != null && c.size > 1) haptics.confirm()
                    }
                },
            )
        }

        if (tags.isNotEmpty()) {
            FilterChipsRow(
                options = tags,
                selected = filterTag,
                onSelect = { filterTag = it },
                // it-045：顶栏下缘→内容 20dp 全站节奏（标题行底 6 + 14）
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
        }

        when {
            outfits.isEmpty() -> EmptyState(
                title = "还没有穿搭记录",
                hint = "在搭配页「保存这套」，或生成效果图后「录入成品图」，就会出现在这里",
            )
            filtered.isEmpty() -> EmptyState(
                title = "该标签下没有穿搭",
                hint = "换一个标签，或清除筛选",
            )
            else -> {
                // ---- 卡组：快速浏览 + 随机翻（it-007/it-010；it-011 增 ‹n/m› 卡序） ----
                // it-031 C6：翻页器移出拼贴区放卡下方居中（审查 P0：胶囊浮层压住帽行）；
                // it-033：胶囊整体保持外置居中不回退，但拆左右两半边独立翻页热区
                // ‹=上一张、›=下一张，各 48×48dp（≥44dp 基线），中心 n/m 纯展示
                Column(
                    Modifier
                        .fillMaxWidth()
                        // it-064 修2b：top 14→20——卡组不可裁剪（it-048 甩卡真实飞行），
                        // 与筛选行之间留缓冲带，飞行溢出不再直接压住 tag 行
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                ) {
                    // it-048：不得加 clipToBounds——甩卡是真实飞行（it-047 全路径），裁剪会把
                    // 卡片在容器边距处切掉（it-031 旧库时代的包裹已随自研内核删除）
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val controller = CardDeck(
                            items = filtered,
                            modifier = Modifier
                                .fillMaxWidth()
                                // it-064 修2b②：层叠几何 base(k)=(s·(V-1-k), -s·(V-1-k))——
                                // 顶卡向右上各偏 V-1=2 层 ×14dp=28dp（右贴屏边、上遮筛选行的根因）。
                                // 容器不可裁剪（it-048），改为内缩起点：start 28dp 把整组左移抵消右偏、
                                // top 8dp + 容器 top 20dp ≥ 上探 28dp，层叠保留、越界归零
                                .padding(start = 28.dp, top = 8.dp)
                                // it-064 修2b：380→368——补偿 top 缓冲增量，整页高度不涨
                                .height(368.dp),
                            // it-047：样式与弹簧全部走 DeckStyle 默认（14dp 层叠 / 6dp 内衬 /
                            // flyOutSpec = it-046 基准 spring(0.9,500)），不再引用三方库类型
                        ) { outfit ->
                            OutfitDeckCard(vm, outfit) {
                                // it-047 #8③：抽取进行中卡面点击忽略
                                if (deck?.isDrawing != true) onOpenOutfit(outfit.id)
                            }
                        }
                        deck = controller
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // it-033：左半边 = 上一张（48×48dp 热区）
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clickable { deck?.previous() }
                                        .semantics { contentDescription = "上一张" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "‹",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = editorialColors().ink,
                                    )
                                }
                                DeckCounter(
                                    deck = deck,
                                    total = filtered.size,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                // it-033：右半边 = 下一张（48×48dp 热区）
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clickable { deck?.next() }
                                        .semantics { contentDescription = "下一张" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "›",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = editorialColors().ink,
                                    )
                                }
                            }
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
                // it-028：首屏瀑布入场（specs/05 #7），仅首进播放（DESIGN.md §3 预算）
                var entranceDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(900)
                    entranceDone = true
                }
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
                    itemsIndexed(filtered, key = { _, it -> it.id }) { index, outfit ->
                        StaggeredEntrance(index = index, animate = !entranceDone) {
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
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * ‹ n/m › 计数（it-047 #10 白名单：currentIndex 读取下沉到本独立组合——
 * 重组只波及胶囊自身，不回流 RecordsScreen 体级/网格）。
 */
@Composable
private fun DeckCounter(
    deck: CardDeckController<Outfit>?,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val idx = (deck?.currentIndex ?: 0).coerceIn(0, (total - 1).coerceAtLeast(0))
    Text(
        "${idx + 1}/$total",
        style = MaterialTheme.typography.labelLarge,
        color = editorialColors().ink,
        modifier = modifier,
    )
}

/**
 * 「随机一套」按钮（it-047 #9：抽取状态读取收口在本组件内——
 * drawRandom 全程不触发 RecordsScreen 体级重组，只重绘按钮自身）。
 */
@Composable
private fun RandomButton(
    deck: CardDeckController<Outfit>?,
    hasItems: Boolean,
    onClick: () -> Unit,
) {
    val drawing = deck?.isDrawing == true
    Button(
        onClick = onClick,
        enabled = deck != null && !drawing && hasItems,
    ) {
        Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        // it-036 C12：文案与 W1 统一为「随机一套」（it-011 R3-P1 的两套文案废止），图标沿用
        Text("随机一套")
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
                    // 用户导入的成品穿搭图：衬纸 Fit 完整展示（it-042 C2——原全幅 Crop 在宽盒里
                    // 把竖图人物头部裁掉，与同页网格缩略（0.86 近原比）两种呈现打架；it-011 C5 同语言）
                    // it-064 修2a：撤销 it-058 C2 的 hero 共享元素——卡组高频切换时 SharedTransition
                    // 的 bounds 跟踪与甩卡飞行互相干扰（实测切换不丝滑的根因），恢复整页转场
                    com.leo.wardrobe.ui.components.PhotoCard(
                        file = vm.imageFileOf(effect.file),
                        contentDescription = "穿搭成品图",
                        corner = 0.dp,
                        mat = true,
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

