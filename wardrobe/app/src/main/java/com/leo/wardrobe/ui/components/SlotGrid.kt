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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.ui.theme.EditorialMotion
import com.leo.wardrobe.ui.theme.editorialColors
import kotlinx.coroutines.launch
import java.io.File

/**
 * 着装位卡片（it-005 人体布局；it-011 O6 可发现性）：
 * 格内 HorizontalPager 左右滑换衣；卡底名称条=名称优先（加粗），右侧「⊞ n/m」清单入口
 * （it-061 直选 + it-072 可发现性升级：名称条整条可点、长按照片区同开清单；单击照片仍进详情）、
 * ✕ 移除该格；空品类为 ＋ 占位。
 * coach=true 时首次进入做 ~150ms 左右微移示意（纯视觉位移，不触碰 pager 状态）。
 * aspect 为宽/高比，由着装位决定（帽近方、上身竖长、下装通栏、鞋扁平）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    val flipScope = rememberCoroutineScope()  // it-031：名称条计数可点翻页
    // it-061 修2：品类清单直选 sheet
    var pickerOpen by remember { mutableStateOf(false) }

    // it-047 #9（05 #2）：落定轻弹 scale 1→1.03→1——任何落定（手动换衣/序号翻页/老虎机）触发；
    // 启动 1.5s 内不弹（入场恢复选中位不产生动效，DESIGN §3 二次进入走快路径）。
    // 随 pagerState 身份重建：effect 中途被取消时不会把 1~1.03 的中间值冻结到下一次落定。
    val settlePulse = remember(pagerState) { Animatable(1f) }
    val pulseArmedAt = remember { android.os.SystemClock.uptimeMillis() + 1500 }
    LaunchedEffect(pagerState) {
        var first = true
        snapshotFlow { pagerState.settledPage }.collect {
            if (first) {
                first = false
                return@collect
            }
            if (android.os.SystemClock.uptimeMillis() < pulseArmedAt) return@collect
            EditorialMotion.runSettlePulse(settlePulse)
        }
    }

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
                .aspectRatio(aspect)
                // it-047 #9：落定轻弹位姿（draw 阶段读 Animatable，不触发重组）
                .graphicsLayer {
                    scaleX = settlePulse.value
                    scaleY = settlePulse.value
                },
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
                    // it-047 #9/#11：吸附收敛进 EditorialMotion.pagerFling——常态官方 snap=smooth，
                    // 系统「移除动画」时自实现瞬时落位（官方 SnapFlingBehavior 不吃系统缩放）；
                    // 预取防入屏白块（参数名 1.7.0 起为 beyondViewport）
                    flingBehavior = EditorialMotion.pagerFling(
                        state = pagerState,
                        reduce = EditorialMotion.reduceMotion(),
                    ),
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = coachOffset.value.dp.toPx() }
                        .clip(RoundedCornerShape(12.dp)),
                ) { page ->
                    // it-029 C1：收缩瞬间页码可能仍越界，跳过该页组合避免越界崩溃
                    val item = items.getOrNull(page) ?: return@HorizontalPager
                    val wished = item.isWishSlot // it-019：愿望单品卡视觉
                    val wishAccent = editorialColors().ink
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { if (wished) alpha = 0.72f }
                            // it-058 C3：按压缩放反馈（与进详情点击同一元素）
                            .pressScale(0.975f)
                            // it-042 C4：单击进详情；it-072：长按从「toast 读全名」升级为打开品类清单
                            // （清单内名称两行完整可见，是全名兜底的超集；US-39 路径随之修订）
                            .combinedClickable(
                                onClick = { onCardTap(item) },
                                onLongClickLabel = "打开品类清单",
                                onLongClick = { if (items.size > 1) pickerOpen = true },
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
                                    tint = wishAccent,
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
                            // it-046 分档撑满：常规格（0.6/0.78/0.85）Crop 满格无留边——
                            // 深色模式下旧衬纸(surfaceVariant 半透明)即 Leo 所见黑边距；
                            // 帽(1.0)/鞋(2.6) 极端比例裁切会剪帽檐/鞋底（内容包围盒实测），
                            // 保留 Fit 但衬纸固定浅色 #F2F3F5，主题无关不再变黑
                            val fitPaper = aspect >= 0.95f
                            // it-058 C2：当前页挂共享元素（W1→W5 照片无缝放大，05 动效#3）；
                            // 仅 settled/current 页挂 key——beyondViewport 预取页不参与匹配，
                            // 否则转场时刻树中存在多个同前缀 key，来源端不可见也会抢匹配
                            val shared = if (pagerState.currentPage == page) {
                                Modifier.fillMaxSize().sharedPhoto("item-photo-${item.id}")
                            } else {
                                Modifier.fillMaxSize()
                            }
                            PhotoCard(
                                file = imageFileOf(item.imageFile),
                                contentDescription = item.name,
                                corner = 12.dp,
                                mat = fitPaper,
                                matColor = if (fitPaper) Color(0xFFF2F3F5) else null,
                                contentScale = if (fitPaper) ContentScale.Fit else ContentScale.Crop,
                                modifier = shared,
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
                                            color = wishAccent,
                                            cornerRadius = CornerRadius(12.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx(), pathEffect = stroke),
                                        )
                                    },
                            )
                        }
                        // it-031 C5 rev2：名称优先（名称加粗主位 + 纯序号角标 + ✕ 移除该格）
                        // it-039：固定清晰字号单行显示，超长名称省略；语义保留完整名称。
                        // ✕ 视觉放大 10→16dp、与角标拉开 8dp；触控节点由下方覆盖层补足到 28×44dp（≥44dp 高）。
                        // it-072：名称条整条可点=打开品类清单（it-061 直选的可发现性升级）；
                        // items==1 不挂 clickable——点击穿透进详情，不给单件槽位假入口。
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x8C000000))
                                .then(
                                    if (items.size > 1) Modifier.clickable(
                                        onClickLabel = "打开品类清单",
                                    ) { pickerOpen = true } else Modifier
                                )
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
                            if (items.size > 1) {
                                // it-072：清单图标——名称条「有列表可开」的显性视觉暗示
                                Icon(
                                    Icons.Rounded.GridView,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                "${pagerState.currentPage + 1}/${items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    // it-033：与 ✕ 拉开 8dp（end），start 4dp 隔开名称列（兼作与图标间距）
                                    .padding(start = 4.dp, end = 8.dp)
                                    // it-061 修2：n/m 计数（点击打开清单由 it-072 名称条整条承载）
                                    .semantics {
                                        // it-033：序号角标 a11y（实际 n/m）
                                        contentDescription =
                                            "第 ${pagerState.currentPage + 1} 件，共 ${items.size} 件"
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

    // it-061 修2：品类清单直选（Leo 反馈「以列表直接选，不用来回滑」）——
    // 该品类全部衣物（缩略图+名称+当前高亮+愿望标），点选 scrollToPage 直达。
    if (pickerOpen) {
        ModalBottomSheet(onDismissRequest = { pickerOpen = false }) {
            Text(
                "${category.label} · 共 ${items.size} 件",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val selected = pagerState.currentPage == index
                    Surface(
                        onClick = {
                            pickerOpen = false
                            flipScope.launch {
                                // it-046：末页回卷即时落位——反向 animateScrollToPage 会扫过全部页
                                if (index == 0) pagerState.scrollToPage(0)
                                else pagerState.animateScrollToPage(
                                    index,
                                    animationSpec = EditorialMotion.smooth(),
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            PhotoCard(
                                file = imageFileOf(item.imageFile).takeIf { item.imageFile.isNotEmpty() },
                                contentDescription = null,
                                corner = 8.dp,
                                mat = true,
                                matColor = Color(0xFFF2F3F5),
                                modifier = Modifier.size(52.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name.ifBlank { category.label },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = editorialColors().ink,
                                    // it-072：1→2 行——清单承载 US-39 全名可见兜底（长按开清单替代原 toast）
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.isWishSlot) {
                                    Text(
                                        "想买 · 未录入",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = editorialColors().inkFaint,
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "当前选中",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
