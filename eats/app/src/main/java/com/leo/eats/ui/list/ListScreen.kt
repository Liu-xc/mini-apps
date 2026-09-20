package com.leo.eats.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.eats.BuildConfig
import com.leo.eats.data.mock.DemoMode
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.domain.model.statsOfAll
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.EmptyState
import com.leo.eats.ui.components.FilterChipsRow
import com.leo.eats.ui.components.KindChip
import com.leo.eats.ui.components.KindPlaceholder
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.RelativeTimeText
import com.leo.eats.ui.components.TagRow
import com.leo.eats.ui.components.label
import com.leo.eats.ui.components.relativeTimeText
import com.leo.eats.ui.components.sharedPhoto
import com.leo.eats.ui.theme.menuColors
import com.leo.eats.ui.visit.LogVisitSheet
import java.io.File

/** 列表排序（US-05） */
enum class ListSort(val label: String) {
    LAST("最近一次吃"),
    RATING("评分"),
    COUNT("累计次数"),
    NAME("名称"),
}

/** 类型筛选项：全部 / 三类型 / 未定位（W2「未上地图」入口落点） */
private sealed interface KindFilter {
    data object All : KindFilter
    data object NoLocation : KindFilter
    data class Of(val kind: PlaceKind) : KindFilter
}

private val kindFilterOptions: List<Pair<KindFilter, String>> = listOf(
    KindFilter.All to "全部",
    KindFilter.Of(PlaceKind.RESTAURANT) to PlaceKind.RESTAURANT.label,
    KindFilter.Of(PlaceKind.TAKEOUT) to PlaceKind.TAKEOUT.label,
    KindFilter.Of(PlaceKind.HOME) to PlaceKind.HOME.label,
    KindFilter.NoLocation to "未定位",
)

