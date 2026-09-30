@file:OptIn(ExperimentalMaterial3Api::class)

package com.leo.wardrobe.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.ui.components.PhotoCard
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * it-075 · 工具结果的结构化卡片模型：只存 id 引用 + 查询条件摘要，
 * 数据快照（名称/图片/标签）渲染时从仓库现取——衣物改名换图自动新鲜，删除落占位行。
 * 由 [parseToolResultCard] 从 tool 消息 payload 解析（live 事件与历史回放同一入口）。
 */
sealed interface ToolResultCard {
    val total: Int
    val ids: List<String>

    data class Items(
        override val total: Int,
        override val ids: List<String>,
        val category: String,
        val keyword: String,
    ) : ToolResultCard

    data class Outfits(
        override val total: Int,
        override val ids: List<String>,
        val keyword: String,
    ) : ToolResultCard
}

/** 卡头的查询条件摘要：「外套 · “蓝”」，空条件 →「全部单品」/「穿搭」 */
fun ToolResultCard.queryLabel(): String = when (this) {
    is ToolResultCard.Items -> buildList {
        if (category.isNotBlank()) add(category)
        if (keyword.isNotBlank()) add("“$keyword”")
    }.joinToString(" · ").ifEmpty { "全部单品" }
    is ToolResultCard.Outfits ->
        keyword.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "穿搭"
}

/** 卡头计数：「找到 8 件 · 外套」「找到 3 套穿搭 · “通勤”」 */
fun ToolResultCard.headerLabel(): String = when (this) {
    is ToolResultCard.Items -> "找到 $total 件 · ${queryLabel()}"
    is ToolResultCard.Outfits -> "找到 $total 套穿搭 · ${queryLabel()}"
}

/**
 * 解析 tool 消息 payload；不认识的结构（null / 其他工具 / 字段缺失）一律返回 null，
 * UI 退回纯文本工具条——旧会话与未升级工具零影响。
 */
