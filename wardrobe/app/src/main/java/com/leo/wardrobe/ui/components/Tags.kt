package com.leo.wardrobe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.TagPresets
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * 只读标签行：#通勤 #简约 …（横向滑动）。
 * it-033：[minChipHeight] >0 时 chip 高度提到该值（W5 详情页传 44dp，走查实测原 15dp），
 * 卡片内复用处保持默认 0dp=原紧凑形制不变。
 */
@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier, minChipHeight: Dp = 0.dp) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    Modifier
                        .heightIn(min = minChipHeight)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = editorialColors().inkFaint,
                    )
                }
            }
        }
    }
}

/** 标签筛选条：#全部 + 各标签（单选） */
@Composable
fun FilterChipsRow(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
            shape = RoundedCornerShape(6.dp),
        )
        options.forEach { tag ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(if (selected == tag) null else tag) },
                label = { Text("#$tag") },
                shape = RoundedCornerShape(6.dp),
            )
        }
    }
}

/**
 * 标签录入（US-13）：已选 chips（可移除）+ 预设快速点选 + 自定义输入。
 */
@Composable
fun TagInput(
    tags: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除 $tag",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(3.dp)
                                    .clickableTag { onChange(tags - tag) },
                            )
                        }
                    }
                }
            }
        }

        // 预设标签（未选中的）
        val remaining = TagPresets.quickPicks.filter { it !in tags }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            remaining.take(14).forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickableTag { if (tags.size < 10) onChange(tags + preset) },
                ) {
                    Text(
                        preset,
                        style = MaterialTheme.typography.labelMedium,
                        color = editorialColors().inkFaint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickableTag {
                    // 点击「+」追加当前输入的自定义标签
                    val t = input.trim()
                    if (t.isNotEmpty() && t !in tags && tags.size < 10) { onChange(tags + t); input = "" }
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add, contentDescription = null,
                        tint = editorialColors().inkFaint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("自定义", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入自定义标签，回车添加", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "添加标签",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickableTag {
                            val t = input.trim()
                            if (t.isNotEmpty() && t !in tags && tags.size < 10) { onChange(tags + t); input = "" }
                        },
                )
            },
        )
    }
}

private fun Modifier.clickableTag(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
