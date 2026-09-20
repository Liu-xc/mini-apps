@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.notesOf
import com.leo.wardrobe.domain.model.outfitById
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.CommentTimeline
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagInput
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.rememberPhotoPicker
import com.leo.wardrobe.ui.outfit.ExportSheet
import com.leo.wardrobe.ui.theme.editorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * W7 穿搭详情（US-09/10/13/14）：成品图横滑 + 组成单品 + 录入成品图 + 复制素材 + 评论。
 */
@Composable
fun OutfitDetailScreen(
    vm: AppViewModel,
    outfitId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val data by vm.data.collectAsState()
    val outfit = remember(outfitId, data) { data.outfitById(outfitId) }

    if (outfit == null) {
        onBack()
        return
    }

    val items = remember(data, outfit) { outfit.itemIds.mapNotNull { data.itemById(it) } }
    val notes = remember(data, outfit) { data.notesOf(NoteParent.OUTFIT, outfit.id) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }

    var showDelete by remember { mutableStateOf(false) }
    var showTagEdit by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf(outfit.tags) }

    val pickEffect = rememberPhotoPicker { uri ->
        if (uri != null) vm.importEffectImage(outfit, items.map { it.id }, uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("穿搭 · ${dateFormat.format(Date(outfit.createdAt))}") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { tagDraft = outfit.tags; showTagEdit = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑标签")
                }
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除穿搭")
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 主视觉（it-011 O7）：成品图横滑优先；无成品图时人体叙事拼贴兜底，
            // 「录入成品图」合并为拼贴右下角标，不再整行重复两个入口
            if (outfit.effectImages.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { outfit.effectImages.size })
                Box {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    ) { page ->
                        val img = outfit.effectImages[page]
                        PhotoCard(
                            file = vm.imageFileOf(img.file),
                            contentDescription = "成品效果图 ${page + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.86f),
                        )
                    }
                    if (outfit.effectImages.size > 1) {
                        // 当前页删除小按钮
                        IconButton(
                            onClick = {
                                vm.removeEffectImage(outfit.id, outfit.effectImages[pagerState.currentPage].file)
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除这张成品图",
                                tint = editorialColors().inkFaint,
                            )
                        }
                    }
                }
                // 指示点
                if (outfit.effectImages.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        repeat(outfit.effectImages.size) { i ->
                            Box(
                                Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage) editorialColors().accent
                                        else editorialColors().hairline,
                                    ),
                            )
                        }
                    }
                }
            } else {
                Box {
                    com.leo.wardrobe.ui.components.BodyCollage(
                        items = items,
                        imageFileOf = vm::imageFileOf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f),
                    )
                    // it-012：角标移拼贴右上，远离「未配X」空槽语义区
                    Surface(
                        onClick = { pickEffect() },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = editorialColors().accent,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                "录入成品图",
                                style = MaterialTheme.typography.labelMedium,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            if (outfit.tags.isNotEmpty()) {
                Row { TagRow(outfit.tags) }
            }

            Text("这套包含", style = MaterialTheme.typography.titleMedium, color = editorialColors().ink)
            items.forEach { item ->
                // it-011 O7：单品行可点直达衣物详情（spec US-10），chevron + 按压反馈
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    onClick = { onOpenItem(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        PhotoCard(
                            file = vm.imageFileOf(item.imageFile),
                            contentDescription = item.name,
                            corner = 10.dp,
                            mat = true,
                            modifier = Modifier.size(width = 44.dp, height = 54.dp),
                        )
                        Text(
                            "${item.category.label} · ${item.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = editorialColors().ink,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = editorialColors().inkFaint,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showExport = true }, modifier = Modifier.weight(1f)) {
                    Text("📋 复制长图")  // it-012：与搭配页同一套词
                }
            }

            HorizontalDivider(color = editorialColors().hairline)
            Text("💬 评论（${notes.size}）", style = MaterialTheme.typography.titleMedium, color = editorialColors().ink)
            CommentTimeline(
                notes = notes,
                onSend = { vm.addNote(NoteParent.OUTFIT, outfit.id, it) },
                onDelete = { vm.deleteNote(it) },
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这套穿搭？") },
            text = { Text("成品图与该穿搭的评论将一并删除，单品不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteOutfit(outfit.id)
                    showDelete = false
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }

    if (showTagEdit) {
        AlertDialog(
            onDismissRequest = { showTagEdit = false },
            title = { Text("穿搭标签") },
            text = {
                Column {
                    TagInput(tags = tagDraft, onChange = { tagDraft = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        TextButton(onClick = { showTagEdit = false }) { Text("取消") }
                        TextButton(onClick = {
                            vm.updateOutfitTags(outfit.id, tagDraft)
                            showTagEdit = false
                        }) { Text("保存") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    if (showExport) {
        ExportSheet(
            vm = vm,
            items = items,
            existingOutfit = outfit,
            onDismiss = { showExport = false },
        )
    }
}