/**
 * W3 列表页：搜索 + 类型/标签筛选 + 排序 + FAB。
 * it-002 R1：瀑布入场、滑动删除、行内共享元素、长按快速记一笔。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: AppViewModel,
    onOpenDetail: (String) -> Unit,
    onEditPlace: (String?) -> Unit,
    focusNoLocation: Boolean = false,
    onFocusConsumed: () -> Unit = {},
) {
    val data by vm.data.collectAsState()
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<KindFilter>(KindFilter.All) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(ListSort.LAST) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PlaceWithStats?>(null) }
    var quickLog by remember { mutableStateOf<PlaceWithStats?>(null) }
    // it-006：演示模式入口（仅 DEBUG 构建显示）——同一入口按当前模式进入/退出
    val context = LocalContext.current
    val demoOn = remember { DemoMode.isEnabled(context) }
    var demoAskOpen by remember { mutableStateOf(false) }

    LaunchedEffect(focusNoLocation) {
        if (focusNoLocation) {
            kindFilter = KindFilter.NoLocation
            onFocusConsumed()
        }
    }

    val allTags = remember(data) { data.places.flatMap { it.tags }.distinct().sorted() }
    val activeFilterCount =
        (if (kindFilter != KindFilter.All) 1 else 0) + (if (tagFilter != null) 1 else 0)
    var filterSheetOpen by remember { mutableStateOf(false) }

    val rows = remember(data, query, kindFilter, tagFilter, sort) {
        val filtered = data.statsOfAll().filter { s ->
            val p = s.place
            (query.isBlank() || p.name.contains(query.trim(), true) || p.notes.contains(query.trim(), true) || p.cuisine.contains(query.trim(), true)) &&
                when (val f = kindFilter) {
                    is KindFilter.All -> true
                    is KindFilter.NoLocation -> !p.located
                    is KindFilter.Of -> p.kind == f.kind
                } &&
                (tagFilter == null || tagFilter in p.tags)
        }
        when (sort) {
            ListSort.LAST -> filtered.sortedByDescending { it.lastVisitAt ?: Long.MIN_VALUE }
            ListSort.RATING -> filtered.sortedWith(compareByDescending<PlaceWithStats> { it.place.rating ?: 0 }.thenBy { it.place.name })
            ListSort.COUNT -> filtered.sortedWith(compareByDescending<PlaceWithStats> { it.visitCount }.thenBy { it.place.name })
            ListSort.NAME -> filtered.sortedBy { it.place.name }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditPlace(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("添加食堂") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "食堂 · ${data.places.size} 家",
                    style = MaterialTheme.typography.headlineSmall,
                    color = menuColors().ink,
                )
            }

            // it-004 O5：单行工具条——搜索 / 排序 / 筛选（角标计数），首屏直达卡片列表
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜名称/菜系", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                Box {
                    FilledTonalIconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        ListSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (sort == s) "✓ ${s.label}" else s.label) },
                                onClick = { sort = s; sortMenuOpen = false },
                            )
                        }
                    }
                }
                BadgedBox(badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text("$activeFilterCount") }
                    }
                }) {
                    FilledTonalIconButton(onClick = { filterSheetOpen = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "筛选")
                    }
                }
                if (BuildConfig.DEBUG) {
                    FilledTonalIconButton(onClick = { demoAskOpen = true }) {
                        Icon(Icons.Rounded.Science, contentDescription = "演示数据")
                    }
                }
            }

            if (data.places.isEmpty()) {
                EmptyState(
                    emoji = "🍜",
                    title = "还没有食堂",
                    hint = "把常去的餐厅、点的外卖、拿手的菜加进来",
                    imageRes = com.leo.eats.R.drawable.eats_empty,
                    actionLabel = "添加第一家",
                    onAction = { onEditPlace(null) },
                )
            } else if (rows.isEmpty()) {
                EmptyState(emoji = "🔍", title = "没有匹配的食堂", hint = "换个关键词或放宽筛选试试")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(rows, key = { _, s -> s.place.id }) { index, s ->
                        StaggeredEntrance(index = index) {
                            SwipeToDeleteRow(
                                s = s,
                                fileOf = { vm.imageFileOf(it) },
                                onDelete = { pendingDelete = s },
                                onClick = { onOpenDetail(s.place.id) },
                                onLongClick = { quickLog = s },
                            )
                        }
                    }
                }
            }
        }
    }

    // it-006：演示模式确认——重启进程切换数据源，真实数据零接触
    if (demoAskOpen) {
        AlertDialog(
            onDismissRequest = { demoAskOpen = false },
            title = { Text(if (demoOn) "退出演示模式？" else "进入演示模式？") },
            text = {
                Text(
                    if (demoOn) "返回真实数据，应用将自动重启。"
                    else "切换到内置演示数据（不落盘），用于测试体验与走查；你的真实数据不会受到任何影响。应用将自动重启。",
                )
            },
            confirmButton = {
                TextButton(onClick = { demoAskOpen = false; DemoMode.setAndRestart(context, !demoOn) }) {
                    Text(if (demoOn) "退出演示" else "进入演示")
                }
            },
            dismissButton = {
                TextButton(onClick = { demoAskOpen = false }) { Text("取消") }
            },
        )
    }

    // it-004 O5：筛选弹层——类型（含「未定位」语义归位为状态）/ 标签
    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = menuColors().ink)
                Spacer(Modifier.height(2.dp))
                Text("类型", style = MaterialTheme.typography.labelLarge, color = menuColors().inkFaint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    kindFilterOptions.forEach { (filter, label) ->
                        FilterChip(
                            selected = kindFilter == filter,
                            onClick = { kindFilter = filter },
                            label = { Text(if (filter is KindFilter.NoLocation) "⌖ $label" else label) },
                        )
                    }
                }
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("标签", style = MaterialTheme.typography.labelLarge, color = menuColors().inkFaint)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = tagFilter == null,
                            onClick = { tagFilter = null },
                            label = { Text("全部") },
                        )
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tagFilter == tag,
                                onClick = { tagFilter = if (tagFilter == tag) null else tag },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            kindFilter = KindFilter.All
                            tagFilter = null
                        },
                        enabled = activeFilterCount > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("清除筛选") }
                    Button(onClick = { filterSheetOpen = false }, modifier = Modifier.weight(1f)) { Text("完成") }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.place.name}」？") },
            text = { Text("将一并删除它的全部吃过记录与照片，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlace(target.place.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    quickLog?.let { target ->
        LogVisitSheet(
            vm = vm,
            placeName = target.place.name,
            onLog = { at, rating, cost, text, uris ->
                vm.logVisit(target.place.id, at, rating, cost, text, uris) { }
                quickLog = null
            },
            onDismiss = { quickLog = null },
        )
    }
}

/** 首屏瀑布入场：透明度 + 上移，逐项 24ms 错峰（it-002 R1） */
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(12)) * 24L)
        progress.animateTo(1f, tween(360))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28f
        },
    ) { content() }
}

/** 左滑删除行（对齐 wardrobe W3 交互，it-002 R1） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteRow(
    s: PlaceWithStats,
    fileOf: (String) -> File?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        PlaceRow(s = s, fileOf = fileOf, onClick = onClick, onLongClick = onLongClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(
    s: PlaceWithStats,
    fileOf: (String) -> File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = menuColors().surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .sharedPhoto("place-${s.place.id}"),
            ) {
                val thumb = s.place.photos.firstOrNull()?.let { fileOf(it) }
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = s.place.name,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    KindPlaceholder(kind = s.place.kind, modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.place.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = menuColors().ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChip(s.place.kind, compact = true)
                    if (s.place.cuisine.isNotBlank()) {
                        Text(
                            s.place.cuisine,
                            style = MaterialTheme.typography.labelSmall,
                            color = menuColors().inkFaint,
                        )
                    }
                }
                if (s.place.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TagRow(s.place.tags.take(5))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                RatingStars(rating = s.place.rating, size = 13.dp)
                Spacer(Modifier.height(6.dp))
                // it-002 R3：相对时间与次数合并单行，右列两行统一节奏
                Text(
                    buildString {
                        append(relativeTimeText(s.lastVisitAt))
                        append(if (s.visitCount == 0) " · 还没吃过" else " · ${s.visitCount} 次")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.lastVisitAt == null) menuColors().accent else menuColors().inkFaint,
                )
            }
        }
    }
}
