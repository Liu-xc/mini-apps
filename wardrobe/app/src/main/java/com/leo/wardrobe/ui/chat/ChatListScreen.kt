@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.data.chat.ChatSessionSummary
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.theme.editorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** W12 顶层顾问：无 Key 仍可阅读历史，但不显示发起入口。 */
@Composable
fun ChatListScreen(
    vm: ChatViewModel,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sessions by vm.sessions.collectAsState()
    val canChat by vm.canChat.collectAsState()
    val ec = editorialColors()
    LaunchedEffect(Unit) { vm.refreshSessions() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("穿搭顾问") },
            // it-062：默认 surface 纯白与纸面背景割裂，顶栏铺纸面色与页面同底
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ec.paper),
            actions = {
                if (canChat && sessions.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { vm.createSession(onOpenSession) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新对话")
                    }
                }
            },
        )
        if (sessions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                EmptyState(
                    title = if (canChat) "还没有对话" else "顾问暂时只读",
                    hint = if (canChat) "新建一段对话，问问你的衣橱" else "配置 API Key 后即可发起新的对话",
                )
                if (canChat) Button(onClick = { vm.createSession(onOpenSession) }) { Text("新对话") }
                else Button(onClick = onOpenSettings) { Text("去配置") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!canChat) {
                    item("readonly") {
                        Card(colors = CardDefaults.cardColors(containerColor = ec.surface)) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("配置 API Key 后可发起对话", style = MaterialTheme.typography.bodyMedium, color = ec.ink)
                                androidx.compose.material3.TextButton(onClick = onOpenSettings) { Text("去配置") }
                            }
                        }
                    }
                }
                items(sessions, key = { it.id }) { session ->
                    SessionCard(session, onClick = { onOpenSession(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ChatSessionSummary, onClick: () -> Unit) {
    val ec = editorialColors()
    // it-062：预览走 Markdown 解析口径扁平化，不裸露 ##/- 等标记（旧索引数据同样干净）
    val preview = remember(session.preview) { markdownPreviewText(session.preview) }
    // it-071 P2：SimpleDateFormat 每次组合新建有分配开销，记忆复用（同 OutfitThumb 先例）
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ec.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium, color = ec.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preview.ifBlank { "还没有消息" }, style = MaterialTheme.typography.bodyMedium, color = ec.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                // it-069 修4：并入全站 yyyy/MM/dd 日期口径（it-036 C12），保留时分
                dateFormat.format(Date(session.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = ec.inkFaint,
            )
        }
    }
}
