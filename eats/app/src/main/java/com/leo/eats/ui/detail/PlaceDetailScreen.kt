package com.leo.eats.ui.detail

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.eats.domain.model.Visit
import com.leo.eats.domain.model.statsOf
import com.leo.eats.domain.model.visitsOf
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.KindChip
import com.leo.eats.ui.components.KindPlaceholder
import com.leo.eats.ui.components.LinkChips
import com.leo.eats.ui.components.PhotoStrip
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.RelativeTimeText
import com.leo.eats.ui.components.TagRow
import com.leo.eats.ui.components.formatVisitTime
import com.leo.eats.ui.components.sharedPhoto
import com.leo.eats.ui.theme.menuColors
import com.leo.eats.ui.visit.LogVisitSheet
import java.io.File

/**
 * W5 详情（US-06）：基本信息 + 派生统计 + 照片 + 链接跳转 + Visit 时间线（倒序可删）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    vm: AppViewModel,
    placeId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onShowOnMap: (String) -> Unit,
) {
    val data by vm.data.collectAsState()
    val stats = remember(data, placeId) { data.statsOf(placeId) }
    val visits = remember(data, placeId) { data.visitsOf(placeId) }
    val place = stats.place

    var showLogVisit by remember { mutableStateOf(false) }
    var deleteVisitTarget by remember { mutableStateOf<Visit?>(null) }
    var showPlanPicker by remember { mutableStateOf(false) }

    LaunchedEffect(data, placeId) {
        if (data.places.none { it.id == placeId }) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(place.id) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                    }
                },
            )
        },
        // it-004 O4：高频动作吸底常驻，不再被记录列表埋没；文案随分类（it-008）
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { showLogVisit = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .height(52.dp),
                ) {
                    Text(
                        when (place.category) {
                            com.leo.eats.domain.model.PlaceCategory.EAT -> "＋ 记一笔今天吃了"
                            com.leo.eats.domain.model.PlaceCategory.DRINK -> "＋ 记一笔今天喝了"
                            com.leo.eats.domain.model.PlaceCategory.PLAY -> "＋ 记一笔今天去了"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // hero（it-002 R1：列表缩略图 → 详情头部共享元素）
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .aspectRatio(16f / 9f)
                    .sharedPhoto("place-$placeId"),
            ) {
                val hero = place.photos.firstOrNull()?.let { vm.imageFileOf(it) }
                if (hero != null) {
                    AsyncImage(
                        model = hero,
                        contentDescription = place.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                } else {
                    KindPlaceholder(
                        kind = place.kind,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.extraLarge),
                        iconSize = 96.dp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // 标题区
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = menuColors().ink,
                )
                KindChip(place.kind, category = place.category)
            }
            if (place.cuisine.isNotBlank()) {
                Text(
                    place.cuisine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = menuColors().inkFaint,
                )
            }
            Spacer(Modifier.height(8.dp))

            // it-008：愿望徽章行（种草时长 + 已安排日期）——常规件不显示
            if (place.isWish) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = menuColors().accent.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "🌟 愿望 · 种草 " + wishDaysText(place.wishlistedAt) +
                                (place.planAt?.let { " · 📅 ${planDateText(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = menuColors().accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // it-008 阶段C：安排/种草操作（仅愿望条目）
            if (place.isWish) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showPlanPicker = true }) {
                        Text(if (place.planAt != null) "改安排" else "📅 安排到某天")
                    }
                    if (place.planAt != null) {
                        TextButton(onClick = { vm.setPlan(place.id, null) }) { Text("清除安排") }
                    }
                    TextButton(onClick = { vm.setWish(place.id, false) }) { Text("取消种草") }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 派生统计（ADR-008；it-004 O4：一套口径——综合(用户评)/均分(记录均)/次数/上次）
            val animatedCount by animateIntAsState(stats.visitCount, label = "visitCount")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("综合", style = MaterialTheme.typography.bodyMedium, color = menuColors().inkFaint)
                RatingStars(rating = place.rating, size = 15.dp)
                if (stats.avgVisitRating != null) {
                    Text(
                        "均分 %.1f".format(stats.avgVisitRating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = menuColors().accent,
                    )
                }
                Text("·", color = menuColors().inkFaint)
                Text(
                    "$animatedCount 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = menuColors().inkFaint,
                )
                Text("·", color = menuColors().inkFaint)
                Text("上次 ", style = MaterialTheme.typography.bodyMedium, color = menuColors().inkFaint)
                RelativeTimeText(at = stats.lastVisitAt, highlight = true)
            }
            Spacer(Modifier.height(10.dp))

            // 地址与地图跳转
            if (place.located || place.address.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = menuColors().inkFaint,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        place.address.ifBlank { "已定位（无地址文本）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = menuColors().inkFaint,
                        modifier = Modifier.weight(1f),
                    )
                    if (place.located) {
                        TextButton(onClick = { onShowOnMap(place.id) }) { Text("在地图上看 ↗") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (place.tags.isNotEmpty()) {
                TagRow(place.tags)
                Spacer(Modifier.height(8.dp))
            }

            PhotoStrip(models = place.photos.map { vm.imageFileOf(it) })
            Spacer(Modifier.height(8.dp))

            LinkChips(
                links = place.links,
                onOpen = { url -> vm.linkOpener.open(url) { vm.toast("没有可打开该链接的应用") } },
            )
            if (place.links.isNotEmpty()) Spacer(Modifier.height(8.dp))

            if (place.notes.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        place.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = menuColors().ink,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 主操作已移至吸底栏（it-004 O4）
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = menuColors().hairline)
            Spacer(Modifier.height(12.dp))

            // Visit 时间线（倒序）
            Text(
                "去过记录 (${visits.size})",
                style = MaterialTheme.typography.titleMedium,
                color = menuColors().ink,
            )
            Spacer(Modifier.height(6.dp))
            if (visits.isEmpty()) {
                Text(
                    "还没去过，记第一笔吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = menuColors().inkFaint,
                )
            }
            visits.forEachIndexed { index, v ->
                VisitCard(
                    visit = v,
                    fileOf = { vm.imageFileOf(it) },
                    isLast = index == visits.lastIndex,
                    onDelete = { deleteVisitTarget = v },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogVisit) {
        LogVisitSheet(
            vm = vm,
            placeName = place.name,
            onLog = { at, rating, cost, text, uris ->
                vm.logVisit(place.id, at, rating, cost, text, uris) { showLogVisit = false }
            },
            onDismiss = { showLogVisit = false },
        )
    }

    // it-008 阶段C：安排到某天（只选日期，不选时间——「周末去」粒度）
    if (showPlanPicker) {
        val zone = java.time.ZoneId.systemDefault()
        val dateState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = place.planAt ?: System.currentTimeMillis(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPlanPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        // DatePicker 返回 UTC 零点，转本地当日中午避免时区偏日
                        val local = java.time.Instant.ofEpochMilli(selected).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        val noon = local.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                        vm.setPlan(place.id, noon)
                    }
                    showPlanPicker = false
                }) { Text("安排") }
            },
            dismissButton = {
                TextButton(onClick = { showPlanPicker = false }) { Text("取消") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = dateState, title = { Text("  想哪天去？") })
        }
    }

    deleteVisitTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteVisitTarget = null },
            title = { Text("删除这条记录？") },
            text = { Text("${formatVisitTime(target.at)} 的记录及其照片将被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVisit(target.id)
                    deleteVisitTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteVisitTarget = null }) { Text("取消") } },
        )
    }
}

/**
 * Visit 时间线条目（it-002 R2）：左列日期 + 竖线节点母题，内容卡右置。
 * isLast：最后一条竖线截止（R3）。
 */
