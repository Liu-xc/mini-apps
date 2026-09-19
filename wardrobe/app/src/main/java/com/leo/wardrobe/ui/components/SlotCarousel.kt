package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File
import kotlin.math.abs

/**
 * 品类槽位轮播（W1 / specs/05 动效#1）：
 * HorizontalPager + graphicsLayer —— 中间卡片全尺寸，两侧缩小变淡；
 * 松手吸附换衣物；当前卡片带轻微落定弹跳。
 */
@Composable
fun SlotCarousel(
    category: WardrobeCategory,
    items: List<Item>,
    pagerState: PagerState,
    imageFileOf: (String) -> File?,
    onCardTap: (Item) -> Unit,
    onAddEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 槽位头：品类名 + 序号
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                category.label,
                style = MaterialTheme.typography.titleMedium,
                color = editorialColors().ink,
            )
            if (items.isNotEmpty()) {
                Text(
                    "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = editorialColors().inkFaint,
                )
            }
        }

        if (items.isEmpty()) {
            // 空槽位：紧凑「+ 去添加」引导（specs W1）
            Surface(
                onClick = onAddEmpty,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    "＋ 还没有${category.label}，去添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = editorialColors().inkFaint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            return@Column
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 10.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            // 轮播形变：基于页偏移的缩放与透明度（业界标准做法，specs/05 参考#2）
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - abs(pageOffset).coerceIn(0f, 1f)
            val scale = lerp(0.86f, 1f, closeness)
            val alpha = lerp(0.45f, 1f, closeness)

            // 落定轻弹：页码变更后 scale 微弹回
            val settled by animateFloatAsState(
                targetValue = if (page == pagerState.currentPage) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "settle",
            )
            val bounce = 1f + 0.025f * settled * closeness

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale * bounce
                    scaleY = scale * bounce
                    this.alpha = alpha
                },
            ) {
                PhotoCard(
                    file = imageFileOf(item.imageFile),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .sharedPhoto("item-photo-${item.id}")
                        .clickable { onCardTap(item) },
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = editorialColors().ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (item.tags.isNotEmpty()) {
                    TagRow(tags = item.tags, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
