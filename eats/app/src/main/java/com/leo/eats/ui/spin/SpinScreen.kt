package com.leo.eats.ui.spin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.ConfettiBurst
import com.leo.eats.ui.components.EmptyState
import com.leo.eats.ui.components.KindChip
import com.leo.eats.ui.components.KindPlaceholder
import com.leo.eats.ui.components.LinkChips
import com.leo.eats.ui.components.TagRow
import com.leo.eats.ui.components.label
import com.leo.eats.ui.components.relativeTimeText
import com.leo.eats.ui.theme.EatsMotion
import com.leo.eats.ui.theme.menuColors
import com.leo.eats.ui.visit.LogVisitSheet
import com.leo.libs.carddeck.CardDeck
import com.leo.libs.carddeck.CardDeckController
import kotlinx.coroutines.launch
import java.io.File

private val RECENT_DAY_OPTIONS = listOf(7, 14, 30)

/**
 * W1 今天吃啥（it-003 卡组；it-004 评审落地）：
 * 类型 chips 常驻 + 忌口/最近排除收进「筛选」弹层（角标计数）；
 * 卡组带 ‹ n/m › 卡序与候选数常驻反馈；抽中后深色结果块覆盖卡组，
 * 「就吃这个/再抽」常驻屏内，落账直达。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpinScreen(
    vm: AppViewModel,
    onOpenDetail: (String) -> Unit,
    onAddPlace: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val config by vm.spinConfig.collectAsState()
    val scope = rememberCoroutineScope()
    var winner by remember { mutableStateOf<PlaceWithStats?>(null) }
    var logTarget by remember { mutableStateOf<PlaceWithStats?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    var deck by remember { mutableStateOf<CardDeckController<PlaceWithStats>?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val candidates = remember(data, config) { vm.candidatesOf(config) }
    val allTags = remember(data) { data.places.flatMap { it.tags }.distinct().sorted() }
    val activeFilterCount = config.excludedTags.size + if (config.excludeRecentDays != null) 1 else 0

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "今天吃啥",
            style = MaterialTheme.typography.displayMedium,
            color = menuColors().ink,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )

        if (data.places.isEmpty()) {
            EmptyState(
                emoji = "🍜",
                title = "还没有食堂",
                hint = "先去列表添加几家，回来抽一张",
                imageRes = com.leo.eats.R.drawable.eats_empty,
                actionLabel = "去添加",
                onAction = onAddPlace,
            )
            return@Column
        }

        // ---- 筛选：类型常驻单行 + 「筛选」收纳忌口/最近排除（it-004） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PlaceKind.entries.forEach { k ->
                val selected = k in config.kinds
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected && config.kinds.size > 1) config.kinds - k else config.kinds + k
                        vm.setSpinKinds(next)
                    },
                    label = { Text(k.label) },
                )
            }
            FilterChip(
                selected = activeFilterCount > 0,
                onClick = { showFilter = true },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(15.dp))
                        Text(
                            if (activeFilterCount > 0) " 筛选 $activeFilterCount" else " 筛选",
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                },
            )
        }

        if (candidates.isEmpty()) {
            Spacer(Modifier.weight(1f))
            EmptyState(emoji = "🔍", title = "过滤后没有可选项", hint = "放宽类型 / 忌口 / 最近排除试试")
            Spacer(Modifier.weight(1f))
        } else {
            // ---- 主区：卡组（常驻组合，保住翻页状态）←→ 抽中结果块覆盖（it-004 E2） ----
            val deckRecede by animateFloatAsState(
                targetValue = if (winner != null) 0.25f else 1f,
                animationSpec = EatsMotion.smooth(),
                label = "deckRecede",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                CardDeck(
                    items = candidates,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = deckRecede
                            scaleX = 0.9f + 0.1f * deckRecede
                            scaleY = 0.9f + 0.1f * deckRecede
                        },
                    properties = com.spartapps.swipeablecards.ui.SwipeableCardsProperties(
                        stackedCardsOffset = 14.dp,
                        padding = 6.dp,
                    ),
                    onSwipe = { _, _ -> winner = null },
                ) { s ->
                    PlaceCard(
                        s = s,
                        fileOf = { vm.imageFileOf(it) },
                        onOpenDetail = { onOpenDetail(s.place.id) },
                        onLog = { logTarget = s },
                        onOpenLink = { url -> vm.linkOpener.open(url) { vm.toast("没有可打开该链接的应用") } },
                    )
                }.also { deck = it }
                ConfettiBurst(
                    trigger = confetti,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(240.dp, 180.dp),
                )

                // ‹ n/m › 卡序 + 候选数常驻（it-004 E1：筛选即反馈、可滑动可视）
                if (winner == null) {
                    val idx = (deck?.currentIndex ?: 0).coerceIn(0, candidates.lastIndex)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = menuColors().surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        Text(
                            "‹ ${idx + 1}/${candidates.size} ›",
                            style = MaterialTheme.typography.labelLarge,
                            color = menuColors().ink,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // ---- 抽中结果块：深色强调 + 按钮常驻屏内（it-004 E2 闭环） ----
            winner?.let { w ->
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = menuColors().ink,
                    border = BorderStroke(2.dp, menuColors().accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
                ) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("今天就吃", style = MaterialTheme.typography.labelLarge, color = menuColors().surface.copy(alpha = 0.75f))
                        Text(
                            w.place.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = menuColors().surface,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Text(
                            buildString {
                                append(w.place.kind.label)
                                if (w.place.cuisine.isNotBlank()) append(" · ${w.place.cuisine}")
                                append(" · 候选 ${candidates.size} 家")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = menuColors().surface.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { logTarget = w },
                                colors = ButtonDefaults.buttonColors(containerColor = menuColors().accent),
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text("✓ 就吃这个", style = MaterialTheme.typography.titleMedium) }
                            OutlinedButton(
                                onClick = {
                                    winner = null
                                    deck?.let { c -> scope.launch { winner = c.drawRandom(); if (winner != null) confetti++ } }
                                },
                                border = BorderStroke(1.dp, menuColors().surface.copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text("再抽", color = menuColors().surface) }
                        }
                    }
                }
            }

            // ---- 动作：随机抽 / 换一张（无抽中结果时常驻底部） ----
            AnimatedVisibility(visible = winner == null, enter = fadeIn(), exit = fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Button(
                        onClick = {
                            val c = deck ?: return@Button
                            winner = null
                            scope.launch {
                                val w = c.drawRandom()
                                if (w != null) {
                                    winner = w
                                    confetti++
                                }
                            }
                        },
                        enabled = deck?.isDrawing != true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (deck?.isDrawing == true) "抽取中…" else "随机抽一张",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    FilledTonalButton(
                        onClick = { deck?.next(); winner = null },
                        enabled = deck?.isDrawing != true,
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("换一张")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- 筛选弹层：忌口标签 + 排除最近吃过的（it-004 从首页收进来） ----
    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = menuColors().ink)
                Text("忌口标签（含任一标签的不进卡组）", style = MaterialTheme.typography.labelLarge, color = menuColors().inkFaint)
                if (allTags.isEmpty()) {
                    Text("还没有标签，添加食堂时可打「忌口 / 风味 / 场景」标签", style = MaterialTheme.typography.bodySmall, color = menuColors().inkFaint)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allTags.forEach { tag ->
                            val selected = tag in config.excludedTags
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) config.excludedTags - tag else config.excludedTags + tag
                                    vm.setSpinExcludedTags(next)
                                },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = config.excludeRecentDays != null,
                        onCheckedChange = { on -> vm.setSpinExcludeRecent(on, config.excludeRecentDays ?: 14) },
                    )
                    Spacer(Modifier.width(8.dp))
                    val days = config.excludeRecentDays
                    if (days != null) {
                        TextButton(onClick = {
                            val next = RECENT_DAY_OPTIONS[(RECENT_DAY_OPTIONS.indexOf(days) + 1) % RECENT_DAY_OPTIONS.size]
                            vm.setSpinExcludeRecent(true, next)
                        }) { Text("排除最近 $days 天吃过的", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        Text("排除最近吃过的", style = MaterialTheme.typography.labelMedium, color = menuColors().inkFaint)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            vm.setSpinExcludedTags(emptySet())
                            vm.setSpinExcludeRecent(false, 14)
                        },
                        enabled = activeFilterCount > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("清除全部") }
                    Button(onClick = { showFilter = false }, modifier = Modifier.weight(1f)) { Text("完成") }
                }
            }
        }
    }

    logTarget?.let { target ->
        LogVisitSheet(
            vm = vm,
            placeName = target.place.name,
            onLog = { at, rating, cost, text, uris ->
                vm.logVisit(target.place.id, at, rating, cost, text, uris) { }
                logTarget = null
                winner = null
            },
            onDismiss = { logTarget = null },
        )
    }
}

/**
 * 食堂信息卡：照片/3D 插画 hero + 名称/类型/菜系 + 评分与上次 + 标签 + 下单链接 + 记一笔。
 */
