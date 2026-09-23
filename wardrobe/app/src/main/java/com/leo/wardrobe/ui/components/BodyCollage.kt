package com.leo.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.ui.theme.editorialColors
import java.io.File

/**
 * 人体叙事拼贴（it-011 O7）：淡色人形轮廓底（头圆/上身/腿/脚），照片落位=穿在身上；
 * 缺失核心槽（帽/上身/下装/鞋）显示虚线空槽「未配X」，一眼读出这套缺什么；
 * 挂件（包/配饰）缺失不占位。W8 卡面与 W7 详情主视觉共用。
 */
@Composable
fun BodyCollage(
    items: List<Item>,
    imageFileOf: (String) -> File?,
    modifier: Modifier = Modifier,
    showEmptySlots: Boolean = true,
) {
    val byCat = items.groupBy { it.category }
    val hat = byCat[WardrobeCategory.HAT]?.firstOrNull()
    val torso = listOf(
        WardrobeCategory.OUTERWEAR,
        WardrobeCategory.TOP,
        WardrobeCategory.DRESS,
    ).mapNotNull { c -> byCat[c]?.firstOrNull() }
    val bottom = byCat[WardrobeCategory.BOTTOM]?.firstOrNull()
    val bag = byCat[WardrobeCategory.BAG]?.firstOrNull()
    val acc = byCat[WardrobeCategory.ACCESSORY]?.firstOrNull()
    val shoes = byCat[WardrobeCategory.SHOES]?.firstOrNull()

    Column(
        modifier
            .background(silhouetteTint())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 头：帽子落圆头剪影；缺失显示小空槽
        Box(Modifier.weight(0.13f).fillMaxWidth()) {
            if (hat != null) {
                BodySlot(
                    file = imageFileOf(hat.imageFile),
                    label = hat.name,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.Center),
                )
            } else if (showEmptySlots) {
                EmptyBodySlot(
                    label = "未配帽",
                    shape = CircleShape,
                    modifier = Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.5f).align(Alignment.Center),
                )
            }
        }
        // 上身行：外套 | 上装 | 连衣裙（同高填满）；全缺→单个空槽
        if (torso.isNotEmpty()) {
            Row(
                Modifier.weight(0.33f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                torso.forEach { item ->
                    BodySlot(
                        file = imageFileOf(item.imageFile),
                        label = item.name,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        } else if (showEmptySlots) {
            EmptyBodySlot(
                label = "未配上装",
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(0.33f).fillMaxWidth(),
            )
        }
        // 腿行：包(矮挂) | 下装（窄长主体） | 配饰(矮挂)
        if (bottom != null || bag != null || acc != null) {
            Row(
                Modifier.weight(0.42f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bag?.let {
                    BodySlot(
                        file = imageFileOf(it.imageFile),
                        label = it.name,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.26f).fillMaxHeight(0.62f),
                    )
                }
                if (bottom != null) {
                    BodySlot(
                        file = imageFileOf(bottom.imageFile),
                        label = bottom.name,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(if (bag == null && acc == null) 1f else 0.48f).fillMaxHeight(),
                    )
                } else if (showEmptySlots) {
                    EmptyBodySlot(
                        label = "未配下装",
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(0.48f).fillMaxHeight(0.82f),
                    )
                }
                acc?.let {
                    BodySlot(
                        file = imageFileOf(it.imageFile),
                        label = it.name,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.26f).fillMaxHeight(0.62f),
                    )
                }
            }
        } else if (showEmptySlots) {
            EmptyBodySlot(
                label = "未配下装",
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(0.42f).fillMaxWidth(),
            )
        }
        // 脚：鞋落扁脚剪影；缺失显示扁空槽
        Box(Modifier.weight(0.12f).fillMaxWidth()) {
            if (shoes != null) {
                BodySlot(
                    file = imageFileOf(shoes.imageFile),
                    label = shoes.name,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.Center),
                )
            } else if (showEmptySlots) {
                EmptyBodySlot(
                    label = "未配鞋",
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxHeight(0.66f).fillMaxWidth(0.62f).align(Alignment.Center),
                )
            }
        }
    }
}

/** 人形剪影淡底：与纸面同族、比 surfaceVariant 更轻 */
@Composable
private fun silhouetteTint(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

/** 有衣物照片的槽位：淡衬底 + Fit 完整呈现轮廓（照片=穿在身上） */
@Composable
private fun BodySlot(
    file: File?,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(180).build(),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
    }
}

/** 缺失品类的虚线空槽（it-011 O7）：与照片槽同形状，虚线描边 + 文案 */
@Composable
private fun EmptyBodySlot(
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    // it-031 C6：空槽虚线与文字加深一档，贴近 ink 级对比（审查：inkFaint 贴近 3:1 下限）
    val outline = editorialColors().inkFaint.copy(alpha = 0.85f)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape)
            .dashedBorder(outline, width = 1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = editorialColors().ink,
            maxLines = 1,
        )
    }
}

/** 虚线描边（Compose 无原生 dashed border，drawBehind + PathEffect） */
private fun Modifier.dashedBorder(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)),
    )
    val r = CornerRadius(10f)
    // 圆形/圆角形状近似取统一小圆角，虚线在浅底上仅作暗示
    drawRoundRect(color = color, style = stroke, cornerRadius = r)
}
