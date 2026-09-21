@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.outfit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.theme.editorialColors

private val EMOJI_CHOICES = listOf("👨", "👩", "🧒", "👶", "🙂", "🧑", "👵", "👴")

/**
 * W2 角色切换（US-12）：列表 + 新建 + 管理（改名/emoji/删除需确认）。
 */
@Composable
fun PersonSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val data by vm.data.collectAsState()
    val current by vm.currentPerson.collectAsState()
    var manageMode by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Person?>(null) }
    var deleteTarget by remember { mutableStateOf<Person?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "切换衣橱",
                style = MaterialTheme.typography.titleLarge,
                color = editorialColors().ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn {
                items(data.persons.size, key = { data.persons[it].id }) { index ->
                    val p = data.persons[index]
                    val isCurrent = p.id == current?.id
                    // it-011 O9：当前角色浅底 + 「✓ 使用中」，替代 6px 绿点
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent && !manageMode) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                            )
                            .clickable {
                                if (manageMode) {
                                    editTarget = p
                                } else {
                                    vm.switchPerson(p.id)
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(p.emoji, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = editorialColors().ink,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                        // it-012：✓使用中紧跟名字（R2：不再悬在行末）
                        if (isCurrent && !manageMode) {
                            Text(
                                " ✓ 使用中",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (manageMode) {
                            IconButton(onClick = { editTarget = p }) {
                                Icon(Icons.Outlined.Edit, "编辑", tint = editorialColors().inkFaint)
                            }
                            IconButton(onClick = { deleteTarget = p }) {
                                Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            // it-011 O9：新建=实心主按钮，管理=文字入口
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) { Text("＋ 新建角色") }
                TextButton(onClick = { manageMode = !manageMode }) {
                    Text(if (manageMode) "完成" else "管理")
                }
            }
        }
    }

    val haptics = rememberHaptics()  // it-028：角色保存确认触感（DESIGN.md §4）
    if (showCreate) {
        PersonEditDialog(
            vm = vm,
            title = "新建角色",
            initialName = "",
            initialEmoji = "🙂",
            onDismiss = { showCreate = false },
            onConfirm = { name, emoji, _ ->
                haptics.confirm()
                vm.addPerson(name, emoji)
                showCreate = false
            },
        )
    }
    editTarget?.let { target ->
        PersonEditDialog(
            vm = vm,
            title = "编辑角色",
            initialName = target.name,
            initialEmoji = target.emoji,
            initialRefPhoto = target.refImageFile,
            showRefPhoto = true,
            onDismiss = { editTarget = null },
            onConfirm = { name, emoji, refPhoto ->
                haptics.confirm()
                vm.updatePerson(target.id, name, emoji)
                // it-017：参考照 diff 提交——照片生命周期（旧文件清理）在 Repository
                when {
                    refPhoto == target.refImageFile -> {}
                    refPhoto == null -> vm.removePersonRefPhoto(target.id)
                    else -> vm.setPersonRefPhoto(target.id, refPhoto)
                }
                editTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        val itemCount = data.items.count { it.personId == target.id }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${target.emoji} ${target.name}？") },
            text = { Text("将连带删除该角色的 $itemCount 件衣物、全部穿搭与评论，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePerson(target.id)
                    deleteTarget = null
                    onDismiss()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PersonEditDialog(
    vm: AppViewModel,
    title: String,
    initialName: String,
    initialEmoji: String,
    initialRefPhoto: String? = null,
    showRefPhoto: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, refPhoto: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    // it-017：对话框内暂存参考照（选择瞬间导入落盘，it-002+1 教训），确定时 diff 提交
    var refPhoto by remember { mutableStateOf(initialRefPhoto) }
    val pickRefPhoto = rememberPhotoPicker { uri ->
        if (uri != null) vm.importPhoto(uri) { file -> if (file != null) refPhoto = file }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (showRefPhoto) {
                    RefPhotoSection(
                        current = refPhoto,
                        onPick = { pickRefPhoto() },
                        onRemove = { refPhoto = null },
                        fileOf = { vm.imageFileOf(it) },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EMOJI_CHOICES.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { emoji = e }
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            contentAlignment = Alignment.Center,
                        ) { Text(e) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, emoji, refPhoto) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** it-017：形象参考照区（可选）——未设置虚位引导，已设置缩略图 + 更换/移除 */
@Composable
private fun RefPhotoSection(
    current: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    fileOf: (String) -> java.io.File?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "形象参考照（可选）",
                style = MaterialTheme.typography.labelLarge,
                color = editorialColors().ink,
            )
            Text(
                "导出时附给生图 Agent 做形象参考，生成更像本人（全身照最佳，头像也可以）",
                style = MaterialTheme.typography.labelSmall,
                color = editorialColors().inkFaint,
            )
            if (current == null) {
                Surface(
                    onClick = onPick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp, MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = editorialColors().inkFaint,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("选择照片", style = MaterialTheme.typography.bodyMedium, color = editorialColors().ink)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhotoCard(
                        file = fileOf(current),
                        contentDescription = "形象参考照",
                        corner = 10.dp,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        // ui-audit P2-1：TextButton 提供按压态与形状，纯文字可点性弱
                        TextButton(
                            onClick = onPick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text("更换", style = MaterialTheme.typography.labelLarge) }
                        TextButton(
                            onClick = onRemove,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text("移除", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
