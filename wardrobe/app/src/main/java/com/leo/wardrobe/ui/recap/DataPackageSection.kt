@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.leo.wardrobe.domain.model.CollectionDiff
import com.leo.wardrobe.domain.model.ImportMode
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * W9「数据」小节（it-024，线框 D1–D7）：导出/导入入口卡 + 导入导出全流程对话框。
 * 状态机在 [RecapViewModel]；本文件只渲染。
 */
@Composable
fun DataPackageSection(
    vm: RecapViewModel,
    demo: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToast: (String) -> Unit,
    onExportDone: (summary: String, shareFile: File) -> Unit,
) {
    val importUi by vm.importUi.collectAsState()
    val exportUi by vm.exportUi.collectAsState()
    val ec = editorialColors()

    Column {
        Text("数据", style = MaterialTheme.typography.titleMedium, color = ec.ink)
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = ec.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                DataRow(
                    icon = { Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = ec.accent, modifier = Modifier.size(22.dp)) },
                    title = "导出数据包",
                    sub = "zip 备份 · 可交给 AI 加工后导回",
                    enabled = !demo && exportUi !is RecapViewModel.ExportUi.Running,
                    onClick = onExport,
                )
                DataRow(
                    icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = ec.accent, modifier = Modifier.size(22.dp)) },
                    title = "导入数据包",
                    sub = "合并打标结果 / 整包恢复",
                    enabled = !demo && importUi !is RecapViewModel.ImportUi.Checking && importUi !is RecapViewModel.ImportUi.Running,
                    onClick = onImport,
                )
                if (demo) {
                    Text(
                        "演示模式下不可用，不触碰真实数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = ec.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    ImportDialogs(importUi = importUi, vm = vm, onToast = onToast)
    ExportDialogs(exportUi = exportUi, vm = vm, onDone = onExportDone)
}

@Composable
private fun DataRow(
    icon: @Composable () -> Unit,
    title: String,
    sub: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ec = editorialColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) ec.ink else ec.inkFaint)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = ec.inkFaint)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) ec.inkFaint else ec.hairline,
        )
    }
}

// ---- 导入流程对话框（D3 ⑤ – D7）----

