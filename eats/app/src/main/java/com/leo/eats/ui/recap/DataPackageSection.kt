@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.eats.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.leo.eats.domain.model.CollectionDiff
import com.leo.eats.domain.model.ImportMode
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.theme.menuColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** it-014 O4：符号分层色（与 wardrobe it-026 同一套：＋绿 ↻琥珀 ＝灰，✗红 ✓绿） */
private val SymAdd = Color(0xFF2E7D32)
private val SymUpdate = Color(0xFFB8860B)
private val SymKeep = Color(0xFF86909C)
private val SymCheck = Color(0xFF2E7D32)
/** it-014 O1：替换危险态底色（警示红浅底） */
private val DangerTint = Color(0xFFFDECEC)

/**
 * W7「数据」小节（it-012，线框 D1–D7 与 wardrobe it-024 共用，差异见其注记 12）：
 * 导出/导入入口卡 + 导入导出全流程对话框。状态机在 [AppViewModel]；本文件只渲染。
 * it-014 交互加固与 wardrobe it-026 同步。
 */
@Composable
fun DataPackageSection(
    vm: AppViewModel,
    demo: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToast: (String) -> Unit,
    onExportDone: (summary: String, shareFile: File) -> Unit,
) {
    val importUi by vm.importUi.collectAsState()
    val exportUi by vm.exportUi.collectAsState()
    val mc = menuColors()

    Column {
        Text("数据", style = MaterialTheme.typography.titleMedium, color = mc.ink)
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = mc.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                DataRow(
                    icon = { Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = mc.accent, modifier = Modifier.size(22.dp)) },
                    title = "导出数据包",
                    sub = "zip 备份 · 可交给 AI 加工后导回",
                    enabled = !demo && exportUi !is AppViewModel.ExportUi.Running,
                    onClick = onExport,
                )
                DataRow(
                    icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = mc.accent, modifier = Modifier.size(22.dp)) },
                    title = "导入数据包",
                    // it-014 O5：入口副标题补覆盖风险暗示
                    sub = "合并打标结果 · 整包恢复（覆盖前确认）",
                    enabled = !demo && importUi !is AppViewModel.ImportUi.Checking && importUi !is AppViewModel.ImportUi.Running,
                    onClick = onImport,
                )
                if (demo) {
                    Text(
                        "演示模式下不可用，不触碰真实数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = mc.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    ImportDialogs(importUi = importUi, vm = vm, onToast = onToast, onExport = onExport)
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
    val mc = menuColors()
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
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) mc.ink else mc.inkFaint)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) mc.inkFaint else mc.hairline,
        )
    }
}

// ---- 导入流程对话框（D3 ⑤ – D7）----

@Composable
private fun ImportDialogs(importUi: AppViewModel.ImportUi, vm: AppViewModel, onToast: (String) -> Unit, onExport: () -> Unit) {
    when (importUi) {
        AppViewModel.ImportUi.Checking -> ProgressDialog("校验数据包…")

        is AppViewModel.ImportUi.Running ->
            ProgressDialog("导入中 · 处理图片 ${importUi.done}/${importUi.total}")

        is AppViewModel.ImportUi.Rejected -> AlertDialog(
            onDismissRequest = vm::dismissImport,
            title = { Text("无法导入此数据包") },
            text = {
                Column {
                    importUi.reasons.forEach { r ->
                        // it-014 O4：✗ 行 error 色
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) { append("✗ ") }
                                append(r)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    // it-014 O4：✓ 安抚行绿色
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = SymCheck, fontWeight = FontWeight.Bold)) { append("✓ ") }
                            append("本地数据未做任何改动")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissImport) { Text("知道了") } },
        )

        is AppViewModel.ImportUi.Confirm -> ConfirmImportDialog(importUi, vm, onExport)

        is AppViewModel.ImportUi.Done -> {
            LaunchedEffect(importUi) {
                onToast("导入完成 · 新增 ${importUi.added} · 更新 ${importUi.updated} · 已即时生效")
                vm.consumeImportDone()
            }
        }

        AppViewModel.ImportUi.Idle -> Unit
    }
}

