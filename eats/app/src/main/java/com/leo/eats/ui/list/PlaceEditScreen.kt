package com.leo.eats.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.placeById
import com.leo.eats.ui.AppViewModel
import com.leo.eats.ui.components.LinkChips
import com.leo.eats.ui.components.PhotoStrip
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.TagInput
import com.leo.eats.ui.components.label
import com.leo.eats.ui.components.rememberPhotoPicker
import com.leo.eats.ui.mapview.LocationPickerSheet
import com.leo.eats.ui.theme.menuColors
import java.io.File

/**
 * W4 添加/编辑食堂（US-01/02）：名称必填、类型单选、位置长按选点（自做可跳过）、
 * 评分可空、标签、照片多张、链接粘贴自动识别来源（ADR-009）、笔记。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(
    vm: AppViewModel,
    placeId: String?,
    onBack: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val existing = remember(placeId, data) { placeId?.let { data.placeById(it) } }

    // 表单状态（进入时一次性初始化，编辑保存成功后返回）
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: PlaceKind.RESTAURANT) }
    var cuisine by remember { mutableStateOf(existing?.cuisine.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location) }
    var address by remember { mutableStateOf(existing?.address.orEmpty()) }
    var rating by remember { mutableStateOf(existing?.rating) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(existing?.tags.orEmpty()) } }
    val photos = remember { mutableStateListOf<String>().apply { addAll(existing?.photos.orEmpty()) } }
    val newUris = remember { mutableStateListOf<String>() }
    val links = remember { mutableStateListOf<PlaceLink>().apply { addAll(existing?.links.orEmpty()) } }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }

    var showPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var linkLabel by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val photoPicker = rememberPhotoPicker { uri -> if (uri != null) newUris += uri.toString() }

    // 数据里已无此条（被删除）时退出
    LaunchedEffect(data, placeId) {
        if (placeId != null && data.placeById(placeId) == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "添加食堂" else "编辑食堂") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除食堂")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormLabel("名称 *")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("如：巷子深火锅 / 番茄炒蛋") },
                singleLine = true,
            )

            FormLabel("类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.label) },
                    )
                }
            }

            FormLabel("菜系 / 风味")
            OutlinedTextField(
                value = cuisine,
                onValueChange = { cuisine = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("火锅、日料、家常菜…（可选）") },
                singleLine = true,
            )

            FormLabel(if (kind == PlaceKind.HOME) "位置（自做可跳过）" else "位置")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showPicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = if (location != null) menuColors().accent else menuColors().inkFaint,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            location != null && address.isNotBlank() -> address
                            location != null -> "已选点 (%.4f, %.4f)".format(location!!.lat, location!!.lng)
                            else -> "长按地图选点（可选）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (location != null) menuColors().ink else menuColors().inkFaint,
                        modifier = Modifier.weight(1f),
                    )
                    if (location != null) {
                        TextButton(onClick = { location = null; address = "" }) { Text("清除") }
                    }
                }
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("地址文本（可选，手填）") },
                singleLine = true,
            )

            FormLabel("综合评分")
            RatingStars(rating = rating, onChange = { rating = it }, size = 26.dp)

            FormLabel("标签（忌口 / 风味 / 场景）")
            TagInput(tags = tags, onChange = { t -> tags.clear(); tags.addAll(t) })

            FormLabel("照片")
            PhotoStrip(
                models = photos.map { vm.imageFileOf(it) } + newUris,
                onRemove = { i ->
                    if (i < photos.size) photos.removeAt(i) else newUris.removeAt(i - photos.size)
                },
                onAdd = { photoPicker() },
            )

            FormLabel("链接（美团 / 大众点评分享）")
            OutlinedTextField(
                value = linkUrl,
                onValueChange = { linkUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴分享链接") },
                singleLine = true,
            )
            OutlinedTextField(
                value = linkLabel,
                onValueChange = { linkLabel = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("备注（如：双人套餐）（可选）") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val url = linkUrl.trim()
                            if (url.isNotEmpty() && links.none { it.url.trim() == url }) {
                                links += PlaceLink(url, linkLabel.trim())
                                linkUrl = ""; linkLabel = ""
                            } else if (url.isNotEmpty()) {
                                vm.toast("这条链接已经加过了")
                            }
                        },
                    ) { Icon(Icons.Rounded.Add, contentDescription = "添加链接") }
                },
            )
            if (links.isNotEmpty()) {
                LinkChips(
                    links = links,
                    onOpen = { url ->
                        vm.linkOpener.open(url) { vm.toast("没有可打开该链接的应用") }
                    },
                    onRemove = { l -> links.remove(l) },
                )
            }

            FormLabel("笔记")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("排队严重、错峰去…（可选）") },
                minLines = 2,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    saving = true
                    vm.savePlace(
                        existing = existing,
                        name = name,
                        kind = kind,
                        cuisine = cuisine,
                        location = location,
                        address = address,
                        rating = rating,
                        tags = tags.toList(),
                        keptPhotos = photos.toList(),
                        newPhotoUris = newUris.toList(),
                        links = links.toList(),
                        notes = notes,
                    ) { ok -> if (ok) onBack() else saving = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            ) { Text(if (existing == null) "保存" else "更新") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        LocationPickerSheet(
            initial = location,
            onConfirm = { loc -> location = loc; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${existing.name}」？") },
            text = { Text("将一并删除它的全部吃过记录与照片，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deletePlace(existing.id)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = menuColors().inkFaint,
    )
}
