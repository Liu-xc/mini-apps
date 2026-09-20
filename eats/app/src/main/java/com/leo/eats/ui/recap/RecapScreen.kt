package com.leo.eats.ui.recap

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.usecase.RecapRange
import com.leo.eats.domain.usecase.RecapStats
import com.leo.eats.domain.usecase.recap
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.KindPlaceholder
import com.leo.eats.ui.theme.menuColors
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** W7 统计回顾页（it-007）：回顾指标 + 年度长图出口 + 回忆提醒设置（阶段B）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(vm: AppViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsState()
    val prefs by vm.recapPrefs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val thisYear = remember { LocalDate.now().year }

    var range by remember { mutableStateOf<RecapRange>(RecapRange.Year(thisYear)) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }

    val stats = remember(data, range, now) { data.recap(range, now) }
    val rangeLabel = when (val r = range) {
        is RecapRange.Year -> "${r.year} 年"
        RecapRange.All -> "累计"
    }

    val options = remember(thisYear) {
        listOf(
            RecapRange.Year(thisYear) to "今年",
            RecapRange.Year(thisYear - 1) to "去年",
            RecapRange.All to "累计",
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("未授予通知权限，提醒将无法弹出")
    }

    fun onToggleReminder(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.setReminder(on, prefs.days)
    }

    fun generate() {
        if (rendering) return
        rendering = true
        vm.generateRecap(range, now) { bmp ->
            rendering = false
            if (bmp == null) vm.toast("这个档位还没有记录，先去记一笔吧") else preview = bmp
        }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(menuColors().paper)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = menuColors().ink)
                }
                Text("统计回顾", style = MaterialTheme.typography.titleLarge, color = menuColors().ink)
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow(Modifier.padding(end = 12.dp)) {
                    options.forEachIndexed { i, (r, label) ->
                        SegmentedButton(
                            selected = range == r,
                            onClick = { range = r },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                HeroBand(stats)
                Spacer(Modifier.height(20.dp))

                if (stats.topPlaces.isNotEmpty()) {
                    SectionTitle("最爱 TOP3")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 48.dp),
                    ) {
                        items(stats.topPlaces, key = { it.place.id }) { t ->
                            TopCard(
                                rank = stats.topPlaces.indexOf(t) + 1,
                                name = t.place.name,
                                sub = buildString {
                                    append("${t.count} 次")
                                    t.avgRating?.let { append(" · 均分 ${"%.1f".format(it)}") }
                                },
                                photoFile = t.place.photos.firstOrNull()?.let { vm.imageFileOf(it) },
                                kind = t.place.kind,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                SectionTitle("类型占比")
                RatioBar(stats)
                Spacer(Modifier.height(20.dp))

                SectionTitle("月度节奏（峰值 ${stats.monthly[stats.monthly.indices.maxBy { stats.monthly[it].count }].label}）")
                MonthlyBars(stats)
                Spacer(Modifier.height(12.dp))

                StreakRow(stats)
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { generate() },
                    enabled = stats.hasData && !rendering,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(if (rendering) "生成中…" else "生成年终食光长图")
                }
                Spacer(Modifier.height(24.dp))

                ReminderSettings(
                    enabled = prefs.enabled,
                    days = prefs.days,
                    demo = vm.isDemo,
                    onToggle = ::onToggleReminder,
                    onDays = { vm.setReminder(prefs.enabled, it) },
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    preview?.let { bmp ->
        RecapPreviewDialog(
            bitmap = bmp,
            onSave = {
                scope.launch { vm.toast(vm.saveRecap(bmp) ?: "当前系统不支持直接存相册，请用分享保存") }
            },
            onShare = {
                runCatching { context.startActivity(vm.shareRecap(bmp)) }
                    .onFailure { vm.toast("没有可用的分享目标") }
            },
            onClose = { preview = null },
        )
    }
}

// ---- 区块组件 ----

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = menuColors().ink,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun HeroBand(stats: RecapStats) {
    val mc = menuColors()
    Surface(shape = MaterialTheme.shapes.large, color = mc.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            HeroCell(Modifier.weight(1f), "${stats.totalVisits}", "档位内顿数")
            Box(Modifier.width(1.dp).height(56.dp).background(mc.hairline))
            HeroCell(Modifier.weight(1f), stats.totalCost?.let { "¥${trim(it)}" } ?: "—", "总花费")
            Box(Modifier.width(1.dp).height(56.dp).background(mc.hairline))
            HeroCell(Modifier.weight(1f), "${stats.placesVisited}", "去过店数")
        }
    }
}

@Composable
private fun HeroCell(modifier: Modifier, value: String, label: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (value.length > 6) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            color = menuColors().ink,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = menuColors().inkFaint)
    }
}

@Composable
private fun TopCard(rank: Int, name: String, sub: String, photoFile: File?, kind: PlaceKind) {
    val mc = menuColors()
    Surface(shape = MaterialTheme.shapes.large, color = mc.surface, tonalElevation = 1.dp, modifier = Modifier.width(200.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(110.dp).clip(MaterialTheme.shapes.large)) {
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    KindPlaceholder(kind = kind, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .background(mc.ink.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$rank", color = mc.paper, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = mc.ink, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
            }
        }
    }
}

@Composable
private fun RatioBar(stats: RecapStats) {
    val mc = menuColors()
    val total = stats.kindCounts.values.sum().coerceAtLeast(1)
    val entries = listOf(
        PlaceKind.RESTAURANT to mc.restaurant,
        PlaceKind.TAKEOUT to mc.takeout,
        PlaceKind.HOME to mc.homeCook,
    )
    Column {
        Row(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEach { (kind, color) ->
                val w = (stats.kindCounts[kind] ?: 0) / total.toFloat()
                if (w > 0f) Box(Modifier.weight(w).fillMaxSize().background(color))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            entries.forEach { (kind, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    val pct = ((stats.kindCounts[kind] ?: 0) * 100f / total).toInt()
                    val label = when (kind) {
                        PlaceKind.RESTAURANT -> "堂食"
                        PlaceKind.TAKEOUT -> "外卖"
                        PlaceKind.HOME -> "自做"
                    }
                    Text("$label $pct%", style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
                }
            }
        }
    }
}

@Composable
private fun MonthlyBars(stats: RecapStats) {
    val mc = menuColors()
    val months = stats.monthly
    val max = months.maxOf { it.count }.coerceAtLeast(1)
    val peak = months.indices.maxBy { months[it].count }
    val barColor = mc.hairline
    val peakColor = mc.accent
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 8.dp.toPx()
            val bw = (size.width - gap * 11) / 12f
            val base = size.height
            months.forEachIndexed { i, m ->
                val h = if (m.count == 0) 5.dp.toPx() else 5.dp.toPx() + (size.height - 10.dp.toPx()) * m.count / max
                drawRoundRect(
                    color = if (i == peak) peakColor else barColor,
                    topLeft = Offset(i * (bw + gap), base - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(months.first().label, style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
            Text(months.last().label, style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
        }
    }
}

@Composable
private fun StreakRow(stats: RecapStats) {
    val mc = menuColors()
    Surface(shape = MaterialTheme.shapes.large, color = mc.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("连续记录", style = MaterialTheme.typography.titleSmall, color = mc.ink)
            Spacer(Modifier.weight(1f))
            if (stats.currentStreak in 1..7) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { i ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    if (i < stats.currentStreak) mc.ink else mc.hairline,
                                    CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            } else if (stats.currentStreak > 7) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = mc.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("${stats.currentStreak} 天", style = MaterialTheme.typography.titleSmall, color = mc.ink)
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
    val mc = menuColors()
    Column {
        Text("回忆提醒", style = MaterialTheme.typography.titleMedium, color = mc.ink)
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = mc.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("好久没去提醒", style = MaterialTheme.typography.titleSmall, color = mc.ink)
                        Text(
                            "去过 3 次以上 · 评分 4 以上 · 超过 $days 天没去",
                            style = MaterialTheme.typography.labelSmall,
                            color = mc.inkFaint,
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
                    Text("演示模式不推送", style = MaterialTheme.typography.labelSmall, color = mc.accent)
                }
            }
        }
    }
}

@Composable
private fun RecapPreviewDialog(
    bitmap: Bitmap,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxSize(0.92f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "长图预览",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    contentScale = ContentScale.FillWidth,
                )
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

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
