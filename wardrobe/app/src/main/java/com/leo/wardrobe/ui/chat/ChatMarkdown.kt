package com.leo.wardrobe.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.net.Uri
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/** W13 assistant 正文：只覆盖顾问实际协议需要的 Markdown 子集，不显示原始标记。 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    bodyColor: Color = editorialColors().ink,
) {
    val ec = editorialColors()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        markdown.lines().forEachIndexed { index, line ->
            MarkdownLine(
                line = line,
                bodyColor = bodyColor,
                accentColor = ec.accentContent,
                key = index,
            )
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    bodyColor: Color,
    accentColor: Color,
    key: Int,
) {
    val trimmed = line.trimStart()
    when {
        trimmed.isBlank() -> Spacer(Modifier.height(2.dp))
        trimmed.startsWith("### ") -> Text(
            inlineMarkdown(trimmed.removePrefix("### "), bodyColor, accentColor),
            style = MaterialTheme.typography.titleMedium,
            color = bodyColor,
        )
        trimmed.startsWith("## ") -> Text(
            inlineMarkdown(trimmed.removePrefix("## "), bodyColor, accentColor),
            style = MaterialTheme.typography.titleLarge,
            color = bodyColor,
        )
        trimmed.startsWith("# ") -> Text(
            inlineMarkdown(trimmed.removePrefix("# "), bodyColor, accentColor),
            style = MaterialTheme.typography.headlineSmall,
            color = bodyColor,
        )
        trimmed.startsWith("> ") || trimmed == ">" -> {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(accentColor),
                )
                MarkdownInlineText(
                    text = trimmed.removePrefix("> "),
                    bodyColor = bodyColor,
                    accentColor = accentColor,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }
        BULLET.matches(trimmed) -> Row(verticalAlignment = Alignment.Top) {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = accentColor)
            MarkdownInlineText(
                text = trimmed.drop(2),
                bodyColor = bodyColor,
                accentColor = accentColor,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        ORDERED.matches(trimmed) -> {
            val match = ORDERED.matchEntire(trimmed) ?: return
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${match.groupValues[1]}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                )
                MarkdownInlineText(
                    text = match.groupValues[2],
                    bodyColor = bodyColor,
                    accentColor = accentColor,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        else -> MarkdownInlineText(line, bodyColor, accentColor)
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    bodyColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        inlineMarkdown(text, bodyColor, accentColor),
        style = MaterialTheme.typography.bodyMedium,
        color = bodyColor,
        modifier = modifier,
    )
}

private val BULLET = Regex("^[-*]\\s+")
private val ORDERED = Regex("^(\\d+)\\.\\s+(.+)$")

private fun inlineMarkdown(text: String, bodyColor: Color, accentColor: Color): AnnotatedString =
    AnnotatedString.Builder().apply {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) || text.startsWith("__", index) -> {
                    val marker = text.substring(index, index + 2)
                    val end = text.indexOf(marker, index + 2)
                    if (end >= 0) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = bodyColor))
                        append(text.substring(index + 2, end))
                        pop()
                        index = end + 2
                    } else {
                        index += 2
                    }
                }
                text[index] == '`' -> {
                    val end = text.indexOf('`', index + 1)
                    if (end >= 0) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Medium, color = accentColor))
                        append(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else {
                        index++
                    }
                }
                text[index] == '*' || text[index] == '_' -> {
                    val marker = text[index]
                    val end = text.indexOf(marker, index + 1)
                    if (end > index + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = bodyColor))
                        append(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else {
                        append(marker)
                        index++
                    }
                }
                text[index] == '[' -> {
                    val labelEnd = text.indexOf("](", index + 1)
                    val linkEnd = if (labelEnd >= 0) text.indexOf(')', labelEnd + 2) else -1
                    if (labelEnd > index && linkEnd > labelEnd) {
                        pushStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        )
                        append(text.substring(index + 1, labelEnd))
                        pop()
                        index = linkEnd + 1
                    } else {
                        append('[')
                        index++
                    }
                }
                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }.toAnnotatedString()

/** 回复的结构化呈现：Markdown 说明 + 由本地衣橱单品组成的卡片。 */
@Composable
fun AssistantReply(
    text: String,
    wardrobeItems: List<Item>,
    imageFileOf: (String) -> File?,
    modifier: Modifier = Modifier,
    onOpenItem: (String) -> Unit = {},
    // it-056：连同推荐一起上抛，供导出面板预选场景等五维参数
    onExport: ((OutfitRecommendation, List<Item>) -> Unit)? = null,
) {
    val parsed = remember(text, wardrobeItems) { parseAssistantReply(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        parsed.parts.forEach { part ->
            when (part) {
                is AssistantReplyPart.Markdown -> MarkdownText(part.text)
                is AssistantReplyPart.Outfit -> OutfitRecommendationCard(
                    recommendation = part.value,
                    wardrobeItems = wardrobeItems,
                    imageFileOf = imageFileOf,
                    onOpenItem = onOpenItem,
                    onExport = onExport,
                )
            }
        }
    }
}

@Composable
private fun OutfitRecommendationCard(
    recommendation: OutfitRecommendation,
    wardrobeItems: List<Item>,
    imageFileOf: (String) -> File?,
    onOpenItem: (String) -> Unit,
    onExport: ((OutfitRecommendation, List<Item>) -> Unit)?,
) {
    val ec = editorialColors()
    // it-055：匹配逻辑与测试共用同一纯函数（matchRecommendationItems）
    val resolved = remember(recommendation, wardrobeItems) {
        matchRecommendationItems(recommendation, wardrobeItems)
    }
    Surface(
        color = ec.paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ec.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ec.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${resolved.count { it.second != null }}/${resolved.size} 件匹配",
                    style = MaterialTheme.typography.labelSmall,
                    color = ec.inkFaint,
                )
            }

            resolved.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (line, item) ->
                        OutfitItemTile(
                            category = item?.category?.label ?: line.categoryLabel,
                            name = item?.name ?: line.itemName,
                            image = item?.let { imageFileOf(it.imageFile) },
                            matched = item != null,
                            // it-055 US-56：匹配成功才可点进 W5；未匹配保持中性不可点
                            onClick = if (item != null) {
                                { onOpenItem(item.id) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (resolved.any { it.second == null }) {
                Text(
                    "未在当前衣橱匹配：" + resolved.filter { it.second == null }
                        .joinToString("、") { it.first.itemName },
                    style = MaterialTheme.typography.labelSmall,
                    color = ec.inkFaint,
                )
            }
            if (recommendation.detailMarkdown.isNotBlank()) {
                MarkdownText(
                    recommendation.detailMarkdown,
                    bodyColor = ec.ink,
                )
            }
            // it-055 US-58：整套导出——复用 W6 导出面板（至少一件匹配才有素材可拼）
            val matchedItems = resolved.mapNotNull { it.second }
            if (onExport != null && matchedItems.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { onExport(recommendation, matchedItems) }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("复制长图", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitItemTile(
    category: String,
    name: String,
    image: File?,
    matched: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val ec = editorialColors()
    val base = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(base, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ec.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(image)
                        .crossfade(false)
                        .build(),
                    contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                )
            } else {
                Text(category, style = MaterialTheme.typography.labelSmall, color = ec.inkFaint)
            }
        }
        Text(category, style = MaterialTheme.typography.labelSmall, color = ec.inkFaint)
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = if (matched) ec.ink else ec.inkFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
