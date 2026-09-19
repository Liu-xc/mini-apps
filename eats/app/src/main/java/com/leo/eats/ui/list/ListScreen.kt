package com.leo.eats.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
import com.leo.eats.ui.theme.menuColors
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
 * W3 列表页（US-05）：搜索 + 类型/标签筛选 + 排序 + FAB。
 * focusNoLocation：由地图页「N 条未上地图」带入的过滤意图。
 */
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

    LaunchedEffect(focusNoLocation) {
        if (focusNoLocation) {
            kindFilter = KindFilter.NoLocation
            onFocusConsumed()
        }
    }

    val allTags = remember(data) {
        data.places.flatMap { it.tags }.distinct().sorted()
    }

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
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("排序: ${sort.label}", style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    ListSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = { sort = s; sortMenuOpen = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("搜索名称 / 菜系 / 笔记", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "类型:",
                    style = MaterialTheme.typography.labelMedium,
                    color = menuColors().inkFaint,
                    modifier = Modifier.padding(start = 20.dp),
                )
                kindFilterOptions.forEach { (filter, label) ->
                    FilterChip(
                        selected = kindFilter == filter,
                        onClick = { kindFilter = filter },
                        label = { Text(label) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (allTags.isNotEmpty()) {
                FilterChipsRow(
                    options = allTags,
                    selected = tagFilter,
                    onSelect = { tagFilter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (data.places.isEmpty()) {
                EmptyState(
                    emoji = "🍜",
                    title = "还没有食堂",
                    hint = "把常去的餐厅、点的外卖、拿手的菜加进来",
                    actionLabel = "添加第一家",
                    onAction = { onEditPlace(null) },
                )
            } else if (rows.isEmpty()) {
                EmptyState(emoji = "🔍", title = "没有匹配的食堂", hint = "换个关键词或放宽筛选试试")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.place.id }) { s ->
                        PlaceRow(s, fileOf = { vm.imageFileOf(it) }, onClick = { onOpenDetail(s.place.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(s: PlaceWithStats, fileOf: (String) -> File?, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = menuColors().surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = s.place.photos.firstOrNull()?.let { fileOf(it) }
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = s.place.name,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                KindPlaceholder(kind = s.place.kind, modifier = Modifier.size(56.dp).padding(4.dp))
            }
            Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(4.dp))
                RelativeTimeText(at = s.lastVisitAt, highlight = s.lastVisitAt == null)
                Text(
                    if (s.visitCount == 0) "还没吃过" else "${s.visitCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = menuColors().inkFaint,
                )
            }
        }
    }
}
