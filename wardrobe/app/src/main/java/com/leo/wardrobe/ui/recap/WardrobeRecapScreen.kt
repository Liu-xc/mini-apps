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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
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
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.usecase.WardrobeRecapRange
import com.leo.wardrobe.domain.usecase.WardrobeRecapStats
import com.leo.wardrobe.domain.usecase.wardrobeRecap
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.CountUpText
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File
import java.time.LocalDate

/** W9 衣橱回顾页（it-018 阶段B）：结构指标 + 打卡行为 + 年度长图出口 + 衣柜提醒设置。
 *  it-021：回顾域（提醒/长图）走 [RecapViewModel]，角色/数据/提示走全局 [AppViewModel]。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeRecapScreen(
    appVm: AppViewModel,
    vm: RecapViewModel,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    /** it-031 C10：打卡空态「去打卡」直达穿搭记录 Tab */
    onGoRecords: () -> Unit = {},
) {
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

    // it-024：数据包导出/导入（D2① / D3④a）
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startImportFromUri(uri)
    }
    // it-024：系统直达（微信「用其他应用打开」/ 文件管理器分享，D3④b）
    val pendingImport by com.leo.wardrobe.MainActivity.pendingImport.collectAsState()
    LaunchedEffect(pendingImport) {
        val p = pendingImport ?: return@LaunchedEffect
        com.leo.wardrobe.MainActivity.pendingImport.value = null
        vm.startImport(p.file, p.displayName)
    }

    fun generate() {
        if (rendering) return
        rendering = true
        vm.generateRecap(person?.id, range, now) { file ->
            rendering = false
            if (file == null) appVm.toast("这个档位还没有打卡记录，先去打卡吧") else previewFile = file
        }
    }

    // it-034 A：本页状态栏沉浸白（白顶栏铺进状态栏）——根 Scaffold 对 RECAP 已去顶，
    // 内层 Scaffold 只留左右/底部 inset，消除状态栏下方那段浅绿带（原三层叠加 inset）
    Scaffold(
        contentWindowInsets = androidx.compose.material3.ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
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

                    if (!stats.hasWearData) {
                        // it-025：无打卡数据时不渲染「穿 0 次/利用率 0%」空指标，给打卡引导
                        Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Outlined.Checkroom,
                                    contentDescription = null,
                                    tint = ec.inkFaint,
                                    modifier = Modifier.size(30.dp),
                                )  // it-030：👟 emoji → Material 图标
                                Spacer(Modifier.height(8.dp))
                                Text("还没有穿搭打卡", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "去「穿搭记录」打开一套，点「今天穿了这套」——\n最百搭、利用率与年度长图都会从这里长出来",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ec.inkFaint,
                                )
                                Spacer(Modifier.height(12.dp))
                                // it-031 C10：空态必须有行动按钮（DESIGN.md §5.8）
                                Button(onClick = onGoRecords) {
                                    Text("去打卡")
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    if (stats.hasWearData && stats.topVersatile.isNotEmpty()) {
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

                    if (stats.hasWearData) {
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
                    }

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
                    // it-034 C7：禁用即说明解锁条件——真实门槛 = 本档位打卡次数 ≥1（hasWearData）
                    if (!stats.hasWearData) {
                        Spacer(Modifier.height(6.dp))
                        val hint = when (val r = range) {
                            is WardrobeRecapRange.Year -> "${r.year} 年打卡 ≥ 1 次后解锁"
                            else -> "累计打卡 ≥ 1 次后解锁"
                        } + " · 去「穿搭记录」点「今天穿了这套」"
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            // 对比 ≥4.5:1：浅色下 onSurfaceVariant(0x808D82) 仅 ~3:1，加深一档；
                            // 深色下 onSurfaceVariant(0x94A294) 对墨纸 ~7:1 直接可用
                            color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFF55605A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                    Spacer(Modifier.height(24.dp))

                    // it-024：「数据」小节（D1），挂在提醒设置之后
                    DataPackageSection(
                        vm = vm,
                        demo = vm.isDemo,
                        onExport = {
                            val name = "wardrobe-backup-" +
                                java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.CHINA)
                                    .format(java.util.Date()) + ".zip"
                            exportLauncher.launch(name)
                        },
                        onImport = {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                            )
                        },
                        onToast = { appVm.toast(it) },
                        onExportDone = { summary, file ->
                            appVm.toastAction(summary, "分享") { vm.sharePackage(file) }
                        },
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
            // it-027：三大数字 count-up（DESIGN.md §5 反例 9），60ms 错峰
            HeroCell(Modifier.weight(1f), stats.itemCount, "单品", 0)
            Box(Modifier.width(1.dp).height(56.dp).background(ec.hairline))
            HeroCell(Modifier.weight(1f), stats.outfitCount, "穿搭套", 60)
            Box(Modifier.width(1.dp).height(56.dp).background(ec.hairline))
            HeroCell(Modifier.weight(1f), stats.wearCount, "打卡次数", 120)
        }
    }
}

