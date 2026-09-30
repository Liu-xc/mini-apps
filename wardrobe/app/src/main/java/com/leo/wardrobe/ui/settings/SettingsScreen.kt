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
import com.leo.wardrobe.ui.components.SegmentedToggleRow
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.settings.SettingsViewModel.CheckState
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W11 设置页（it-041 阶段 A，US-41a）：模型连接（厂商/模型/Key/连通性自检）+ 模式状态。
 * 白顶栏沉浸二级页（it-034 组，路由加入根 Scaffold 去顶 inset 名单）；
 * Key 只出 mask（红线③）；it-050 的 Mock 环境使用隔离测试连接与缓存。
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
    var clearCacheAsk by remember { mutableStateOf(false) }
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
    val chatModels = vm.chatModelsOf(connection.presetId)
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
                // it-045：顶栏下缘→内容 20dp 全站节奏
                .padding(top = 20.dp, start = 20.dp, end = 20.dp),
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
                        if (vm.isDemo) "测试连接：Key 与真实衣橱隔离加密保存，导出数据不携带。"
                        else "BYOK 直连模型厂商：Key 加密存于本机，导出数据不携带，界面只显示尾码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ec.ink,
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

                    // 模型（it-077 统一目录：按 capability 过滤后的档位下拉；空 = 目录首个）
                    if (!isCustom && chatModels.size > 1) {
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
                                    connection.model.ifBlank { "默认 · ${chatModels.first().id}" },
                                    color = ec.ink,
                                )
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                            }
                            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("默认 · ${chatModels.first().id}") },
                                    onClick = {
                                        vm.resetCheck()
                                        vm.selectModel("")
                                        modelMenu = false
                                    },
                                )
                                chatModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.id)
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
                                            vm.selectModel(m.id)
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
                        label = { Text("API Key", color = ec.ink) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            Text(
                                when {
                                    keyMask != null -> "已保存 $keyMask · 留空保持不变"
                                    else -> "未配置"
                                },
                                color = ec.ink,
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
                            enabled = !running && !customIncomplete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (running) "自检中…" else "保存并自检")
                        }
                        if (keyMask != null) {
                            // it-043 O4（走查 C8）：回退动作中性弱色，与主 CTA 分层（仍带二次确认）
                            TextButton(
                                onClick = { clearAsk = true },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = ec.inkFaint,
                                ),
                            ) { Text("清除 Key") }
                        }
                    }

                    if (vm.isDemo) {
                        TextButton(
                            onClick = { clearCacheAsk = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = ec.inkFaint),
                        ) { Text("清除测试缓存") }
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

            // ---------- 生图模型卡（it-077 US-64c：与聊天卡同形制；同厂商共用一把 Key） ----------
            val imageConn by vm.imageConnection.collectAsState()
            val imageKeyMask by vm.imageKeyMask.collectAsState()
            var imageKeyInput by remember(imageConn.presetId) { mutableStateOf("") }
            var imagePresetMenu by remember { mutableStateOf(false) }
            var imageModelMenu by remember { mutableStateOf(false) }
            var clearImageKeyAsk by remember { mutableStateOf(false) }
            val isImageCustom = imageConn.presetId == SettingsViewModel.IMAGE_CUSTOM_ID
            val imageProviderName = if (isImageCustom) "自定义" else vm.imagePresetOptions.find { it.id == imageConn.presetId }?.displayName ?: imageConn.presetId
            val imageModels = vm.imageModelsOf(imageConn)
            val imageCustomIncomplete = isImageCustom &&
                (imageConn.customBaseUrl.isBlank() || imageConn.customModel.isBlank())
            val sharesChatKey = !isImageCustom && imageConn.presetId == connection.presetId

            Surface(
                shape = MaterialTheme.shapes.large,
                color = ec.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("生图模型", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                    Text(
                        "穿搭效果图的生成通道；选择与上方连接相同的厂商时共用同一把 Key。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ec.ink,
                    )

                    // 厂商
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("厂商", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { imagePresetMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            ) {
                                Text(imageProviderName, color = ec.ink)
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                            }
                            DropdownMenu(expanded = imagePresetMenu, onDismissRequest = { imagePresetMenu = false }) {
                                vm.imagePresetOptions.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.displayName) },
                                        onClick = {
                                            imageKeyInput = ""
                                            vm.selectImagePreset(p.id)
                                            imagePresetMenu = false
                                        },
                                        trailingIcon = {
                                            if (p.id == imageConn.presetId) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = "当前")
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("自定义…") },
                                    onClick = {
                                        imageKeyInput = ""
                                        vm.selectImagePreset(SettingsViewModel.IMAGE_CUSTOM_ID)
                                        imagePresetMenu = false
                                    },
                                    trailingIcon = {
                                        if (isImageCustom) Icon(Icons.Rounded.CheckCircle, contentDescription = "当前")
                                    },
                                )
                            }
                        }
                    }

                    // 模型（含档位与参考价快照——以厂商账单为准）
                    if (!isImageCustom && imageModels.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("模型", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                            TextButton(
                                onClick = { imageModelMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    imageConn.model.ifBlank {
                                        imageModels.first().id + " · ${imageModels.first().tier}"
                                    },
                                    color = ec.ink,
                                )
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = ec.inkFaint)
                            }
                            DropdownMenu(expanded = imageModelMenu, onDismissRequest = { imageModelMenu = false }) {
                                imageModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.id)
                                                Text(
                                                    buildString {
                                                        append(m.tier)
                                                        m.costPerImage?.let { append(" · 参考价 ¥$it/张") }
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = ec.inkFaint,
                                                )
                                            }
                                        },
                                        onClick = {
                                            vm.selectImageModel(m.id)
                                            imageModelMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 自定义生图厂商（OpenAI images 兼容）
                    if (isImageCustom) {
                        OutlinedTextField(
                            value = imageConn.customBaseUrl,
                            onValueChange = { vm.updateImageCustom(baseUrl = it) },
                            label = { Text("Base URL（含 /v1，images/generations 兼容）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = imageConn.customModel,
                            onValueChange = { vm.updateImageCustom(model = it) },
                            label = { Text("模型名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // API Key（同厂商与聊天连接共用；自定义生图为独立槽位）
                    OutlinedTextField(
                        value = imageKeyInput,
                        onValueChange = { imageKeyInput = it },
                        label = { Text("API Key", color = ec.ink) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            Text(
                                when {
                                    imageKeyMask != null && sharesChatKey -> "已保存 $imageKeyMask · 与聊天连接共用 · 留空保持不变"
                                    imageKeyMask != null -> "已保存 $imageKeyMask · 留空保持不变"
                                    else -> "未配置"
                                },
                                color = ec.ink,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 生图没有免费 ping（拍板：不做付费自检）——保存即校验格式，首次生成时验证
                        Button(
                            onClick = { vm.saveImageConnection(imageConn, imageKeyInput) },
                            enabled = !imageCustomIncomplete,
                            modifier = Modifier.weight(1f),
                        ) { Text("保存") }
                        if (imageKeyMask != null) {
                            TextButton(
                                onClick = { clearImageKeyAsk = true },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = ec.inkFaint,
                                ),
                            ) { Text("清除 Key") }
                        }
                    }
                }
            }

            if (clearImageKeyAsk) {
                AlertDialog(
                    onDismissRequest = { clearImageKeyAsk = false },
                    title = { Text("清除生图连接的 Key？") },
                    text = { Text("清除后需要重新粘贴 API Key 才能生成穿搭效果图；与聊天连接共用的厂商会一并失效。") },
                    confirmButton = {
                        TextButton(onClick = {
                            clearImageKeyAsk = false
                            imageKeyInput = ""
                            vm.clearImageKey(imageConn.presetId)
                        }) { Text("清除") }
                    },
                    dismissButton = { TextButton(onClick = { clearImageKeyAsk = false }) { Text("取消") } },
                )
            }

            // ---------- 用量卡（US-41d；it-077 增生图张数） ----------
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
                                            if (usage.images > 0) {
                                                "输入 ${usage.promptTokens} · 输出 ${usage.completionTokens} · 生图 ${usage.images} 张"
                                            } else {
                                                "输入 ${usage.promptTokens} · 输出 ${usage.completionTokens}"
                                            },
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

            // ---------- 外观卡（it-070 US-61：跟随系统/亮色/暗色） ----------
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ec.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleLarge, color = ec.ink)
                    val mode by vm.themeMode.collectAsState()
                    val modeLabels = listOf("跟随系统", "亮色", "暗色")
                    val modeValues = listOf("system", "light", "dark")
                    SegmentedToggleRow(
                        selectedIndex = modeValues.indexOf(mode).coerceAtLeast(0),
                        onSelect = { vm.setThemeMode(modeValues[it]) },
                        count = modeLabels.size,
                        modifier = Modifier.fillMaxWidth(),
                    ) { i ->
                        Text(modeLabels[i], style = MaterialTheme.typography.labelMedium, color = ec.ink)
                    }
                    Text(
                        "选择后立即生效并记住",
                        style = MaterialTheme.typography.bodySmall,
                        color = ec.inkFaint,
                    )
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
                    // it-043 补遗（走查 02页 P2）：演示入口从「灰字长句」改为与
                    // 「数据模式」同构的 label/value 行 + 独立短句提示——层级拆开、去掉重复前缀
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("演示模式", style = MaterialTheme.typography.titleMedium, color = ec.ink)
                            Text(
                                if (vm.isDemo) "已开启" else "未开启",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ec.inkFaint,
                            )
                        }
                    }
                    if (!vm.isDemo) {
                        Text(
                            "入口：衣橱页标题 3 秒内连点 5 次",
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
    if (clearCacheAsk) {
        AlertDialog(
            onDismissRequest = { clearCacheAsk = false },
            title = { Text("清除测试缓存？") },
            text = { Text("将删除本机 Mock 顾问缓存；下次相同请求会重新调用模型。") },
            confirmButton = {
                TextButton(onClick = { clearCacheAsk = false; vm.clearMockChatCache() }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { clearCacheAsk = false }) { Text("取消") } },
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