@Composable
private fun ImportDialogs(
    importUi: RecapViewModel.ImportUi,
    vm: RecapViewModel,
    onToast: (String) -> Unit,
) {
    when (importUi) {
        RecapViewModel.ImportUi.Checking -> ProgressDialog("校验数据包…")

        is RecapViewModel.ImportUi.Running ->
            ProgressDialog("导入中 · 处理图片 ${importUi.done}/${importUi.total}")

        is RecapViewModel.ImportUi.Rejected -> AlertDialog(
            onDismissRequest = vm::dismissImport,
            title = { Text("无法导入此数据包") },
            text = {
                Column {
                    importUi.reasons.forEach { r ->
                        Text("✗ $r", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✓ 本地数据未做任何改动",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissImport) { Text("知道了") } },
        )

        is RecapViewModel.ImportUi.Confirm -> ConfirmImportDialog(importUi, vm)

        is RecapViewModel.ImportUi.Done -> {
            LaunchedEffect(importUi) {
                onToast("导入完成 · 新增 ${importUi.added} · 更新 ${importUi.updated} · 已即时生效")
                vm.consumeImportDone()
            }
        }

        RecapViewModel.ImportUi.Idle -> Unit
    }
}

@Composable
private fun ConfirmImportDialog(state: RecapViewModel.ImportUi.Confirm, vm: RecapViewModel) {
    var replace by remember { mutableStateOf(false) }
    var askReplace by remember { mutableStateOf(false) }
    val ec = editorialColors()

    AlertDialog(
        onDismissRequest = vm::dismissImport,
        title = { Text("数据包预览") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("📦 ${state.fileName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                val time = remember(state.exportedAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(state.exportedAt))
                }
                val from = if (state.generator.startsWith("wardrobe")) "来自 ${state.generator}"
                else "来自 ${state.generator} · AI 加工包"
                Text("$from · $time", style = MaterialTheme.typography.labelSmall, color = ec.inkFaint)
                Spacer(Modifier.height(12.dp))

                if (!replace) {
                    Text("合并模式将发生：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    DiffLines(state)
                    if (state.diff.locallyNewer > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "⚠ ${state.diff.locallyNewer} 件在导出后被本地修改过，导入将覆盖这些修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB8860B),
                        )
                    }
                } else {
                    Text("替换模式将发生：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✕ 清除  本地${countsLine(state.localCounts)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "＋ 导入  包内${countsLine(state.packageCounts)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(14.dp))
                SelectRow(
                    selected = !replace,
                    label = "合并（推荐）",
                    sub = "同 id 实体以包内为准，本地多出的保留",
                ) { replace = false }
                SelectRow(
                    selected = replace,
                    label = "替换全部",
                    sub = "清空本地后导入包内内容",
                ) { replace = true }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (replace) askReplace = true else vm.applyImport(ImportMode.MERGE) }) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissImport) { Text("取消") }
        },
    )

    if (askReplace) {
        AlertDialog(
            onDismissRequest = { askReplace = false },
            title = { Text("替换全部数据？") },
            text = {
                Text(
                    "将删除本地${countsLine(state.localCounts)}，替换为包内${countsLine(state.packageCounts)}。\n" +
                        "此操作无法撤销，建议先导出一份当前数据再替换。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askReplace = false
                    vm.applyImport(ImportMode.REPLACE)
                }) { Text("替换", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askReplace = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SelectRow(selected: Boolean, label: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = editorialColors().inkFaint)
        }
    }
}

@Composable
private fun DiffLines(state: RecapViewModel.ImportUi.Confirm) {
    val lines = listOfNotNull(
        diffLine("单品", state.diff.items),
        diffLine("穿搭", state.diff.outfits),
        diffLine("评论", state.diff.notes),
        diffLine("打卡", state.diff.wearLogs),
        diffLine("想买", state.diff.wishItems),
        diffLine("心愿穿搭", state.diff.wishOutfits),
        diffLine("角色", state.diff.persons),
    )
    lines.forEach {
        Text(it, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
    }
}

private fun diffLine(label: String, d: CollectionDiff): String? {
    if (d.added == 0 && d.updated == 0 && d.localOnly == 0 && d.unchanged == 0) return null
    val parts = listOfNotNull(
        if (d.added > 0) "＋新增 ${d.added}" else null,
        if (d.updated > 0) "↻更新 ${d.updated}" else null,
        if (d.kept > 0) "＝保留 ${d.kept}" else null,
    )
    if (parts.isEmpty()) return null
    return "${parts.joinToString(" · ")} $label"
}

private val COUNT_LABELS = linkedMapOf(
    "persons" to "角色", "items" to "单品", "outfits" to "穿搭", "notes" to "评论",
    "wearLogs" to "打卡", "wishItems" to "想买", "wishOutfits" to "心愿穿搭",
)

private fun countsLine(counts: Map<String, Int>): String =
    counts.entries.filter { it.value > 0 && COUNT_LABELS.containsKey(it.key) }
        .joinToString(" · ") { "${it.value}${COUNT_LABELS[it.key]}" }
        .ifEmpty { "（空）" }

// ---- 导出流程对话框（D2）----

@Composable
private fun ExportDialogs(exportUi: RecapViewModel.ExportUi, vm: RecapViewModel, onDone: (String, File) -> Unit) {
    when (exportUi) {
        RecapViewModel.ExportUi.Idle -> Unit
        is RecapViewModel.ExportUi.Running ->
            if (exportUi.total > 0) ProgressDialog("打包中 · 写入图片 ${exportUi.done}/${exportUi.total}")
            else ProgressDialog("打包中…")

        is RecapViewModel.ExportUi.Done -> {
            LaunchedEffect(exportUi) {
                onDone(exportUi.summary, exportUi.shareFile)
                vm.dismissExport()
            }
        }

        is RecapViewModel.ExportUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismissExport,
            title = { Text("导出失败") },
            text = { Text(exportUi.message) },
            confirmButton = { TextButton(onClick = vm::dismissExport) { Text("知道了") } },
        )
    }
}

@Composable
private fun ProgressDialog(text: String) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.size(30.dp))
                Spacer(Modifier.height(14.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
