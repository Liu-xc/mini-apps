package com.leo.wardrobe.ui.wardrobe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.leo.wardrobe.ui.components.StaggeredEntrance
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.data.mock.DemoMode
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.tagsUsedIn
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.FilterChipsRow
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.iconRes
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W3 衣橱页（US-01/02/03/13）：品类分组 + 标签筛选 + 点编辑 + 滑动删除（确认）+ FAB。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    vm: AppViewModel,
    onEditItem: (String?) -> Unit,
    onOpenItem: (String) -> Unit = {},
    onOpenRecap: () -> Unit = {},
    onOpenWishlist: () -> Unit = {},
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    var filterTag by remember { mutableStateOf<String?>(null) }
    var categoryTab by remember { mutableStateOf<WardrobeCategory?>(null) }
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    // it-015：演示模式入口——同一入口按当前模式进入/退出
    val context = LocalContext.current
    val demoOn = remember { DemoMode.isEnabled(context) }
    var demoAskOpen by remember { mutableStateOf(false) }
    // it-015 修订：入口改「标题连点 5 次」隐藏开关（3 秒内有效），全构建可用——正式包也可体验演示数据
    var demoTaps by remember { mutableStateOf(0) }
    var demoFirstTapAt by remember { mutableStateOf(0L) }
    fun tapForDemo() {
        val now = System.currentTimeMillis()
        if (now - demoFirstTapAt > 3000L) { demoTaps = 0; demoFirstTapAt = now }
        if (++demoTaps >= DEMO_TAP_COUNT) {
            demoTaps = 0
            demoAskOpen = true
        } else if (demoTaps >= 2) {
            vm.toast(if (demoOn) "再按 ${DEMO_TAP_COUNT - demoTaps} 次退出演示模式" else "再按 ${DEMO_TAP_COUNT - demoTaps} 次进入演示模式")
        }
    }

    val personId = person?.id
    val allItems = remember(personId, data) { if (personId != null) data.itemsOf(personId) else emptyList() }
    val filtered = remember(allItems, filterTag, categoryTab) {
        allItems
            .filter { filterTag == null || filterTag!! in it.tags }
            .filter { categoryTab == null || it.category == categoryTab }
    }
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
                    if (categoryTab == null) "衣橱 · ${person?.name ?: ""}" else "${categoryTab!!.label} · ${person?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = editorialColors().ink,
                    modifier = Modifier.clickable { tapForDemo() },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "共 ${filtered.size} 件",
                        style = MaterialTheme.typography.labelSmall,
                        color = editorialColors().inkFaint,
                    )
                    // it-018：衣橱回顾入口（W9）
                    FilledTonalIconButton(
                        onClick = onOpenRecap,
                        modifier = Modifier.padding(start = 8.dp).size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = "衣橱回顾",
                            tint = editorialColors().inkFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    // it-019：心愿入口（W9'：想买单品 + 心愿穿搭）；it-030：emoji → Material Star
                    FilledTonalIconButton(
                        onClick = onOpenWishlist,
                        modifier = Modifier.padding(start = 8.dp).size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "心愿",
                            tint = editorialColors().inkFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // it-012 O4'：品类图标 Tab（横滑）+「筛选」固定行尾不再被挤出屏外
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = categoryTab == null,
                        onClick = { categoryTab = null },
                        label = { Text("全部") },
                    )
                    WardrobeCategory.entries.forEach { c ->
                        val selected = categoryTab == c
                        Surface(
                            onClick = { categoryTab = if (categoryTab == c) null else c },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (selected) editorialColors().accent.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (selected) 1.5.dp else 0.dp,
                                editorialColors().accent,
                            ),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(c.iconRes),
                                    contentDescription = c.label,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
                // 筛选固定：角标常驻可见（R2 P1 修法）
                FilterChip(
                    selected = filterTag != null,
                    onClick = { filterSheetOpen = true },
                    label = { Text(if (filterTag != null) "筛选·1" else "筛选") },
                )
            }

            if (allItems.isEmpty()) {
                EmptyState(
                    title = "衣橱还空着",
                    hint = "点右下角 ＋ 拍照录入第一件衣物",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else if (filtered.isEmpty()) {
                EmptyState(
                    title = "该筛选下没有衣物",
                    hint = "换个品类或标签试试",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                // it-011 C6：两列卡片网格——首屏 4–6 件直达浏览；去行内品类小标，
                // 「全部」下保留品类小节标题；滑动删除改长按删除（网格里滑动会让位滚动）
                // it-028：首屏瀑布入场落地（specs/05 #7），rememberSaveable 保证仅首进播放（DESIGN.md §3 预算）
                var entranceDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(900)
                    entranceDone = true
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // it-012：去品类小节标题（R2：单件品类占整行打断节奏），按品类序平铺
                    val sorted = filtered.sortedBy { it.category.ordinal }
                    itemsIndexed(sorted, key = { _, it -> it.id }) { index, item ->
                        StaggeredEntrance(index = index, animate = !entranceDone) {
                            ItemCard(vm, item, onEdit = { onEditItem(item.id) }, onDelete = { pendingDelete = item }, onOpenDetail = { onOpenItem(item.id) })
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

    // it-011 C6：标签筛选弹层
    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = editorialColors().ink)
                Text("标签", style = MaterialTheme.typography.labelLarge, color = editorialColors().inkFaint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = filterTag == null,
                        onClick = { filterTag = null },
                        label = { Text("全部") },
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = filterTag == tag,
                            onClick = { filterTag = if (filterTag == tag) null else tag },
                            label = { Text("#$tag") },
                        )
                    }
                }
                Button(
                    onClick = { filterSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("完成") }
            }
        }
    }

    // it-015：演示模式确认——重启进程切换数据源，真实数据零接触
    if (demoAskOpen) {
        AlertDialog(
            onDismissRequest = { demoAskOpen = false },
            title = { Text(if (demoOn) "退出演示模式？" else "进入演示模式？") },
            text = {
                Text(
                    if (demoOn) "返回真实衣橱，应用将自动重启。"
                    else "切换到内置演示数据（不落盘），用于测试体验与走查；你的真实衣橱不会受到任何影响。应用将自动重启。",
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

/** 网格衣物卡（it-011 C6；it-012 右上 ··· 显式菜单 = 编辑/删除，长按保留快捷） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    vm: AppViewModel,
    item: Item,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpenDetail, onLongClick = onDelete)
            .padding(8.dp),
    ) {
        Box {
            PhotoCard(
                file = vm.imageFileOf(item.imageFile),
                contentDescription = item.name,
                corner = 10.dp,
                mat = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f),
            )
            // it-012：··· 显式入口（P0：替代零提示长按）
            Box(Modifier.align(Alignment.TopEnd)) {
                Surface(
                    onClick = { menuOpen = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = androidx.compose.ui.graphics.Color(0x66000000),
                    modifier = Modifier.padding(4.dp).size(28.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "编辑或删除",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(5.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        // it-025：名称行尾 › 暗示可点进详情（大图/评论/穿搭反查）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        ) {
            Text(
                item.name,
                // it-030 C4：实体名走衬线 Title（DESIGN.md §2.3，与详情页一致）
                style = MaterialTheme.typography.titleLarge,
                color = editorialColors().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", style = MaterialTheme.typography.titleSmall, color = editorialColors().inkFaint)
        }
        // it-012：颜色改色点胶囊，与 #标签 并列但语义分离
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 2.dp)) {
            if (item.color.isNotBlank()) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                    Text(
                        item.color,
                        style = MaterialTheme.typography.labelSmall,
                        color = editorialColors().inkFaint,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (item.tags.isNotEmpty()) {
                Text(
                    (if (item.color.isNotBlank()) "  " else "") + item.tags.take(2).joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 演示模式隐藏开关（it-015 修订）：衣橱页标题连点次数，3 秒窗口 */
private const val DEMO_TAP_COUNT = 5
