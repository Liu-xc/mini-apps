@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.leo.wardrobe.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.leo.libs.agent.AgentError
import com.leo.libs.agent.Message
import com.leo.libs.agent.Role
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.theme.editorialColors

/** W12 消息行（分组后的渲染单元） */
private sealed interface RowUi {
    data class Me(val text: String) : RowUi
    data class Ai(val text: String, val stamp: Long) : RowUi
    data class Tools(val names: List<String>, val results: List<Pair<String, String>>) : RowUi
}

private fun groupRows(messages: List<Message>): List<RowUi> = buildList {
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        when (m.role) {
            Role.User -> { add(RowUi.Me(m.text)); i++ }
            Role.Assistant -> {
                if (m.toolCalls.isNotEmpty()) {
                    val results = mutableListOf<Pair<String, String>>()
                    var j = i + 1
                    while (j < messages.size && messages[j].role == Role.Tool) {
                        results += (messages[j].toolCallId ?: "") to messages[j].text
                        j++
                    }
                    add(RowUi.Tools(m.toolCalls.map { it.name }, results))
                    i = j
                } else {
                    if (m.text.isNotBlank()) add(RowUi.Ai(m.text, m.createdAt))
                    i++
                }
            }
            else -> i++ // system / 游离 tool 不单独成行
        }
    }
}

private val SUGGESTIONS = listOf("配一套通勤装", "我有哪些外套", "最近穿得少吗")

/**
 * W12 对话页（it-041 阶段 B；it-043 O2/O3 收口）：
 * - 底部锚定（reverseLayout）：最新消息/流式/错误恒贴输入栏，修「顶部锚定空洞」（走查 C2）
 * - 空态垂直居中 + 可点示例 chip（走查 C1/C12），标题与占位措辞错开
 * - 工具条折叠为中文摘要可展开（走查 C3）；错误容器化紧贴失败轮次，Key/网络类直达设置（走查 C4）
 */
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    appVm: com.leo.wardrobe.ui.AppViewModel? = null,
) {
    val ec = editorialColors()
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val notices by vm.toolNotices.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val rows = remember(messages) { groupRows(messages) }
    val display = remember(rows) { rows.asReversed() } // reverseLayout：index0=底部=最新
    val isEmpty = rows.isEmpty() && streaming.isEmpty() && notices.isEmpty() && error == null
    // it-043 补遗：动作行条件提到外层作用域——状态读取若在 items 子项内，完成后
    // 重组可能被跳过（走查实测：完成后 chip 不出现，须重进页面）；外层读取 + Boolean
    // 参数变化能强制 items 内容重组
    val showActions = !running && error == null && display.firstOrNull() is RowUi.Ai
    // it-043 补遗（真因）：reverseLayout 下新消息插在 index0（视口锚定会把新项挤到可视区
    // 下方），消息数变化后必须主动滚回 index0——否则最新回复不可见（走查实测：UI 停在旧内容）
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    fun sendCurrent() {
        input.takeIf { it.isNotBlank() }?.let { vm.send(it); input = "" }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("穿搭顾问") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
        )

        LazyColumn(
            state = listState,
            reverseLayout = true, // it-043 O2：底部锚定，新内容永远贴输入栏
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        ) {
            if (isEmpty) {
                item(key = "empty") {
                    Column(
                        // Lazy 主轴无约束，fillMaxSize 不生效 → 必须 fillParentMaxSize 才能真居中
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // it-043 O2：居中 + 措辞与占位错开（占位=「输入问题…」）
                        EmptyState(
                            title = "今天想搭点什么？",
                            hint = "点下面的示例直接开始，也可以输入任意问题",
                        )
                        // it-043 O2：示例可点 chip（一键提问）
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 2,
                        ) {
                            SUGGESTIONS.forEach { s ->
                                OutlinedButton(
                                    onClick = { vm.send(s) },
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) { Text(s, style = MaterialTheme.typography.bodyMedium, color = ec.ink) }
                            }
                        }
                    }
                }
            } else {
                // it-043 O3：错误容器紧贴失败轮次（列表最底部=最新轮次下方）
                error?.let { err ->
                    item(key = "error") { ErrorRow(err, onRetry = vm::retry, onDismiss = vm::clearError, onOpenSettings = onOpenSettings) }
                }
                if (thinking || streaming.isNotEmpty()) {
                    item(key = "live") {
                        if (thinking && streaming.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text("思考中…", style = MaterialTheme.typography.bodyMedium, color = ec.inkFaint)
                            }
                        } else {
                            AiBubble(streaming + "▍")
                        }
                    }
                }
                if (notices.isNotEmpty()) {
                    item(key = "notices") {
                        ToolsRow(
                            names = notices.map { it.name },
                            results = notices.map { it.name to it.detail },
                            initialExpanded = true, // 进行中直接展示「查询中…」
                            live = true,
                        )
                    }
                }
                items(display.size, key = { "row-${display.size - 1 - it}" }) { idx ->
                    when (val row = display[idx]) {
                        is RowUi.Me -> MeBubble(row.text)
                        is RowUi.Ai -> AiBubble(
                            text = row.text,
                            // it-043 补遗（走查 06页 P2）：回复署名时间戳（旧会话 0 不显示）
                            stamp = row.stamp.takeIf { it > 0L },
                            // it-044 O6（走查 C11）：最新一条完整回复下挂复制/追问/重新生成动作
                            actions = if (showActions && idx == 0) {
                                {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                                    ) {
                                        ActionChipBtn("复制") {
                                            vm.copyReply(row.text)
                                            appVm?.toast("已复制回复")
                                        }
                                        ActionChipBtn("换个场合再推荐") { vm.send("换个场合再推荐一套") }
                                        ActionChipBtn("重新生成") { vm.regenerate() }
                                    }
                                }
                            } else null,
                        )
                        // it-043 O3：历史工具条默认折叠为中文摘要
                        is RowUi.Tools -> ToolsRow(row.names, row.results, initialExpanded = false)
                    }
                }
            }
        }

        // 输入栏（ime=Send 与应用内发送同一行为）
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("输入问题…") },
                enabled = !running,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendCurrent() }),
                modifier = Modifier.weight(1f),
            )
            if (running) {
                IconButton(onClick = { vm.stop() }) {
                    Icon(Icons.Rounded.Stop, contentDescription = "停止", tint = ec.ink)
                }
            } else {
                IconButton(
                    onClick = { sendCurrent() },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else ec.inkFaint,
                    )
                }
            }
        }
    }
}

