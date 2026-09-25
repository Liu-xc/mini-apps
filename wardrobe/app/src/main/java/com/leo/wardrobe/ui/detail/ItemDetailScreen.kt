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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.leo.wardrobe.ui.components.rememberHaptics
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

    // it-040 US-40：补抠状态条——候选图独立于当前图（还原=弃候选，当前图全程未动）；
    // alpha 检测决定文案（已抠→重新抠图 / 原图→去背景），入口常驻（决策 3）
    var recutCandidate by remember { mutableStateOf<String?>(null) }
    var recutRunning by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val isCutout = remember(item.id, item.imageFile) { vm.looksCutoutPhoto(item.imageFile) }

    fun startRecut() {
        if (recutRunning) return
        recutRunning = true
        vm.cutoutPhoto(item.imageFile) { out ->
            recutRunning = false
            if (out != null) {
                recutCandidate = out
                haptics.confirm()
            } // 失败 toast 且当前图不动（VM 内处理）
        }
    }

    fun discardCandidate() {
        recutCandidate?.let(vm::deletePhotoFile)
        recutCandidate = null
        haptics.tick()
    }

    fun keepCandidate() {
        val candidate = recutCandidate ?: return
        vm.applyPhotoRecut(item.id, candidate) { ok ->
            if (ok) {
                recutCandidate = null
                haptics.confirm()
            }
        }
    }

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
            // it-011 C5：统一浅底衬纸容器——固定比例/圆角/淡底，无论原图背景如何；
            // it-040：补抠候选期垫棋盘格提示透明底（与 W4 同款视觉语言），共享元素仅在稳定态挂
            val candidate = recutCandidate
            if (candidate != null) {
                val cellPx = with(LocalDensity.current) { 12.dp.toPx() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .drawBehind {
                            val light = Color(0xFFF2F3F5)
                            val dark = Color(0xFFE1E3E8)
                            var row = 0
                            var y = 0f
                            while (y < size.height) {
                                var col = row
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(
                                        if (col % 2 == 0) dark else light,
                                        topLeft = Offset(x, y),
                                        size = Size(
                                            minOf(cellPx, size.width - x),
                                            minOf(cellPx, size.height - y),
                                        ),
                                    )
                                    x += cellPx
                                    col++
                                }
                                y += cellPx
                                row++
                            }
                        },
                ) {
                    PhotoCard(
                        file = vm.imageFileOf(candidate),
                        contentDescription = "新背景预览",
                        corner = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
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
            }

            // it-040 US-40：操作状态条（候选预览 / 推理中 / 已抠态重新抠 / 原图态去背景）
            when {
                candidate != null -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "新背景预览",
                            style = MaterialTheme.typography.bodyMedium,
                            color = editorialColors().ink,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { discardCandidate() }) { Text("还原") }
                        Button(onClick = { keepCandidate() }) { Text("保留") }
                    }
                }

                recutRunning -> OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(44.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在去背景…")
                }

                isCutout -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "已去背景 · 透明底",
                            style = MaterialTheme.typography.bodyMedium,
                            color = editorialColors().ink,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { startRecut() }) { Text("重新抠图") }
                    }
                }

                else -> OutlinedButton(
                    onClick = { startRecut() },
                    enabled = !recutRunning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(44.dp),
                ) {
                    Text("去背景 · 一键透明底")
                }
            }
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
                // it-033：详情标签 chips 触控/视觉高 ≥44dp（走查实测原 15dp）
                Row(Modifier.padding(top = 8.dp)) { TagRow(item.tags, minChipHeight = 44.dp) }
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
    // it-042 C3：并入 it-036 C12 的全站日期格式（yyyy/MM/dd），废止 MM/dd 双轨
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
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
