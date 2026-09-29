package com.leo.darkroom.ui.pick

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.SampleArt
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.EditorialEntrance
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.editorialColors

/** W1（it-011 双态）：授权后是沉浸式相册显影——大卡横滑翻阅、首次跑显影动画；
 * 未授权/相册为空时回退原选图布局（Photo Picker/拍照/样片全量可用）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PickScreen(
    vm: DarkroomViewModel,
    state: UiState,
    playEntrance: Boolean,
    onEntrancePlayed: () -> Unit,
) {
    val colors = editorialColors()
    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> vm.onPhotoPicked(uri) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> vm.onCameraResult(ok) }
    val cameraUri = remember { { vm.prepareCamera() } }
    // it-011：相册运行时权限（ADR-008）——用户点击引导卡时才请求，不冷启动硬弹
    val albumPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onAlbumPermissionResult(granted) }

    val galleryMode = state.albumGranted == true && !state.galleryDismissed &&
            (state.album.isNotEmpty() || state.albumLoading)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        pageCount = { state.album.size.coerceAtLeast(1) },
    )
    // it-011 收尾：点按成品=全屏大图（保存/编辑从查看器动作进，而非直跳编辑页）
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var viewerPhoto by remember { mutableStateOf<com.leo.darkroom.data.AlbumPhoto?>(null) }

    // 首次冷入场编排只放一次（it-008 M2.3）：flag 由导航壳跨屏持有，
    // 从 W2/W4 返回与 Activity 重建都不重放（基线「二次进入走快路径」）。
    LaunchedEffect(Unit) {
        if (playEntrance) onEntrancePlayed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .padding(20.dp),
    ) {
        EditorialEntrance(delayMs = 0, enabled = playEntrance) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DARKROOM · 私人暗房",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = colors.inkFaint,
                )
                Spacer(Modifier.weight(1f))
                if (galleryMode) {
                    IconButton(onClick = vm::exitGallery, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "退出相册浏览", tint = colors.inkFaint)
                    }
                }
                IconButton(onClick = vm::openSettings, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = "设置", tint = colors.inkFaint)
                }
            }
        }

        if (galleryMode) {
            // —— 沉浸相册态：大卡翻页 + 底部模式/重播/拍照 ——
            EditorialEntrance(delayMs = 24, enabled = playEntrance) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.album.isNotEmpty()) {
                        GalleryPager(
                            vm, state, pagerState,
                            onOpenViewer = { photo, bmp -> viewerPhoto = photo; viewerBitmap = bmp; viewerOpen = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            "${pagerState.currentPage + 1} / ${state.album.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkFaint,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            EditorialEntrance(delayMs = 48, enabled = playEntrance) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = {
                                state.album.getOrNull(pagerState.currentPage)
                                    ?.let { vm.replayGalleryCard(it.id) }
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "重播显影", tint = colors.ink)
                        }
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DevelopMode.entries.forEach { mode ->
                                ModeChip(
                                    mode = mode,
                                    selected = state.mode == mode,
                                    onClick = { vm.setMode(mode) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        IconButton(
                            onClick = { cameraLauncher.launch(cameraUri()) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = "现在拍一张", tint = colors.ink)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        state.mode.note + " · 点按成片可编辑导出",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            // —— 回退态：原有选图布局 + 相册权限引导（it-011）——
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                EditorialEntrance(delayMs = 24, enabled = playEntrance) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "显影",
                            style = MaterialTheme.typography.displayLarge,
                            color = colors.ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "把这一刻，慢慢洗成一张可以带走的相纸。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkFaint,
                        )
                    }
                }

                if (state.albumGranted != true || state.galleryDismissed) {
                    EditorialEntrance(delayMs = 36, enabled = playEntrance) {
                        Column {
                            Spacer(Modifier.height(18.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.hairline),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "沉浸式相册浏览",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        when {
                                            state.albumGranted == false ->
                                                "还没拿到相册权限——可以再试一次，或继续用下面的入口选图。"

                                            state.galleryDismissed ->
                                                "沉浸式相册浏览还在这里，随时回来。"

                                            else ->
                                                "授权读取相册后，首页变成可以滑动的相纸墙：左右滑照片，每一张都在你眼前显影。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkFaint,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (state.albumGranted == true) vm.enterGallery()
                                            else albumPermissionLauncher.launch(albumPermission())
                                        },
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.ink,
                                            contentColor = colors.paper,
                                        ),
                                    ) {
                                        Text(
                                            when {
                                                state.albumGranted == false -> "再试一次"
                                                state.galleryDismissed -> "回到沉浸相册"
                                                else -> "开启相册权限"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                EditorialEntrance(delayMs = 48, enabled = playEntrance) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                pickLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.loadingPhoto,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.ink,
                                contentColor = colors.paper,
                            ),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.loadingPhoto) "正在读取照片…" else "从相册挑一张")
                        }

                        TextButton(
                            onClick = { cameraLauncher.launch(cameraUri()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.ink),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("现在拍一张")
                        }
                    }
                }

                // it-007 US-14 显影模式：拍立得 / 数码相机 / 胶片
                EditorialEntrance(delayMs = 60, enabled = playEntrance) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Text("显影模式", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DevelopMode.entries.forEach { mode ->
                                ModeChip(
                                    mode = mode,
                                    selected = state.mode == mode,
                                    onClick = { vm.setMode(mode) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            state.mode.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkFaint,
                        )
                    }
                }

                EditorialEntrance(delayMs = 72, enabled = playEntrance) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("先从一张样片开始", style = MaterialTheme.typography.labelSmall, color = colors.inkFaint)
                            Spacer(Modifier.weight(1f))
                            Text("离线可试", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                        }
                        Spacer(Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(SampleArt.titles) { index, title ->
                                val bitmap = remember(context, index) { SampleArt.load(context, index, 512) }
                                val interactionSource = remember { MutableInteractionSource() }
                                val pressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (pressed) 0.97f else 1f,
                                    animationSpec = EditorialMotion.pop(),
                                    label = "samplePress",
                                )
                                Surface(
                                    onClick = { vm.onSamplePicked(index) },
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .width(112.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.cardPaper,
                                    border = BorderStroke(1.dp, colors.cardHairline),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(7.dp),
                                    ) {
                                        Image(
                                            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(98.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                        )
                                        Spacer(Modifier.height(7.dp))
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.cardInk,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        EditorialEntrance(delayMs = 96, enabled = playEntrance) {
            Column {
                Spacer(Modifier.height(14.dp))
                Text(
                    "全程离线 · 照片不上传",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkFaint,
                )
            }
        }

        if (viewerOpen && viewerBitmap != null && viewerPhoto != null) {
            val photo = viewerPhoto!!
            val bmp = viewerBitmap!!
            com.leo.darkroom.ui.result.PhotoViewer(
                image = bmp.asImageBitmap(),
                contentDescription = "相册照片大图",
                onClose = { viewerOpen = false },
                actions = {
                    Button(
                        onClick = {
                            viewerOpen = false
                            vm.openGalleryResult(photo, bmp, toEdit = false)
                            vm.exportImage(share = false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.paper,
                        ),
                    ) { Text("存图片") }
                    OutlinedButton(
                        onClick = {
                            viewerOpen = false
                            vm.openGalleryResult(photo, bmp, toEdit = true)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    ) { Text("编辑") }
                },
            )
        }
    }
}

/** 相册运行时权限名（it-011 ADR-008）：API 33+ 走 READ_MEDIA_IMAGES */
private fun albumPermission(): String =
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        @Suppress("DEPRECATION")
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * 显影模式选择（it-007 US-14）：三枚等宽 chip，选中态同时用边框与字重表达，
 * 微型示意图是 Canvas 画的形体，不用 emoji（DESIGN §5 反例 2）。
 * it-008 M2.4：边框宽与色 150ms 过渡，选中瞬间示意图 pop 一下；字重不动画（过渡会抖）。
 */
@Composable
private fun ModeChip(
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
            // 白框相纸：成像区在上、底边更宽
            drawRect(colors.ink, topLeft = Offset(w * 0.16f, h * 0.03f), size = Size(w * 0.68f, h * 0.94f))
            drawRect(colors.paper, topLeft = Offset(w * 0.23f, h * 0.10f), size = Size(w * 0.54f, h * 0.50f))
        }

        DevelopMode.DIGITAL -> {
            // 深色机身 + 亮屏 + 底部 OSD 一横
            drawRect(colors.ink, topLeft = Offset(w * 0.08f, h * 0.12f), size = Size(w * 0.84f, h * 0.74f))
            drawRect(colors.paper, topLeft = Offset(w * 0.15f, h * 0.19f), size = Size(w * 0.70f, h * 0.44f))
            drawRect(
                colors.paper,
                topLeft = Offset(w * 0.28f, h * 0.72f),
                size = Size(w * 0.44f, h * 0.06f),
            )
        }

        DevelopMode.FILM -> {
            // 片条：深底 + 中间片格 + 上下两排齿孔
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
