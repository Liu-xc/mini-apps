package com.leo.eats.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.eats.domain.model.TagPresets
import com.leo.eats.ui.theme.menuColors

/** 系统 Photo Picker（W4/W6 共用）：返回所选 Uri */
@Composable
fun rememberPhotoPicker(onResult: (Uri?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> onResult(uri) }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/** 只读标签行：#辣 #火锅 …（横向滑动） */
@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier) {
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
                Text(
                    "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = menuColors().inkFaint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
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

/** 标签录入：已选 chips（可移除）+ 预设快速点选 + 自定义输入（≤10 个） */
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
                                    .clickable { onChange(tags - tag) },
                            )
                        }
                    }
                }
            }
        }

        val remaining = TagPresets.quickPicks.filter { it !in tags }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            remaining.take(14).forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { if (tags.size < 10) onChange(tags + preset) },
                ) {
                    Text(
                        preset,
                        style = MaterialTheme.typography.labelMedium,
                        color = menuColors().inkFaint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable {
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
                        tint = menuColors().inkFaint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("自定义", style = MaterialTheme.typography.labelMedium, color = menuColors().inkFaint)
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入自定义标签，点 ＋ 添加", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "添加标签",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            val t = input.trim()
                            if (t.isNotEmpty() && t !in tags && tags.size < 10) { onChange(tags + t); input = "" }
                        },
                )
            },
        )
    }
}

/**
 * 照片条（W4/W5/W6）：既有文件与新选 Uri 统一为 model 列表，4:3 横滑。
 * onRemove 回调下标按展示顺序；onAdd 提供时显示添加块。
 */
@Composable
fun PhotoStrip(
    models: List<Any?>,
    modifier: Modifier = Modifier,
    onRemove: ((Int) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    if (models.isEmpty() && onAdd == null) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        models.forEachIndexed { i, model ->
            Box {
                AsyncImage(
                    model = model,
                    contentDescription = "照片 ${i + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 104.dp, height = 78.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                if (onRemove != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        // it-016：媒体角标命中区 36dp（DESIGN.md §2.5 例外档），视觉不变
                        modifier = Modifier
                            .padding(3.dp)
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clickable { onRemove(i) },
                    ) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除照片",
                                tint = menuColors().ink,
                                modifier = Modifier.size(18.dp).padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (onAdd != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(width = 104.dp, height = 78.dp)
                    .clickable { onAdd() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AddAPhoto,
                        contentDescription = "添加照片",
                        tint = menuColors().inkFaint,
                    )
                }
            }
        }
    }
}