/** it-043 O3：容器化错误条（图标 + 分类文案 + 重试/去设置/知道了） */
@Composable
private fun ErrorRow(
    error: AgentError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ec = editorialColors()
    val errorColor = MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ec.surface,
        border = BorderStroke(1.dp, errorColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = errorColor, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(error.userMessage, style = MaterialTheme.typography.bodyMedium, color = errorColor)
                Row {
                    TextButton(onClick = onRetry) { Text("重试") }
                    // it-043 O3：Key/网络类直达设置，恢复路径少一跳
                    if (error is AgentError.Auth || error is AgentError.Network) {
                        TextButton(onClick = onOpenSettings) { Text("去设置") }
                    }
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = ec.inkFaint)) {
                        Text("知道了")
                    }
                }
            }
        }
    }
}

@Composable
private fun MeBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AiBubble(text: String, stamp: Long? = null, actions: (@Composable () -> Unit)? = null) {
    val ec = editorialColors()
    Column {
        Row(Modifier.fillMaxWidth()) {
            Surface(
                color = ec.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, ec.hairline),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ec.ink,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        // it-043 补遗：来源辨识——「穿搭顾问 · HH:mm」（旧会话 createdAt=0 不显示）
        if (stamp != null) {
            val time = remember(stamp) {
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(stamp))
            }
            Text(
                "穿搭顾问 · $time",
                style = MaterialTheme.typography.labelSmall,
                color = ec.inkFaint,
                modifier = Modifier.padding(start = 6.dp, top = 3.dp),
            )
        }
        actions?.invoke()
    }
}

/** it-044 O6：回复下的小动作 chip（触控 ≥44dp） */
@Composable
private fun ActionChipBtn(label: String, onClick: () -> Unit) {
    val ec = editorialColors()
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, ec.hairline),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ec.ink)
    }
}

/**
 * it-043 O3：工具条——中文摘要头「查了衣橱 · n 次」+ 可点折叠展开（走查 C3）。
 * [initialExpanded] 用于进行中通知（直接展示查询进度）。
 */
@Composable
private fun ToolsRow(
    names: List<String>,
    results: List<Pair<String, String>>,
    initialExpanded: Boolean = false,
    live: Boolean = false,
) {
    val ec = editorialColors()
    var expanded by remember(names, results) { mutableStateOf(initialExpanded) }
    val labels = names.map { com.leo.wardrobe.ui.chat.toolLabel(it) }.distinct()
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ec.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (live) Modifier else Modifier
                            .padding(vertical = 4.dp),
                    ),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    // it-043 O3：中文摘要替代 raw 工具名（进行中=「正在查：…」）
                    if (live) "正在查：${labels.joinToString("、")}"
                    else "查了衣橱 · ${names.size} 次",
                    style = MaterialTheme.typography.labelLarge,
                    color = ec.ink,
                    modifier = Modifier.weight(1f),
                )
                if (!live) {
                    androidx.compose.material3.IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = ec.inkFaint,
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(if (expanded) 180f else 0f),
                        )
                    }
                }
            }
            if (expanded || live) {
                results.forEach { (_, detail) ->
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = ec.ink,
                            maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