@Composable
private fun ConfirmImportDialog(state: AppViewModel.ImportUi.Confirm, vm: AppViewModel, onExport: () -> Unit) {
    var replace by remember { mutableStateOf(false) }
    var askReplace by remember { mutableStateOf(false) }
    val mc = menuColors()

    AlertDialog(
        onDismissRequest = vm::dismissImport,
        title = { Text("数据包预览") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("📦 ${state.fileName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                val time = remember(state.exportedAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(state.exportedAt))
                }
                val from = if (state.generator.startsWith("eats")) "来自 ${state.generator}"
                else "来自 ${state.generator} · AI 加工包"
                Text("$from · $time", style = MaterialTheme.typography.labelSmall, color = mc.inkFaint)
                Spacer(Modifier.height(12.dp))

                // it-014 O1①：替换模式警示胶囊常驻（切回合并即消失）
                if (replace) {
                    Surface(shape = RoundedCornerShape(8.dp), color = DangerTint) {
                        Text(
                            "当前：替换模式",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!replace) {
                    Text("合并模式将发生：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    diffAnnotated("家", state.diff.places)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                    }
                    diffAnnotated("笔记录", state.diff.visits)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                    }
                    if (state.diff.locallyNewer > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "⚠ ${state.diff.locallyNewer} 家在导出后被本地修改过，导入将覆盖这些修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = SymUpdate,
                        )
                    }
                } else {
                    Text("替换模式将发生：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    // it-014 O2：删除/导入两行口径对称，各带图片数
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("✕ ") }
                            append("清除  本地${countsLine(state.localCounts)} · ${state.localImages} 图")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = SymAdd, fontWeight = FontWeight.Bold)) { append("＋ ") }
                            append("导入  包内${countsLine(state.packageCounts)} · ${state.packageImages} 图")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(14.dp))
                SelectRow(
                    selected = !replace,
                    label = "合并（推荐）",
                    sub = "同 id 条目以包内为准，本地多出的保留",
                ) { replace = false }
                SelectRow(
                    selected = replace,
                    label = "替换全部",
                    sub = "清空本地后导入包内内容",
                    danger = true,
                ) { replace = true }
            }
        },
        confirmButton = {
            // it-014 O1③：确认按钮随模式变文案并着警示色
            TextButton(
                onClick = { if (replace) askReplace = true else vm.applyImport(ImportMode.MERGE) },
                colors = if (replace) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(if (replace) "替换…" else "导入") }
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
                Column {
                    // it-014 O2：二次确认文案带图片数
                    Text(
                        "将删除本地${countsLine(state.localCounts)} · ${state.localImages} 张图，" +
                            "替换为包内${countsLine(state.packageCounts)} · ${state.packageImages} 张图。\n" +
                            "此操作无法撤销，建议先导出一份当前数据再替换。",
                    )
                    Spacer(Modifier.height(12.dp))
                    // it-014 O3：「建议先导出」升为可执行按钮（SAF 保存后回到本对话框）
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                    ) { Text("先导出一份当前数据（推荐）") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askReplace = false
                    vm.applyImport(ImportMode.REPLACE)
                }) { Text("仍要替换", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askReplace = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SelectRow(selected: Boolean, label: String, sub: String, danger: Boolean = false, onClick: () -> Unit) {
    val highlight = selected && danger
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) DangerTint else Color.Transparent)
            .border(
                width = if (highlight) 2.dp else 0.dp,
                color = if (highlight) MaterialTheme.colorScheme.error else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = if (highlight) {
                RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
            } else {
                RadioButtonDefaults.colors()
            },
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) MaterialTheme.colorScheme.error else menuColors().inkFaint,
            )
        }
    }
}

private data class DiffPart(val symbol: String, val color: Color, val text: String)

private fun diffParts(d: CollectionDiff): List<DiffPart> = buildList {
    if (d.added > 0) add(DiffPart("＋", SymAdd, "新增 ${d.added}"))
    if (d.updated > 0) add(DiffPart("↻", SymUpdate, "更新 ${d.updated}"))
    if (d.kept > 0) add(DiffPart("＝", SymKeep, "保留 ${d.kept}"))
}

/** it-014 O4：差异行符号着色加粗（＋绿 ↻琥珀 ＝灰），数字保持正文色 */
private fun diffAnnotated(label: String, d: CollectionDiff): androidx.compose.ui.text.AnnotatedString? {
    if (d.added == 0 && d.updated == 0 && d.localOnly == 0 && d.unchanged == 0) return null
    val parts = diffParts(d)
    if (parts.isEmpty()) return null
    return buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            if (i > 0) append(" · ")
            withStyle(SpanStyle(color = p.color, fontWeight = FontWeight.Bold)) { append(p.symbol) }
            append(p.text)
        }
        append(" $label")
    }
}

private val COUNT_LABELS = linkedMapOf("places" to "家", "visits" to "笔记录")

private fun countsLine(counts: Map<String, Int>): String =
    counts.entries.filter { it.value > 0 && COUNT_LABELS.containsKey(it.key) }
        .joinToString(" · ") { "${it.value}${COUNT_LABELS[it.key]}" }
        .ifEmpty { "（空）" }

// ---- 导出流程对话框（D2）----

@Composable
private fun ExportDialogs(exportUi: AppViewModel.ExportUi, vm: AppViewModel, onDone: (String, File) -> Unit) {
    when (exportUi) {
        AppViewModel.ExportUi.Idle -> Unit
        is AppViewModel.ExportUi.Running ->
            if (exportUi.total > 0) ProgressDialog("打包中 · 写入图片 ${exportUi.done}/${exportUi.total}")
            else ProgressDialog("打包中…")

        is AppViewModel.ExportUi.Done -> {
            LaunchedEffect(exportUi) {
                onDone(exportUi.summary, exportUi.shareFile)
                vm.dismissExport()
            }
        }

        is AppViewModel.ExportUi.Failed -> AlertDialog(
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
