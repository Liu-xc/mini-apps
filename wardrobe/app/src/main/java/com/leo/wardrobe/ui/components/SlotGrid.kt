package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.launch
import java.io.File

/**
 * 着装位卡片（it-005 人体布局；it-011 O6 可发现性）：
 * 格内 HorizontalPager 左右滑换衣；卡底名称条=名称优先（加粗），右侧纯序号「n/n」可点翻页
 * （it-031 C5 rev2，品类由格位+图片承载）、✕ 移除该格；空品类为 ＋ 占位。
 * coach=true 时首次进入做 ~150ms 左右微移示意（纯视觉位移，不触碰 pager 状态）。
 * aspect 为宽/高比，由着装位决定（帽近方、上身竖长、下装通栏、鞋扁平）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlotCell(
    category: WardrobeCategory,
    items: List<Item>,
    pagerState: PagerState,
    imageFileOf: (String) -> File?,
    onCardTap: (Item) -> Unit,
    onAddEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 0.8f,
    coach: Boolean = false,
    /** it-015 修订：移除该格（非空时显示 ✕）；null = 不提供移除 */
    onRemove: (() -> Unit)? = null,
    /** it-042 C4：长按照片区读完整名称（窄槽单行省略的可见兜底）；null = 不提供 */
    onLongPress: ((Item) -> Unit)? = null,
) {
    // 首次 coach：左右各晃一下，暗示可滑动（it-011 O6）
    val coachOffset = remember { Animatable(0f) }
    val flipScope = rememberCoroutineScope()  // it-031：名称条计数可点翻页
    LaunchedEffect(coach) {
        if (coach && items.size > 1) {
            repeat(2) {
                coachOffset.animateTo(14f, tween(130))
                coachOffset.animateTo(0f, tween(170))
                coachOffset.animateTo(-14f, tween(130))
                coachOffset.animateTo(0f, tween(170))
            }
        }
    }
    // it-029 C1：列表收缩（如关闭混入心愿撤走愿望卡）时把页码回卷进范围，
    // 否则重组期 currentPage 仍指向旧末页，配合下方 getOrNull 防御双保险
    LaunchedEffect(items.size) {
        val last = items.lastIndex
        if (last >= 0 && pagerState.currentPage > last) pagerState.scrollToPage(last)
    }

    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect),
        ) {
            if (items.isEmpty()) {
                Surface(
                    onClick = onAddEmpty,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("＋", style = MaterialTheme.typography.titleSmall, color = editorialColors().inkFaint)
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = editorialColors().inkFaint,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(0.dp),
                    pageSpacing = 0.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = coachOffset.value.dp.toPx() }
                        .clip(RoundedCornerShape(12.dp)),
                ) { page ->
                    // it-029 C1：收缩瞬间页码可能仍越界，跳过该页组合避免越界崩溃
                    val item = items.getOrNull(page) ?: return@HorizontalPager
                    val wished = item.isWishSlot // it-019：愿望单品卡视觉
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { if (wished) alpha = 0.72f }
                            // it-042 C4：单击进详情不变；长按补全名提示（窄槽「鼠尾…」兜底）
                            .combinedClickable(
                                onClick = { onCardTap(item) },
                                onLongClick = { onLongPress?.invoke(item) },
                            ),
                    ) {
                        // it-011 C5：统一浅底衬纸，完整呈现衣物轮廓
                        if (wished && item.imageFile.isEmpty()) {
                            // 无商品图：品类占位（it-030：🌟 emoji → Material Star）
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF3FA265),
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.name.ifBlank { category.label },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        } else {
                            PhotoCard(
                                file = imageFileOf(item.imageFile),
                                contentDescription = item.name,
                                corner = 12.dp,
                                mat = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // it-019：愿望卡左上「想买」角标 + 虚线描边，与已有单品一眼可辨
                        if (wished) {
                            // it-030：角标 Material Star + 想买；底色加深保证白字对比（审查 P1）
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                modifier = Modifier.align(Alignment.TopStart),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        "想买",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        val stroke = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                        drawRoundRect(
                                            color = Color(0xFF3FA265),
                                            cornerRadius = CornerRadius(12.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx(), pathEffect = stroke),
                                        )
                                    },
                            )
                        }
                        // it-031 C5 rev2：名称优先（名称加粗主位 + 纯序号角标可点循环翻页 + ✕ 移除该格）
                        // it-039：固定清晰字号单行显示，超长名称省略；语义保留完整名称。
                        // ✕ 视觉放大 10→16dp、与角标拉开 8dp；触控节点由下方覆盖层补足到 28×44dp（≥44dp 高）。
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x8C000000))
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val itemName = items.getOrNull(pagerState.currentPage)?.name.orEmpty()
                            Text(
                                itemName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = itemName },
                            )
                            Text(
                                "${pagerState.currentPage + 1}/${items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    // it-033：与 ✕ 拉开 8dp（end），start 4dp 隔开名称列
                                    .padding(start = 4.dp, end = 8.dp)
                                    .clickable {
                                        if (items.size > 1) {
                                            flipScope.launch {
                                                pagerState.animateScrollToPage(
                                                    (pagerState.currentPage + 1) % items.size,
                                                )
                                            }
                                        }
                                    }
                                    .semantics {
                                        // it-033：序号角标补 a11y（实际 n/m）
                                        contentDescription =
                                            "第 ${pagerState.currentPage + 1} 件，共 ${items.size} 件，点按切换"
                                    },
                            )
                            if (onRemove != null) {
                                // it-033：✕ 视觉 16dp，居中于名称条；触控热区由名称条右端的覆盖层承载
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        if (onRemove != null) {
                            // it-033：✕ 触控覆盖层——28×44dp，底边贴名称条右端、向上探入照片边缘一角。
                            // 高度补足 ≥44dp 走查要求；宽度止步 28dp：窄格（包/帽 ≈100dp）名称列预算优先，
                            // 44dp 宽列会把长名重新挤回截断（US-33b）。节点不与角标/名称重叠（角标止于 28dp 线外）。
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .width(28.dp)
                                    .height(44.dp)
                                    .clickable { onRemove() }
                                    .semantics { contentDescription = "移除该格" },
                            )
                        }
                    }
                }
            }
        }
    }
}
