@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.wardrobe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * W4 添加/编辑衣物（US-01/02/13）：照片必填 + 名称/品类/颜色/描述/标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    vm: AppViewModel,
    itemId: String?,
    onBack: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val existing = remember(itemId, data) { itemId?.let { data.itemById(it) } }

    var importedFile by remember { mutableStateOf<String?>(null) } // 选择后立即导入落盘的文件名
    var importing by remember { mutableStateOf(false) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: WardrobeCategory.TOP) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color ?: "") }
    var desc by remember(existing?.id) { mutableStateOf(existing?.desc ?: "") }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags ?: emptyList()) }
    var photoMissing by remember { mutableStateOf(false) }

    val pickPhoto = rememberPhotoPicker { uri ->
        if (uri != null) {
            importing = true
            vm.importPhoto(uri) { file ->
                importing = false
                if (file != null) {
                    importedFile = file
                    photoMissing = false
                }
            }
        }
    }

    val hasPhoto = importedFile != null || existing != null

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (existing == null) "添加衣物" else "编辑衣物") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            },
            actions = {
                Button(
                    onClick = {
                        if (!hasPhoto) {
                            photoMissing = true
                        } else {
                            vm.saveItem(existing, importedFile, name, category, color, desc, tags) { ok ->
                                if (ok) onBack()
                            }
                        }
                    },
                    enabled = name.isNotBlank() && hasPhoto,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("保存") }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 照片预览（已落盘的本地文件）
            if (importedFile != null) {
                PhotoCard(
                    file = vm.imageFileOf(importedFile!!),
                    contentDescription = "新照片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                )
            } else if (existing != null) {
                PhotoCard(
                    file = vm.imageFileOf(existing.imageFile),
                    contentDescription = existing.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                )
            }
            Surface(
                onClick = { pickPhoto() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (photoMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = editorialColors().inkFaint,
                    )
                    Text(
                        when {
                            importing -> "正在导入照片…"
                            hasPhoto -> "点击更换照片（必填）"
                            else -> "📷 从相册选择照片（必填）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = editorialColors().ink,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                placeholder = { Text("如：白色牛津纺衬衫") },
            )

            Text("品类", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WardrobeCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label) },
                    )
                }
            }

            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("颜色") },
                placeholder = { Text("如：白色") },
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("描述（会拼进生图文案）") },
                placeholder = { Text("如：宽松棉质、纽扣领") },
            )

            Text("标签", style = MaterialTheme.typography.labelMedium, color = editorialColors().inkFaint)
            TagInput(tags = tags, onChange = { tags = it })

            Spacer(Modifier.height(32.dp))
        }
    }
}
