package com.leo.wardrobe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.BuildConfig
import com.leo.wardrobe.data.mock.DemoMode
import com.leo.wardrobe.ui.components.CountUpText
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.settings.SettingsViewModel.CheckState
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W11 设置页（it-041 阶段 A，US-41a）：模型连接（厂商/模型/Key/连通性自检）+ 模式状态。
 * 白顶栏沉浸二级页（it-034 组，路由加入根 Scaffold 去顶 inset 名单）；
 * Key 只出 mask（红线③），演示模式禁用输入（红线②）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit = {},
) {
    val ec = editorialColors()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    val connection by vm.connection.collectAsState()
    val keyMask by vm.keyMask.collectAsState()
    val check by vm.check.collectAsState()

    var keyInput by remember(connection.presetId) { mutableStateOf("") }
    var presetMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }
    var exitDemoAsk by remember { mutableStateOf(false) }

    // 自检成功 = 关键确认动作，一次轻震（DESIGN.md §4；Running→Success 边沿触发）
    var prevCheck by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    LaunchedEffect(check) {
        if (check is CheckState.Success && prevCheck is CheckState.Running) haptics.confirm()
        prevCheck = check
    }
    // 每次进入刷新用量（对话后返回设置页即新值）
    LaunchedEffect(Unit) { vm.refreshUsage() }

    val preset = vm.presetOptions.find { it.id == connection.presetId }
    val isCustom = connection.presetId == SettingsViewModel.CUSTOM_ID
    val presetName = preset?.displayName ?: if (isCustom) "自定义" else connection.presetId
    val customIncomplete = isCustom &&
        (connection.customBaseUrl.isBlank() || connection.customModel.isBlank())
    val running = check is CheckState.Running

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- 模型连接卡 ----------
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ec.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("模型连接", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                    Text(
                        "BYOK 直连模型厂商：Key 加密存于本机，导出数据不携带，界面只显示尾码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ec.inkFaint,
                    )

                    // 厂商
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("厂商", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // it-044 O5（走查 C9）：内容零水平内边距 → caret 右缘与输入框右缘同线
                            TextButton(
                                onClick = { presetMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            ) {
                                Text(presetName, color = ec.ink)
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                            }
                            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                                vm.presetOptions.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.displayName) },
                                        onClick = {
                                            vm.resetCheck()
                                            keyInput = ""
                                            vm.selectPreset(p.id)
                                            presetMenu = false
                                        },
                                        trailingIcon = {
                                            if (p.id == connection.presetId) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = "当前")
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("自定义…") },
                                    onClick = {
                                        vm.resetCheck()
                                        keyInput = ""
                                        presetMenu = false
                                        vm.selectPreset(SettingsViewModel.CUSTOM_ID)
                                    },
                                    trailingIcon = {
                                        if (isCustom) Icon(Icons.Rounded.CheckCircle, contentDescription = "当前")
                                    },
                                )
                            }
                        }
                    }

                    // 模型（预设项且多模型时给选择；空 = preset 默认）
                    if (!isCustom && preset != null && preset.models.size > 1) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("模型", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                            TextButton(
                                onClick = { modelMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    connection.model.ifBlank { "默认 · ${preset.defaultModel}" },
                                    color = ec.ink,
                                )
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                            }
                            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("默认 · ${preset.defaultModel}") },
                                    onClick = {
                                        vm.resetCheck()
                                        vm.selectModel("")
                                        modelMenu = false
                                    },
                                )
                                preset.models.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.name)
                                                if (m.tier.isNotBlank()) {
                                                    Text(
                                                        m.tier,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = ec.inkFaint,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            vm.resetCheck()
                                            vm.selectModel(m.name)
                                            modelMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 自定义厂商配置
                    if (isCustom) {
                        OutlinedTextField(
                            value = connection.customBaseUrl,
                            onValueChange = {
                                vm.resetCheck()
                                vm.updateCustom(baseUrl = it)
                            },
                            label = { Text("Base URL（含 /v1）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = connection.customModel,
                            onValueChange = {
                                vm.resetCheck()
                                vm.updateCustom(model = it)
                            },
                            label = { Text("模型名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // API Key
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            vm.resetCheck()
                            keyInput = it
                        },
                        label = { Text("API Key") },
                        singleLine = true,
                        enabled = !vm.isDemo,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            Text(
                                when {
                                    vm.isDemo -> "演示模式不保存 Key"
                                    keyMask != null -> "已保存 $keyMask · 留空保持不变"
                                    else -> "未配置"
                                },
                                color = ec.inkFaint,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 自检状态（it-043 O4）：容器化状态行；Idle 时回落到持久化的上次结果（走查 C6）
                    if (check is CheckState.Running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        val last by vm.lastCheck.collectAsState()
                        val shown: Triple<Boolean, String, Long>? = when (val c = check) {
                            is CheckState.Success -> Triple(true, c.detail, System.currentTimeMillis())
                            is CheckState.Failure -> Triple(false, c.message, System.currentTimeMillis())
                            CheckState.Idle -> last?.let { Triple(it.ok, it.detail, it.at) }
                            else -> null
                        }
                        shown?.let { (ok, detail, at) ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (ok) ec.ink else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        remember(at) {
                                            java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(at))
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ec.inkFaint,
                                    )
                                }
                            }
                        }
                    }

                    // 动作行
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { vm.saveAndCheck(connection, keyInput) },
                            enabled = !running && !customIncomplete && !vm.isDemo,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (running) "自检中…" else "保存并自检")
                        }
                        if (keyMask != null && !vm.isDemo) {
                            // it-043 O4（走查 C8）：回退动作中性弱色，与主 CTA 分层（仍带二次确认）
                            TextButton(
                                onClick = { clearAsk = true },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = ec.inkFaint,
                                ),
                            ) { Text("清除 Key") }
                        }
                    }

                    // it-041 阶段 B：对话入口（W12）
                    OutlinedButton(
                        onClick = onOpenChat,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("开始对话")
                    }
                }
            }

            // ---------- 用量卡（US-41d） ----------
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ec.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("用量", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                    val totals by vm.usage.collectAsState()
                    if (totals.isEmpty()) {
                        Text("暂无对话用量", style = MaterialTheme.typography.bodyMedium, color = ec.inkFaint)
                    } else {
                        totals.entries
                            .sortedBy { it.key.first + it.key.second }
                            .forEach { entry ->
                                val (presetId, model) = entry.key
                                val usage = entry.value
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${vm.presetLabel(presetId)} · $model",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ec.ink,
                                        )
                                        Text(
                                            "输入 ${usage.promptTokens} · 输出 ${usage.completionTokens}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ec.inkFaint,
                                        )
                                    }
                                    // 数字 count-up（DESIGN.md 红线⑨）；it-043 O4：标注「累计」口径（走查 C6）
                                    CountUpText(
                                        target = usage.totalTokens,
                                        format = { "累计 $it tokens" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ec.ink,
                                    )
                                }
                            }
                    }
                }
            }

            // ---------- 模式与关于卡 ----------
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ec.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("数据模式", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                            Text(
                                if (vm.isDemo) "演示模式 · 内置数据不落盘" else "正常 · 本机真实数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ec.inkFaint,
                            )
                        }
                        if (vm.isDemo) {
                            TextButton(onClick = { exitDemoAsk = true }) { Text("退出演示模式") }
                        }
                    }
                    if (!vm.isDemo) {
                        Text(
                            "演示模式入口：衣橱页标题 3 秒内连点 5 次。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ec.inkFaint,
                        )
                    }
                    Text(
                        // it-043 O4（走查 C3）：版本行去 ADR 编号等内部决策黑话
                        "版本 ${BuildConfig.VERSION_NAME} · 联网仅用于模型对话与自检",
                        style = MaterialTheme.typography.labelMedium,
                        color = ec.inkFaint,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    // 清除 Key（破坏性，二次确认——DESIGN.md §5 红线⑦）
    if (clearAsk) {
        AlertDialog(
            onDismissRequest = { clearAsk = false },
            title = { Text("清除已保存的 Key？") },
            text = { Text("清除后需要重新粘贴 API Key 才能使用模型功能。") },
            confirmButton = {
                TextButton(onClick = {
                    clearAsk = false
                    keyInput = ""
                    vm.clearKey(connection.presetId)
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { clearAsk = false }) { Text("取消") } },
        )
    }

    // 退出演示模式（重启进程切数据源，同 W3 先例）
    if (exitDemoAsk) {
        AlertDialog(
            onDismissRequest = { exitDemoAsk = false },
            title = { Text("退出演示模式？") },
            text = { Text("返回真实衣橱，应用将自动重启。") },
            confirmButton = {
                TextButton(onClick = {
                    exitDemoAsk = false
                    DemoMode.setAndRestart(context, false)
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { exitDemoAsk = false }) { Text("取消") } },
        )
    }
}