@Composable
private fun PlaceCard(
    s: PlaceWithStats,
    fileOf: (String) -> File?,
    onOpenDetail: () -> Unit,
    onLog: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = menuColors().surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        Column {
            // hero：照片或 3D 类型插画
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(212.dp),
            ) {
                val photo = s.place.photos.firstOrNull()?.let { fileOf(it) }
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = s.place.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    KindPlaceholder(
                        kind = s.place.kind,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 92.dp,
                    )
                }
                // 顶部右上角：综合评分胶囊（it-004：口径标明「综合」）
                if (s.place.rating != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = menuColors().surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("★", color = androidx.compose.ui.graphics.Color(0xFFE0A93E), style = MaterialTheme.typography.labelLarge)
                            Text(
                                " 综合 ${s.place.rating}",
                                style = MaterialTheme.typography.labelLarge,
                                color = menuColors().ink,
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.place.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = menuColors().ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    KindChip(s.place.kind)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(relativeTimeText(s.lastVisitAt))
                        append(if (s.visitCount == 0) " · 还没吃过" else " · ${s.visitCount} 次")
                        if (s.place.cuisine.isNotBlank()) append(" · ${s.place.cuisine}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = menuColors().inkFaint,
                )
                if (s.place.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TagRow(s.place.tags)
                }
                if (s.place.links.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LinkChips(links = s.place.links, onOpen = onOpenLink)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLog, modifier = Modifier.weight(1f)) { Text("＋ 记一笔") }
                    FilledTonalButton(onClick = onOpenDetail) { Text("详情") }
                }
            }
        }
    }
}
