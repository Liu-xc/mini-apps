package com.leo.darkroom.ui.pick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.FrameStyle
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpeed
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.result.PhotoViewer
import com.leo.darkroom.ui.theme.EditorialEntrance
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.SelectChip
import com.leo.darkroom.ui.theme.editorialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画册模式（it-012，原沉浸相册独立成屏）：相册网格点缩略图进入，从该页起翻；
 * 翻到新照片跑显影动画，动画完停留在此（不跳页）；点成品=大图（存图/编辑）；
 * 顶行返回回网格；底部 = 快捷配置面板（模式/速度只是动画的样式）+ 重播。
 */
@Composable
fun AlbumPagerScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    val pagerState = rememberPagerState(
        initialPage = state.albumInitialPage.coerceIn(0, (state.album.size - 1).coerceAtLeast(0)),
        pageCount = { state.album.size.coerceAtLeast(1) },
    )
    var panelOpen by remember { mutableStateOf(false) }
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var viewerPhoto by remember { mutableStateOf<com.leo.darkroom.data.AlbumPhoto?>(null) }

    LaunchedEffect(Unit) { if (state.playedIds.isEmpty() && state.album.isEmpty()) vm.loadAlbum(true) }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .padding(20.dp),
    ) {
        // 顶行：返回 + 页码
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::backToPick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回相册", tint = colors.ink)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "画册",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${(pagerState.currentPage + 1).coerceAtMost(state.album.size)} / ${state.album.size}",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                color = colors.inkFaint,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 翻页主体（weight 必须挂在入场组件本身——挂在其内容上对 Column 无效，
        // 曾把底部配置胶囊挤出屏外，it-012 收尾实锤修复）
        EditorialEntrance(delayMs = 24, enabled = true, modifier = Modifier.weight(1f)) {
            GalleryPager(
                vm, state, pagerState,
                onOpenViewer = { photo, bmp ->
                    viewerPhoto = photo
                    viewerBitmap = bmp
                    viewerOpen = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 底部：快捷配置面板 + 重播（it-011 收尾 2 移入）
        EditorialEntrance(delayMs = 48, enabled = true) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = { panelOpen = !panelOpen },
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.hairline),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${state.mode.label} · ${state.speed.label} ${state.speed.durationMs / 1000}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.ink,
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (panelOpen) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                                contentDescription = if (panelOpen) "收起配置" else "展开配置",
                                tint = colors.inkFaint,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            state.album.getOrNull(pagerState.currentPage)
                                ?.let { vm.replayGalleryCard(it.id) }
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重播显影", tint = colors.ink)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    state.mode.note + " · 点按成片看大图",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // it-012 收尾 6：配置面板=相机式浮层——半透明蒙层盖在照片上，点蒙层收起
    if (panelOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { panelOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0x59000000))
                    .clickable { panelOpen = false },
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 108.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = androidx.compose.ui.graphics.Color(0xE61A1A1A),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("动画模式", style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color(0xB3FBFBFA))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DevelopMode.entries.forEach { mode ->
                                DarkPill(mode.label, state.mode == mode) { vm.setMode(mode) }
                            }
                        }
                        if (state.mode == DevelopMode.POLAROID) {
                            Spacer(Modifier.height(12.dp))
                            Text("相纸", style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color(0xB3FBFBFA))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                com.leo.darkroom.card.FrameStyle.entries.forEach { frame ->
                                    DarkPill(frame.label, state.spec.frame == frame) { vm.setFrame(frame) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("动画速度", style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color(0xB3FBFBFA))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DevelopSpeed.entries.forEach { speed ->
                                DarkPill("${speed.label} ${speed.durationMs / 1000}s", state.speed == speed) { vm.setSpeed(speed) }
                            }
                        }
                    }
                }
            }
        }
    }

    // 大图查看器（it-012 收尾 3）：放大的是整张成品卡（相纸/日期/脚注全套），不是裸照片
    val viewerCardArt by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        null, viewerPhoto, state.spec, state.mode, state.photoLook,
    ) {
        val p = viewerPhoto ?: return@produceState
        val src = viewerBitmap ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val w = 1440f
            val layout = com.leo.darkroom.card.CardLayout.solve(w, mode = state.mode)
            android.graphics.Bitmap.createBitmap(
                w.toInt(), layout.height.toInt(), android.graphics.Bitmap.Config.ARGB_8888,
            ).also { bmp ->
                com.leo.darkroom.card.PhotoCardPainter.paint(
                    canvas = android.graphics.Canvas(bmp),
                    cardWidthPx = w,
                    photo = src,
                    spec = state.spec,
                    visual = com.leo.darkroom.develop.DevelopSpec.visualAt(state.mode, 1f),
                    mode = state.mode,
                    grain = vm.grain,
                    look = state.photoLook,
                )
            }
        }
    }

    if (viewerOpen && viewerPhoto != null) {
        val photo = viewerPhoto!!
        val bmp = viewerBitmap!!
        val image = (viewerCardArt ?: bmp).asImageBitmap()
        PhotoViewer(
            image = image,
            contentDescription = "成品卡大图",
            onClose = { viewerOpen = false },
            actions = {
                Button(
                    onClick = {
                        viewerOpen = false
                        vm.openGalleryResult(photo, bmp, toEdit = false)
                        vm.exportImage(share = false)
                    },
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFBFBFA),
                        contentColor = androidx.compose.ui.graphics.Color(0xFF1B1B1B),
                    ),
                ) { Text("存图片") }
                OutlinedButton(
                    onClick = {
                        viewerOpen = false
                        vm.openGalleryResult(photo, bmp, toEdit = true)
                    },
                    modifier = Modifier.height(44.dp),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xB3FBFBFA)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                    ),
                ) { Text("编辑") }
            },
        )
    }
}

