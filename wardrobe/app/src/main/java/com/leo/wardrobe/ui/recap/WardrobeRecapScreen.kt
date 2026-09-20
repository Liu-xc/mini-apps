@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.recap

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.usecase.WardrobeRecapRange
import com.leo.wardrobe.domain.usecase.WardrobeRecapStats
import com.leo.wardrobe.domain.usecase.wardrobeRecap
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File
import java.time.LocalDate

/** W9 衣橱回顾页（it-018 阶段B）：结构指标 + 打卡行为 + 年度长图出口 + 衣柜提醒设置。
 *  it-021：回顾域（提醒/长图）走 [RecapViewModel]，角色/数据/提示走全局 [AppViewModel]。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeRecapScreen(appVm: AppViewModel, vm: RecapViewModel, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val data by appVm.data.collectAsState()
    val prefs by vm.recapPrefs.collectAsState()
    val person by appVm.currentPerson.collectAsState()
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val thisYear = remember { LocalDate.now().year }

    var range by remember { mutableStateOf<WardrobeRecapRange>(WardrobeRecapRange.Year(thisYear)) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var showIdle by remember { mutableStateOf(false) }
    // 二级页拦截系统返回（否则直接退出整个回顾页）
    androidx.activity.compose.BackHandler(enabled = showIdle) { showIdle = false }

    val stats = remember(data, person, range, now) {
        data.wardrobeRecap(person?.id ?: "", range, now)
    }
    val ec = editorialColors()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) appVm.toast("未授予通知权限，提醒将无法弹出")
    }

    fun generate() {
        if (rendering) return
        rendering = true
        vm.generateRecap(person?.id, range, now) { file ->
            rendering = false
            if (file == null) appVm.toast("这个档位还没有打卡记录，先去打卡吧") else previewFile = file
        }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(ec.paper)) {
            if (showIdle) {
                // 二级页独占整页（含顶栏），父页头部不堆叠（评审修复）
                IdleItemsPage(
                    idleItems = stats.idleItems,
                    fileOf = vm::imageFileOf,
                    onOpenItem = onOpenItem,
                    onBack = { showIdle = false },
                )
            } else {
                TopAppBar(
                    title = { Text("衣橱回顾 · ${stats.personName}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        SingleChoiceSegmentedButtonRow(Modifier.padding(end = 12.dp)) {
                            listOf(
                                WardrobeRecapRange.Year(thisYear) to "今年",
                                WardrobeRecapRange.All to "累计",
                            ).forEachIndexed { i, (r, label) ->
                                SegmentedButton(
                                    selected = range == r,
                                    onClick = { range = r },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    HeroBand(stats)
                    Spacer(Modifier.height(20.dp))

                    if (stats.topVersatile.isNotEmpty()) {
                        SectionTitle("最百搭 TOP3")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 48.dp),
                        ) {
                            items(stats.topVersatile, key = { it.item.id }) { t ->
                                VersatileCard(
                                    rank = stats.topVersatile.indexOf(t) + 1,
                                    name = t.item.name,
                                    sub = "进过 ${t.outfitCount} 套 · 穿 ${t.wearCount} 次",
                                    photoFile = vm.imageFileOf(t.item.imageFile),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    SectionTitle("品类分布")
                    CategoryBar(stats.categoryCounts)
                    Spacer(Modifier.height(20.dp))

                    Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("利用率", style = MaterialTheme.typography.titleSmall, color = ec.ink)
                                Spacer(Modifier.weight(1f))
                                Text("${(stats.utilization * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ec.hairline),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(stats.utilization.toFloat().coerceIn(0.01f, 1f))
                                        .height(8.dp)
                                        .background(ec.accent),
                                )
                            }
                            Text(
                                "穿过 1 次以上的单品占比",
                                style = MaterialTheme.typography.labelSmall,
                                color = ec.inkFaint,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = ec.surface,
                        tonalElevation = 1.dp,
                        onClick = { showIdle = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("闲置清单", style = MaterialTheme.typography.titleSmall, color = ec.ink)
                                Text(
                                    "${stats.idleItems.size} 件从没上过身",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ec.inkFaint,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "查看闲置清单",
                                tint = ec.inkFaint,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (stats.topOutfit != null) {
                        Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("出勤最高", style = MaterialTheme.typography.titleSmall, color = ec.ink)
                                Spacer(Modifier.width(20.dp))
                                Text(
                                    "「${stats.topOutfit!!.outfit.tags.firstOrNull() ?: "这套"}」穿过 ${stats.topOutfit!!.wearCount} 次",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ec.inkFaint,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    StreakRow(stats)
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { generate() },
                        enabled = stats.hasWearData && !rendering,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(if (rendering) "生成中…" else "生成年度衣橱长图")
                    }
                    Spacer(Modifier.height(24.dp))

                    ReminderSettings(
                        enabled = prefs.enabled,
                        days = prefs.days,
                        demo = vm.isDemo,
                        onToggle = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.setReminder(on, prefs.days)
                        },
                        onDays = { vm.setReminder(prefs.enabled, it) },
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    previewFile?.let { file ->
        RecapPreviewDialog(
            file = file,
            onSave = {
                if (vm.saveRecapImage(file)) appVm.toast("已存相册 Pictures/Wardrobe ✓")
                else appVm.toast("当前系统不支持直接存相册，请用分享保存")
            },
            onShare = {
                runCatching { vm.shareRecapImage(file) }
                    .onFailure { appVm.toast("没有可用的分享目标") }
            },
            onClose = { previewFile = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = editorialColors().ink,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun HeroBand(stats: WardrobeRecapStats) {
    val ec = editorialColors()
    Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            HeroCell(Modifier.weight(1f), "${stats.itemCount}", "单品")
            Box(Modifier.width(1.dp).height(56.dp).background(ec.hairline))
            HeroCell(Modifier.weight(1f), "${stats.outfitCount}", "穿搭套")
            Box(Modifier.width(1.dp).height(56.dp).background(ec.hairline))
            HeroCell(Modifier.weight(1f), "${stats.wearCount}", "打卡次数")
        }
    }
}

@Composable
private fun HeroCell(modifier: Modifier, value: String, label: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = editorialColors().ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = editorialColors().inkFaint)
    }
}

@Composable
private fun VersatileCard(rank: Int, name: String, sub: String, photoFile: File?) {
    val ec = editorialColors()
    Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp, modifier = Modifier.width(180.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                PhotoCard(
                    file = photoFile,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .background(ec.ink.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$rank", color = ec.paper, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = ec.ink, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = ec.inkFaint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategoryBar(counts: Map<com.leo.wardrobe.domain.model.WardrobeCategory, Int>) {
    val ec = editorialColors()
    val total = counts.values.sum().coerceAtLeast(1)
    Column {
        Row(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            counts.filterValues { it > 0 }.forEach { (_, count) ->
                val w = count / total.toFloat()
                if (w > 0f) Box(Modifier.weight(w).fillMaxSize().background(if (counts.values.size > 1) ec.accent.copy(alpha = 0.25f + 0.5f * w) else ec.accent))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            counts.filterValues { it > 0 }.entries.joinToString(" · ") { "${it.key.label} ${it.value}" },
            style = MaterialTheme.typography.labelSmall,
            color = ec.inkFaint,
        )
    }
}

@Composable
private fun StreakRow(stats: WardrobeRecapStats) {
    val ec = editorialColors()
    Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("连续打卡", style = MaterialTheme.typography.titleSmall, color = ec.ink)
            Spacer(Modifier.weight(1f))
            if (stats.currentStreak > 7) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = ec.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("${stats.currentStreak} 天", style = MaterialTheme.typography.titleSmall, color = ec.ink)
        }
    }
}

@Composable
private fun ReminderSettings(
    enabled: Boolean,
    days: Int,
    demo: Boolean,
    onToggle: (Boolean) -> Unit,
    onDays: (Int) -> Unit,
) {
    val ec = editorialColors()
    Column {
        Text("衣柜提醒", style = MaterialTheme.typography.titleMedium, color = ec.ink)
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("好久没穿提醒", style = MaterialTheme.typography.titleSmall, color = ec.ink)
                        Text(
                            "单品粒度 · 穿过 2 次以上 · 超过 $days 天没穿",
                            style = MaterialTheme.typography.labelSmall,
                            color = ec.inkFaint,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = onToggle, enabled = !demo)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 90, 180).forEach { d ->
                        FilterChip(
                            selected = days == d,
                            onClick = { onDays(d) },
                            enabled = !demo,
                            label = { Text("$d 天") },
                        )
                    }
                }
                if (demo) {
                    Spacer(Modifier.height(6.dp))
                    Text("演示模式不推送", style = MaterialTheme.typography.labelSmall, color = ec.accent)
                }
            }
        }
    }
}

/** 闲置清单二级页：从没上过身的单品网格（点开详情） */
@Composable
private fun IdleItemsPage(
    idleItems: List<Item>,
    fileOf: (String) -> File?,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ec = editorialColors()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("闲置清单 · ${idleItems.size} 件") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
        )
        if (idleItems.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("没有闲置", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                Text(
                    "每件单品都上过身，衣橱利用率满分",
                    style = MaterialTheme.typography.bodySmall,
                    color = ec.inkFaint,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(idleItems, key = { it.id }) { item ->
                    Column(
                        Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(ec.surface)
                            .clickable { onOpenItem(item.id) },
                    ) {
                        PhotoCard(
                            file = fileOf(item.imageFile),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                        )
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = ec.ink,
                                maxLines = 1,
                            )
                            Text(
                                "买了 ${((System.currentTimeMillis() - item.createdAt) / 86_400_000L).coerceAtLeast(0)} 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = ec.inkFaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapPreviewDialog(
    file: File,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.path) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxSize(0.92f),
        ) {
            Column(Modifier.padding(16.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "长图预览",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("存相册") }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("分享") }
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("完成") }
                }
            }
        }
    }
}
