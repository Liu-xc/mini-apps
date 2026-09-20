package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * 着装位卡片（it-005 人体布局；it-011 O6 可发现性）：
 * 格内 HorizontalPager 左右滑换衣；左上品类徽标、右上 ‹ n/n › 序号胶囊
 * （chevron 明示可翻）、卡底名称遮罩条；空品类为 ＋ 占位。
 * coach=true 时首次进入做 ~150ms 左右微移示意（纯视觉位移，不触碰 pager 状态）。
 * aspect 为宽/高比，由着装位决定（帽近方、上身竖长、下装通栏、鞋扁平）。
 */
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
) {
    // 首次 coach：左右各晃一下，暗示可滑动（it-011 O6）
    val coachOffset = remember { Animatable(0f) }
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
                    val item = items[page]
                    val wished = item.isWishSlot // it-019：愿望单品卡视觉
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { if (wished) alpha = 0.72f }
                            .clickable { onCardTap(item) },
                    ) {
                        // it-011 C5：统一浅底衬纸，完整呈现衣物轮廓
                        if (wished && item.imageFile.isEmpty()) {
                            // 无商品图：品类占位
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("🌟", style = MaterialTheme.typography.headlineMedium)
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
                            Surface(
                                color = Color(0xB33FA265),
                                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                modifier = Modifier.align(Alignment.TopStart),
                            ) {
                                Text(
                                    "🌟 想买",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
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
                        // it-012：品类徽标并入卡底名称条，‹ n/n › 独占右上（R2 双胶囊竞争）
                        // 序号胶囊（右上，it-011 O6：‹ n/n › 明示可翻）
                        Surface(
                            color = Color(0x73000000),
                            shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp),
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Text(
                                "‹ ${pagerState.currentPage + 1}/${items.size} ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        // 当前单品名（卡底遮罩条）；右端 ✕ 移除该格（it-015 修订：增删能力）
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x8C000000)),
                        ) {
                            Text(
                                buildString {
                                    append(category.label)
                                    items.getOrNull(pagerState.currentPage)?.name?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .padding(end = if (onRemove != null) 30.dp else 0.dp),
                            )
                            if (onRemove != null) {
                                Text(
                                    "✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .clickable { onRemove() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