fun parseToolResultCard(payload: JsonElement?): ToolResultCard? {
    val obj = payload as? JsonObject ?: return null
    val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: return null
    val ids = obj["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: return null
    if (ids.isEmpty()) return null
    val total = obj["total"]?.jsonPrimitive?.intOrNull ?: ids.size
    val keyword = obj["keyword"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return when (kind) {
        "items" -> ToolResultCard.Items(
            total = total,
            ids = ids,
            category = obj["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            keyword = keyword,
        )
        "outfits" -> ToolResultCard.Outfits(total = total, ids = ids, keyword = keyword)
        else -> null
    }
}

/** 列表卡内联展示上限：超过即折叠为前 N 行 + 「查看全部」抽屉入口（提案拍板阈值 5） */
const val RESULT_INLINE_MAX = 5

/**
 * it-075 · 工具结果卡片入口：单品查询出单品卡/列表卡，穿搭查询出穿搭列表卡。
 * [items]/[outfits] 为当前角色实时数据（与推荐卡同源），id 失配的行落「已移出衣橱」占位。
 */
@Composable
fun ToolResultCards(
    cards: List<ToolResultCard>,
    items: List<Item>,
    outfits: List<Outfit>,
    imageFileOf: (String) -> File?,
    onOpenItem: (String) -> Unit,
    onOpenOutfit: (String) -> Unit,
    onBrowseAll: (ToolResultCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            when (card) {
                is ToolResultCard.Items -> ItemResultCard(card, items, imageFileOf, onOpenItem, onBrowseAll)
                is ToolResultCard.Outfits -> OutfitListCard(card, items, outfits, imageFileOf, onOpenOutfit, onBrowseAll)
            }
        }
    }
}

/** 单品查询卡：命中 1 件 → 单品横卡；多件 → 列表卡（>[RESULT_INLINE_MAX] 折叠） */
@Composable
private fun ItemResultCard(
    card: ToolResultCard.Items,
    items: List<Item>,
    imageFileOf: (String) -> File?,
    onOpenItem: (String) -> Unit,
    onBrowseAll: (ToolResultCard) -> Unit,
) {
    val live = remember(card, items) { card.ids.mapNotNull { id -> items.find { it.id == id } } }
    val removed = card.ids.size - live.size
    CardShell {
        when {
            live.isEmpty() -> RemovedAllRow(card.ids.size, "件")
            live.size == 1 -> SingleItemBody(live.first(), imageFileOf, onOpenItem)
            else -> ListCardBody(
                header = card.headerLabel(),
                rows = live,
                removed = removed,
                unit = "件",
                removedUnit = "件已移出衣橱",
                row = { item ->
                    ItemRow(
                        item = item,
                        imageFileOf = imageFileOf,
                        onClick = { onOpenItem(item.id) },
                    )
                },
                browseAll = { onBrowseAll(card) },
            )
        }
    }
}

/** 穿搭查询卡：列表卡（行 = 成品图/首件单品图 + 组合名 + 标签） */
@Composable
private fun OutfitListCard(
    card: ToolResultCard.Outfits,
    items: List<Item>,
    outfits: List<Outfit>,
    imageFileOf: (String) -> File?,
    onOpenOutfit: (String) -> Unit,
    onBrowseAll: (ToolResultCard) -> Unit,
) {
    val live = remember(card, outfits) { card.ids.mapNotNull { id -> outfits.find { it.id == id } } }
    val removed = card.ids.size - live.size
    CardShell {
        if (live.isEmpty()) {
            RemovedAllRow(card.ids.size, "套")
        } else {
            ListCardBody(
                header = card.headerLabel(),
                rows = live,
                removed = removed,
                unit = "套",
                removedUnit = "套已删除",
                row = { outfit ->
                    OutfitRow(
                        outfit = outfit,
                        items = items,
                        imageFileOf = imageFileOf,
                        onClick = { onOpenOutfit(outfit.id) },
                    )
                },
                browseAll = { onBrowseAll(card) },
            )
        }
    }
}

/** 与推荐卡同族的卡容器（paper 底 + hairline + 16dp 圆角） */
@Composable
private fun CardShell(content: @Composable () -> Unit) {
    val ec = editorialColors()
    Surface(
        color = ec.paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ec.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun RemovedAllRow(n: Int, unit: String) {
    Text(
        "这 $n ${unit}已移出衣橱",
        style = MaterialTheme.typography.bodySmall,
        color = editorialColors().inkFaint,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** 单品横卡：图 + 名称 + 品类·颜色 + 标签，整卡可点进 W5 */
@Composable
private fun SingleItemBody(item: Item, imageFileOf: (String) -> File?, onOpenItem: (String) -> Unit) {
    val ec = editorialColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = { onOpenItem(item.id) })
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoCard(
            file = imageFileOf(item.imageFile),
            contentDescription = item.name,
            corner = 12.dp,
            modifier = Modifier.size(width = 76.dp, height = 92.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                color = ec.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.category.label, item.color.ifBlank { "未记颜色" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = ec.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TagRow(tags = item.tags.take(4))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ec.inkFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 列表卡通用体：卡头 + 内联行（上限 [RESULT_INLINE_MAX]）+ 折叠入口 + 移除计数 */
@Composable
private fun <T> ListCardBody(
    header: String,
    rows: List<T>,
    removed: Int,
    unit: String,
    removedUnit: String,
    row: @Composable (T) -> Unit,
    browseAll: () -> Unit,
) {
    val ec = editorialColors()
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(header, style = MaterialTheme.typography.labelLarge, color = ec.ink)
        Spacer(Modifier.size(2.dp))
        rows.take(RESULT_INLINE_MAX).forEach { row(it) }
        if (rows.size > RESULT_INLINE_MAX) {
            TextButton(
                onClick = browseAll,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Text("查看全部 ${rows.size} $unit")
            }
        }
        if (removed > 0) {
            Text(
                "另有 $removed $removedUnit",
                style = MaterialTheme.typography.labelSmall,
                color = ec.inkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 单品行：缩略图 + 名称 + 品类·颜色 + ›（行高 ≥56dp 满足触控基线） */
@Composable
private fun ItemRow(
    item: Item,
    imageFileOf: (String) -> File?,
    onClick: () -> Unit,
) {
    val ec = editorialColors()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoCard(
            file = imageFileOf(item.imageFile),
            contentDescription = item.name,
            corner = 10.dp,
            modifier = Modifier.size(width = 42.dp, height = 54.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = ec.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.category.label, item.color.ifBlank { "未记颜色" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = ec.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ec.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 穿搭行：成品图/首件单品图 + 组合名 + 标签摘要 */
@Composable
private fun OutfitRow(
    outfit: Outfit,
    items: List<Item>,
    imageFileOf: (String) -> File?,
    onClick: () -> Unit,
) {
    val ec = editorialColors()
    val firstItem = remember(outfit, items) {
        outfit.itemIds.firstNotNullOfOrNull { id -> items.find { it.id == id } }
    }
    val image = outfit.effectImages.firstOrNull()?.file ?: firstItem?.imageFile
    val name = remember(outfit, items) {
        outfit.itemIds.mapNotNull { id -> items.find { it.id == id }?.name }
            .joinToString(" + ")
            .ifBlank { "未命名穿搭" }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoCard(
            file = image?.let(imageFileOf),
            contentDescription = name,
            corner = 10.dp,
            modifier = Modifier.size(width = 54.dp, height = 54.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = ec.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                outfit.tags.joinToString("/").ifBlank {
                    if (outfit.effectImages.isNotEmpty()) "有成品图" else "无成品图"
                },
                style = MaterialTheme.typography.labelSmall,
                color = ec.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ec.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * it-075 · 折叠后的全量浏览抽屉：skipPartiallyExpanded 一次展开到位（ExportSheet 形制），
 * 行点击 = 关抽屉 + 进详情（单品 W5 / 穿搭 W7）。
 */
@Composable
fun ResultBrowserSheet(
    card: ToolResultCard,
    items: List<Item>,
    outfits: List<Outfit>,
    imageFileOf: (String) -> File?,
    onOpenItem: (String) -> Unit,
    onOpenOutfit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ec = editorialColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenH = LocalConfiguration.current.screenHeightDp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (screenH * 0.92f).dp),
        ) {
            Text(
                card.headerLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = ec.ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            when (card) {
                is ToolResultCard.Items -> {
                    val live = card.ids.mapNotNull { id -> items.find { it.id == id } }
                    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                        items(live.size) { i ->
                            val item = live[i]
                            ItemRow(
                                item = item,
                                imageFileOf = imageFileOf,
                                onClick = {
                                    onDismiss()
                                    onOpenItem(item.id)
                                },
                            )
                        }
                    }
                }
                is ToolResultCard.Outfits -> {
                    val live = card.ids.mapNotNull { id -> outfits.find { it.id == id } }
                    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                        items(live.size) { i ->
                            val outfit = live[i]
                            OutfitRow(
                                outfit = outfit,
                                items = items,
                                imageFileOf = imageFileOf,
                                onClick = {
                                    onDismiss()
                                    onOpenOutfit(outfit.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