/** 种草至今天数文本（it-008） */
private fun wishDaysText(from: Long?): String {
    if (from == null) return "今天"
    val days = (System.currentTimeMillis() - from) / (24L * 60 * 60 * 1000)
    return if (days <= 0) "今天" else "$days 天前"
}

/** 安排日期文本（it-008）：今天/明天/周X M/dd */
private fun planDateText(planAt: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(planAt).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return when (date.toEpochDay() - today.toEpochDay()) {
        0L -> "今天去"
        1L -> "明天去"
        else -> "周${"日一二三四五六"[date.dayOfWeek.value % 7]} ${date.monthValue}/${date.dayOfMonth}去"
    }
}

@Composable
private fun VisitCard(visit: Visit, fileOf: (String) -> File?, isLast: Boolean = false, onDelete: () -> Unit) {
    val date = remember(visit.at) {
        java.time.Instant.ofEpochMilli(visit.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 日期左列
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text(
                "${date.monthValue}/${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = menuColors().inkFaint,
            )
            Text(
                formatVisitTime(visit.at).substringAfter("· ").take(5),
                style = MaterialTheme.typography.labelSmall,
                color = menuColors().inkFaint.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.width(10.dp))
        // 竖线 + 节点
        Box(
            Modifier
                .width(14.dp)
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(8.dp)
                    .background(menuColors().accent, androidx.compose.foundation.shape.CircleShape),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp)
                        .width(1.dp)
                        .height(999.dp)
                        .background(menuColors().hairline),
                )
            }
        }
        // 内容卡
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = menuColors().surface,
            tonalElevation = 1.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(rating = visit.rating, size = 13.dp)
                    Spacer(Modifier.weight(1f))
                    if (visit.cost != null) {
                        Text(
                            "¥" + visit.cost.toString().removeSuffix(".0"),
                            style = MaterialTheme.typography.labelMedium,
                            color = menuColors().inkFaint,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除这条记录",
                            tint = menuColors().inkFaint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (visit.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        visit.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = menuColors().ink,
                    )
                }
                if (visit.photos.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visit.photos) { photo ->
                            fileOf(photo)?.let { f ->
                                AsyncImage(
                                    model = f,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 88.dp, height = 66.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
