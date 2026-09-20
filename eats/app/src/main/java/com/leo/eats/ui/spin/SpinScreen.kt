package com.leo.eats.ui.spin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.leo.eats.ui.components.RatingStars
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
 * W1 今天吃啥（it-003：转盘 → 侧滑卡组，libs/carddeck）：
 * 候选食堂做成信息卡（照片/类型/评分/上次/标签/下单链接），左右滑快速浏览，
 * 「随机抽一张」纯随机抽取（无权重），落定彩屑 + 就吃这个直接落账。
 */
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

    val candidates = remember(data, config) { vm.candidatesOf(config) }
    val allTags = remember(data) { data.places.flatMap { it.tags }.distinct().sorted() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // ---- 过滤（沿用 it-002 单行收纳） ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Text("类型", style = MaterialTheme.typography.labelMedium, color = menuColors().inkFaint)
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
            Text(
                "忌口",
                style = MaterialTheme.typography.labelMedium,
                color = menuColors().inkFaint,
                modifier = Modifier.padding(start = 8.dp),
            )
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

        if (candidates.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            EmptyState(emoji = "🔍", title = "过滤后没有可选项", hint = "放宽类型 / 忌口 / 最近排除试试")
        } else {
            Spacer(Modifier.height(10.dp))

            // ---- 卡组（浏览 + 抽取的主角） ----
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val controller = CardDeck(
                    items = candidates,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(472.dp),
                    onSwipe = { _, _ -> winner = null },
                ) { s ->
                    PlaceCard(
                        s = s,
                        fileOf = { vm.imageFileOf(it) },
                        onOpenDetail = { onOpenDetail(s.place.id) },
                        onLog = { logTarget = s },
                        onOpenLink = { url -> vm.linkOpener.open(url) { vm.toast("没有可打开该链接的应用") } },
                    )
                }
                deck = controller
                ConfettiBurst(
                    trigger = confetti,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(240.dp, 180.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- 动作：随机抽 / 换一张 ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

            // ---- 抽中结果条 ----
            val w = winner
            AnimatedVisibility(
                visible = w != null,
                enter = scaleIn(EatsMotion.pop(), initialScale = 0.92f) + fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                if (w != null) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = menuColors().surface,
                        border = androidx.compose.foundation.BorderStroke(2.dp, menuColors().accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "就吃 ${w.place.name}？",
                                style = MaterialTheme.typography.titleMedium,
                                color = menuColors().ink,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { logTarget = w }, modifier = Modifier.weight(1f)) {
                                    Text("✓ 就吃这个")
                                }
                                FilledTonalButton(onClick = {
                                    winner = null
                                    deck?.let { c -> scope.launch { winner = c.drawRandom(); if (winner != null) confetti++ } }
                                }, modifier = Modifier.weight(1f)) { Text("再抽") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
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
        shadowElevation = 4.dp,
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
                // 顶部右上角：评分胶囊
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
                                " ${s.place.rating}",
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
