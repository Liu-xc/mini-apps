package com.leo.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * 着装位卡片（it-005，W1 人体布局）：格内 HorizontalPager 左右滑换衣；
 * 左上品类徽标、右上序号、卡底名称遮罩条；空品类为 ＋ 占位。
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
) {
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
                        .clip(RoundedCornerShape(12.dp)),
                ) { page ->
                    val item = items[page]
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { onCardTap(item) },
                    ) {
                        PhotoCard(
                            file = imageFileOf(item.imageFile),
                            contentDescription = item.name,
                            corner = 12.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 品类徽标（左上）
                        Surface(
                            color = Color(0x73000000),
                            shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                        ) {
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        // 序号（右上）
                        Surface(
                            color = Color(0x73000000),
                            shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp),
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Text(
                                "${pagerState.currentPage + 1}/${items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        // 当前单品名（卡底遮罩条）
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x8C000000)),
                        ) {
                            Text(
                                items.getOrNull(pagerState.currentPage)?.name ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
