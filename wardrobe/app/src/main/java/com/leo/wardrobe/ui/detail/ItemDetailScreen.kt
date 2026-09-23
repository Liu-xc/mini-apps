@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.notesOf
import com.leo.wardrobe.domain.model.outfitsContaining
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.CommentTimeline
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.sharedPhoto
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * W5 衣物详情（US-11/13/14）：大图（共享元素进入）+ 相关穿搭反查 + 评论。
 */
@Composable
fun ItemDetailScreen(
    vm: AppViewModel,
    itemId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenOutfit: (String) -> Unit,
) {
    val data by vm.data.collectAsState()
    val item = remember(itemId, data) { data.itemById(itemId) }

    if (item == null) {
        onBack()
        return
    }

    val related = remember(data, itemId) { data.outfitsContaining(itemId) }
    val notes = remember(data, itemId) { data.notesOf(NoteParent.ITEM, itemId) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { onEdit(item.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // it-011 C5：统一浅底衬纸容器——固定比例/圆角/淡底，无论原图背景如何
            PhotoCard(
                file = vm.imageFileOf(item.imageFile),
                contentDescription = item.name,
                corner = 20.dp,
                mat = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .sharedPhoto("item-photo-${item.id}"),
            )
            Text(
                item.name,
                style = MaterialTheme.typography.displaySmall,
                color = editorialColors().ink,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                listOfNotNull(item.color.takeIf { it.isNotBlank() }, item.desc.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = editorialColors().inkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (item.tags.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp)) { TagRow(item.tags) }
            }

            if (related.isNotEmpty()) {
                Text(
                    "这件衣服穿过这些穿搭",
                    style = MaterialTheme.typography.titleMedium,
                    color = editorialColors().ink,
                    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
                )
                // it-011 R6-P1：放大为可点卡片，「点开看整套」预期明确
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp),
                ) {
                    items(related.size, key = { related[it].id }) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(150.dp),
                        ) {
                            OutfitThumb(vm, related[index], showDate = false, onClick = { onOpenOutfit(related[index].id) })
                            Text(
                                "点开看整套 ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = editorialColors().inkFaint,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = editorialColors().hairline, modifier = Modifier.padding(vertical = 18.dp))
            Text(
                "评论（${notes.size}）",
                style = MaterialTheme.typography.titleMedium,
                color = editorialColors().ink,
            )
            CommentTimeline(
                notes = notes,
                onSend = { vm.addNote(NoteParent.ITEM, itemId, it) },
                onDelete = { vm.deleteNote(it) },
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 穿搭缩略图（W5 相关穿搭 / W8 记录网格共用）：成品图优先，否则 2x2 单品拼贴 */
@Composable
fun OutfitThumb(
    vm: AppViewModel,
    outfit: Outfit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onClick: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val items = remember(data, outfit) { outfit.itemIds.mapNotNull { data.itemById(it) } }
    val effect = outfit.effectImages.firstOrNull()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
    ) {
        if (effect != null) {
            PhotoCard(
                file = vm.imageFileOf(effect.file),
                contentDescription = "穿搭成品图",
                corner = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f),
            )
        } else if (items.isNotEmpty()) {
            // it-012 O5'：与卡组同一套人形拼贴（含空槽），一页一种语言
            com.leo.wardrobe.ui.components.BodyCollage(
                items = items,
                imageFileOf = vm::imageFileOf,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f),
            )
        }
        if (showDate) {
            Text(
                dateFormat.format(Date(outfit.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = editorialColors().inkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
