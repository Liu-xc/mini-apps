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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import com.leo.darkroom.ui.theme.pressScale

/** W1 = 相册（it-012 IA 重构）：进去就是照片墙——小图网格浏览，点缩略图进画册模式；
 * 顶行只有 拍照 与 设置 两个图标；未授权/空相册给一个安静的引导态（不是表单）。 */
@Composable
fun PickScreen(
    vm: DarkroomViewModel,
    state: UiState,
    playEntrance: Boolean,
    onEntrancePlayed: () -> Unit,
) {
    val colors = editorialColors()
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> vm.onCameraResult(ok) }
    val cameraUri = remember { { vm.prepareCamera() } }
    val albumPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onAlbumPermissionResult(granted) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (playEntrance) onEntrancePlayed()
        // 已授权过（如重装后系统保留）则直接加载相册
        if (state.albumGranted == null &&
            context.checkSelfPermission(albumPermission()) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            vm.onAlbumPermissionResult(true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .padding(20.dp),
    ) {
        // 顶行：标签 + 拍照 + 设置（仅此三个元素）
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
                IconButton(
                    onClick = { cameraLauncher.launch(cameraUri()) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "现在拍一张", tint = colors.inkFaint)
                }
                IconButton(onClick = vm::openSettings, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = "设置", tint = colors.inkFaint)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.albumGranted != true -> EditorialEntrance(
                delayMs = 24, enabled = playEntrance, modifier = Modifier.weight(1f),
            ) {
                AlbumEmptyHint(
                    title = "相册是暗房的入口",
                    body = if (state.albumGranted == false)
                        "还没拿到相册权限——可以再试一次。"
                    else
                        "授权后在这里浏览照片，点开任意一张，开始显影。",
                    action = if (state.albumGranted == false) "再试一次" else "打开相册",
                    onAction = { albumPermissionLauncher.launch(albumPermission()) },
                )
            }

            state.albumLoading && state.album.isEmpty() -> EditorialEntrance(
                delayMs = 0, enabled = false, modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            state.album.isEmpty() -> EditorialEntrance(
                delayMs = 24, enabled = playEntrance, modifier = Modifier.weight(1f),
            ) {
                AlbumEmptyHint(
                    title = "相册里还没有照片",
                    body = "拍一张回来，它会是第一张相纸。",
                    action = "现在拍一张",
                    onAction = { cameraLauncher.launch(cameraUri()) },
                )
            }

            else -> EditorialEntrance(
                delayMs = 24, enabled = playEntrance, modifier = Modifier.weight(1f),
            ) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(108.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItemsIndexed(state.album) { index, photo ->
                        AlbumThumb(vm, photo, index)
                    }
                }
            }
        }

        EditorialEntrance(delayMs = 48, enabled = playEntrance) {
            Text(
                "全程离线 · 照片不上传",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** 相册缩略格（it-012）：小图 + 统一按压反馈，点按进画册模式 */
@Composable
private fun AlbumThumb(vm: DarkroomViewModel, photo: com.leo.darkroom.data.AlbumPhoto, index: Int) {
    val colors = editorialColors()
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
        value = vm.albumThumbnail(photo, 256)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .height(140.dp)
            .pressScale(interaction)
            .background(colors.surface)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = bitmap != null,
            ) { vm.openAlbumPager(index) },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = remember(bitmap) { bitmap!!.asImageBitmap() },
                contentDescription = "照片 ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 未授权/空相册的安静引导态（DESIGN §5.8：图形 + 行动按钮） */
@Composable
private fun AlbumEmptyHint(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = editorialColors()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(72.dp)) {
                // 一张未显影的相纸：白框 + 乳白成像区 + 石墨日期点
                drawRoundRect(
                    color = colors.surface,
                    topLeft = Offset(size.width * 0.14f, size.height * 0.04f),
                    size = Size(size.width * 0.72f, size.height * 0.92f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawRect(
                    color = colors.hairline,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.14f),
                    size = Size(size.width * 0.52f, size.height * 0.48f),
                )
                drawCircle(
                    color = colors.accent,
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * 0.32f, size.height * 0.76f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.paper,
                ),
            ) { Text(action) }
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
