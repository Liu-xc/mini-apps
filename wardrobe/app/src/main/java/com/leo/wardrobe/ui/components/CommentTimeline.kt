package com.leo.wardrobe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 评论时间线 + 输入（US-14），倒序展示 */
@Composable
fun CommentTimeline(
    notes: List<Note>,
    onSend: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    // it-029 C8：删除先确认（DESIGN.md §5.7——评论是不可再生的记忆，误触即失）
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    val haptics = rememberHaptics()  // it-027：发送确认轻震（DESIGN.md §4）
    // it-036 C12：日期统一 YYYY/MM/DD（原 MM/dd——与 W7 顶栏「穿搭 · 2026/09/24」两套格式废止；
    // createdAt 为完整时间戳，年份直接取到，无需补全）
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条评论？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target.id)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.forEach { note ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    dateFormat.format(Date(note.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    note.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 10.dp),  // it-033：与删除 × 间距拉开
                )
                IconButton(
                    onClick = { pendingDelete = note },
                    // it-028：44dp → it-033：触控基线 48dp
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "删除评论",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = {
                    if (input.isNotBlank()) {
                        haptics.tick()
                        onSend(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