/**
 * 显影模式选择（it-007 US-14；it-012 起只住在画册快捷面板里——模式=动画样式）：
 * 选中态边框+字重双重表达，微型示意图 Canvas 画形体（DESIGN §5 反例 2）。
 */
@Composable
internal fun ModeChip(
    mode: DevelopMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = editorialColors()
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = tween(150),
        label = "chipBorderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.hairline,
        animationSpec = tween(150),
        label = "chipBorderColor",
    )
    val glyphScale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            glyphScale.snapTo(1f)
            glyphScale.animateTo(1.08f, tween(90))
            glyphScale.animateTo(1f, EditorialMotion.pop())
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(66.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = glyphScale.value
                        scaleY = glyphScale.value
                    },
            ) { drawModeGlyph(mode, colors) }
            Spacer(Modifier.height(4.dp))
            Text(
                mode.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.ink else colors.inkFaint,
            )
        }
    }
}

/** 三种卡面的缩微剪影：相纸白框 / 屏幕 + OSD / 齿孔片条 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawModeGlyph(
    mode: DevelopMode,
    colors: com.leo.darkroom.ui.theme.EditorialColors,
) {
    val w = size.width
    val h = size.height
    when (mode) {
        DevelopMode.POLAROID -> {
            drawRect(colors.ink, topLeft = Offset(w * 0.16f, h * 0.03f), size = Size(w * 0.68f, h * 0.94f))
            drawRect(colors.paper, topLeft = Offset(w * 0.23f, h * 0.10f), size = Size(w * 0.54f, h * 0.50f))
        }

        DevelopMode.DIGITAL -> {
            drawRect(colors.ink, topLeft = Offset(w * 0.08f, h * 0.12f), size = Size(w * 0.84f, h * 0.74f))
            drawRect(colors.paper, topLeft = Offset(w * 0.15f, h * 0.19f), size = Size(w * 0.70f, h * 0.44f))
            drawRect(colors.paper, topLeft = Offset(w * 0.28f, h * 0.72f), size = Size(w * 0.44f, h * 0.06f))
        }

        DevelopMode.FILM -> {
            drawRect(colors.ink, topLeft = Offset(0f, h * 0.18f), size = Size(w, h * 0.64f))
            drawRect(colors.paper, topLeft = Offset(w * 0.07f, h * 0.33f), size = Size(w * 0.86f, h * 0.34f))
            for (i in 0..3) {
                val x = w * 0.08f + i * w * 0.24f
                drawRect(colors.paper, topLeft = Offset(x, h * 0.22f), size = Size(w * 0.13f, h * 0.07f))
                drawRect(colors.paper, topLeft = Offset(x, h * 0.71f), size = Size(w * 0.13f, h * 0.07f))
            }
        }
    }
}


/** 相机面板的暗底胶囊（it-012 收尾 7）：选中=亮底深字，未选=半透明白描边亮字 */
@Composable
private fun RowScope.DarkPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) androidx.compose.ui.graphics.Color(0xFFFBFBFA)
                else androidx.compose.ui.graphics.Color(0x14FFFFFF),
            )
            .border(
                BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x59FFFFFF)),
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) androidx.compose.ui.graphics.Color(0xFF1B1B1B)
            else androidx.compose.ui.graphics.Color(0xE6FBFBFA),
            maxLines = 1,
        )
    }
}