@Composable
private fun HeroCell(modifier: Modifier, target: Int, label: String, delayMs: Long) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CountUpText(
            target = target,
            style = MaterialTheme.typography.headlineMedium,
            color = editorialColors().ink,
            delayMs = delayMs,
        )
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

/**
 * it-034 C6：品类分布色阶——同色系 6 档绿（深→浅），按数量降序铺档（数量最大段最深，
 * 保证拿到段内白字标注的宽段一定落在深色档）；段间 2dp 留白保留。
 * 档位按段数均摊，段数不足/超过 6 时在首尾档之间插值取档。
 */
private val CategoryRamp = listOf(
    Color(0xFF14543A),
    Color(0xFF1D6845),
    Color(0xFF2A7D55),
    Color(0xFF429E68),  // 品牌绿（Accent 同值）
    Color(0xFF7CC09B),
    Color(0xFFBFE1CE),
)

@Composable
private fun CategoryBar(counts: Map<com.leo.wardrobe.domain.model.WardrobeCategory, Int>) {
    val ec = editorialColors()
    val segments = counts.filterValues { it > 0 }.entries
        .sortedWith(
            compareByDescending<Map.Entry<com.leo.wardrobe.domain.model.WardrobeCategory, Int>> { it.value }
                .thenBy { it.key.ordinal },
        )
    val total = segments.sumOf { it.value }.coerceAtLeast(1)
    Column {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)),
        ) {
            val gap = 2.dp
            val avail = maxWidth - gap * (segments.size - 1).coerceAtLeast(0)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                segments.forEachIndexed { i, entry ->
                    val share = entry.value / total.toFloat()
                    if (share <= 0f) return@forEachIndexed
                    // 按数量降序铺 6 档：段数 n>1 时 idx = i*(5)/(n-1)，n=1 用最深档
                    val rampIdx = if (segments.size <= 1) 0
                    else (i * (CategoryRamp.size - 1)) / (segments.size - 1)
                    val segColor = CategoryRamp[rampIdx.coerceIn(0, CategoryRamp.size - 1)]
                    // 宽段（>78dp 逻辑宽）段内直标「品类 n」；窄段靠下方文字行对应
                    val wide = avail * share > 78.dp
                    Box(
                        Modifier.weight(share).fillMaxSize().background(segColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (wide) {
                            Text(
                                "${entry.key.label} ${entry.value}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                // 白字仅用于对比 ≥4.5:1 的深色档，浅色档落墨色兜底
                                color = if (segColor.luminance() < 0.183f) Color.White else ec.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            segments.joinToString(" · ") { "${it.key.label} ${it.value}" },
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
                // it-034 C9：chips 可用 = 总开关开 && 非演示（原仅演示禁用）；
                // 不可用时整行 alpha 0.45 表示「跟着总开关走」
                val chipsActive = enabled && !demo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.alpha(if (chipsActive) 1f else 0.45f),
                ) {
                    listOf(60, 90, 180).forEach { d ->
                        FilterChip(
                            selected = days == d,
                            onClick = { onDays(d) },
                            enabled = chipsActive,
                            label = { Text("$d 天") },
                            // it-034 C9：选中态统一品牌绿——primary 容器/描边 + onPrimary 文字
                            // （与 TagInput 已选标签同构，覆盖 M3 默认淡紫灰系）
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = chipsActive,
                                selected = days == d,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
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
    // it-023：预览改 Coil 异步按约束降采样（原 BitmapFactory 在组合期主线程全尺寸解码长图，开预览必卡）
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxSize(0.92f),
        ) {
            Column(Modifier.padding(16.dp)) {
                coil.compose.AsyncImage(
                    model = file,
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
