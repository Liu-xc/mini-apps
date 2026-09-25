@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.leo.libs.agent.Message
import com.leo.libs.agent.Role
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.theme.editorialColors

/** W12 消息行（分组后的渲染单元） */
private sealed interface RowUi {
    data class Me(val text: String) : RowUi
    data class Ai(val text: String) : RowUi
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
                    if (m.text.isNotBlank()) add(RowUi.Ai(m.text))
                    i++
                }
            }
            else -> i++ // system / 游离 tool 不单独成行
        }
    }
}

/**
 * W12 对话页（it-041 阶段 B，US-41b/c）：流式打字机 + 工具调用条 + 分类错误重试；
 * 历史来自 FileSessionStore（杀进程可续）；白顶栏沉浸二级页（it-034 组）。
 */
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
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
    val isEmpty = rows.isEmpty() && streaming.isEmpty() && notices.isEmpty()
    // 自动滚到底（内容增长即触发；末项 = 行 + 进行中工具条 + 思考/流式）
    val totalItems = rows.size +
        (if (notices.isNotEmpty()) 1 else 0) +
        (if (thinking || streaming.isNotEmpty()) 1 else 0) +
        (if (isEmpty) 1 else 0)
    LaunchedEffect(totalItems, streaming.length) {
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "问问你的衣橱",
                        hint = "比如：配一套通勤装 · 我有哪些外套 · 最近是不是穿得太少",
                    )
                }
            }
            items(rows.size, key = { "row-$it" }) { idx ->
                when (val row = rows[idx]) {
                    is RowUi.Me -> MeBubble(row.text)
                    is RowUi.Ai -> AiBubble(row.text)
                    is RowUi.Tools -> ToolsRow(row.names, row.results)
                }
            }
            if (notices.isNotEmpty()) {
                item(key = "notices") {
                    ToolsRow(notices.map { it.name }, notices.map { it.name to it.detail })
                }
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
        }

        // 分类错误 + 重试（US-41b）
        val err = error
        if (err != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    err.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.retry() }) { Text("重试") }
                TextButton(onClick = { vm.clearError() }) { Text("知道了") }
            }
        }

        // 输入栏
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
                placeholder = { Text("问问你的衣橱…") },
                enabled = !running,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    input.takeIf { it.isNotBlank() }?.let { vm.send(it); input = "" }
                }),
                modifier = Modifier.weight(1f),
            )
            if (running) {
                IconButton(onClick = { vm.stop() }) {
                    Icon(Icons.Rounded.Stop, contentDescription = "停止", tint = ec.ink)
                }
            } else {
                IconButton(
                    onClick = { input.takeIf { it.isNotBlank() }?.let { vm.send(it); input = "" } },
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
private fun AiBubble(text: String) {
    val ec = editorialColors()
    Row(Modifier.fillMaxWidth()) {
        Surface(
            color = ec.surface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ec.hairline),
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
}

@Composable
private fun ToolsRow(names: List<String>, results: List<Pair<String, String>>) {
    val ec = editorialColors()
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "已查衣橱：${names.joinToString("、")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ec.ink,
                )
            }
            results.forEach { (_, detail) ->
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = ec.inkFaint,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
