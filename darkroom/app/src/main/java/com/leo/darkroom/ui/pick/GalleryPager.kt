package com.leo.darkroom.ui.pick

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.CardLayout
import com.leo.darkroom.card.CardSpec
import com.leo.darkroom.data.AlbumPhoto
import com.leo.darkroom.develop.EjectStyle
import com.leo.darkroom.platform.Haptics
import com.leo.darkroom.ui.develop.DevelopCard
import com.leo.darkroom.ui.theme.EditorialMotion
import com.leo.darkroom.ui.theme.editorialColors
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 沉浸式相册显影（it-011 US-18）：首页大卡横滑翻阅相册；首次（本次启动内）成为当前页
 * 的照片跑一遍当前模式的出纸+显影动画，动画中点按=跳到成品，成品点按=进成片页；
 * 已播过的翻回直接显示成品；重播由底部按钮触发（VM 移出 played 集）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryPager(
    vm: DarkroomViewModel,
    state: UiState,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    // 近尾部预取下一页相册索引
    LaunchedEffect(pagerState.currentPage, state.album.size) {
        if (state.album.size - pagerState.currentPage < 10 && state.album.isNotEmpty()) {
            vm.loadMoreAlbum()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        pageSpacing = 16.dp,
        contentPadding = PaddingValues(horizontal = 44.dp),
    ) { page ->
        val photo = state.album.getOrNull(page) ?: return@HorizontalPager
        GalleryCard(
            vm = vm,
            photo = photo,
            spec = state.spec,
            mode = state.mode,
            speedMs = state.speed.durationMs.toInt(),
            isCurrent = pagerState.currentPage == page,
            shouldAnimate = pagerState.currentPage == page && photo.id !in state.playedIds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GalleryCard(
    vm: DarkroomViewModel,
    photo: AlbumPhoto,
    spec: CardSpec,
    mode: com.leo.darkroom.develop.DevelopMode,
    speedMs: Int,
    isCurrent: Boolean,
    shouldAnimate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = editorialColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = EditorialMotion.reduceMotion()

    // 缩略图：按 id 解码 + 进程内缓存
    val bitmap by produceState<Bitmap?>(null, photo.id) {
        value = vm.albumThumbnail(photo)
    }

    // 出纸 + 显影进度（复用 W2 的曲线真源）
    val eject = remember(photo.id, mode) { Animatable(0f) }
    val progress = remember(photo.id, mode) { Animatable(1f) }
    var playing by remember(photo.id, mode) { mutableStateOf(false) }
    var job by remember(photo.id, mode) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun skipToResult() {
        job?.cancel()
        job = scope.launch {
            eject.snapTo(0f)
            progress.snapTo(1f)
        }
        playing = false
        vm.markGalleryPlayed(photo.id)
    }

    LaunchedEffect(isCurrent, shouldAnimate) {
        if (isCurrent && shouldAnimate) {
            if (reduceMotion) {
                eject.snapTo(0f)
                progress.snapTo(1f)
                vm.markGalleryPlayed(photo.id)
            } else {
                playing = true
                eject.snapTo(1f)
                progress.snapTo(0f)
                eject.animateTo(0f, tween(EditorialMotion.EJECT_MS, easing = EditorialMotion.ejectEase))
                progress.animateTo(1f, tween(speedMs, easing = LinearEasing))
                playing = false
                vm.markGalleryPlayed(photo.id)
                Haptics.confirm(context)
            }
        } else if (!isCurrent) {
            // 滑走的页面一律回正成品态；未播过的回来会重新出纸
            job?.cancel()
            playing = false
            eject.snapTo(0f)
            progress.snapTo(1f)
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val layout = CardLayout.solve(
            with(density) { maxWidth.toPx() - 2.dp.toPx() },
            with(density) { maxHeight.toPx() },
            mode,
        )
        Box(
            Modifier
                .graphicsLayer {
                    when (mode.eject) {
                        EjectStyle.SLOT_RISE -> {
                            translationY = eject.value * layout.height
                            rotationZ = sin((1f - eject.value) * PI.toFloat()) * 1.15f
                        }

                        EjectStyle.FILM_WIND -> {
                            translationX = eject.value * layout.width
                            rotationZ = sin((1f - eject.value) * PI.toFloat()) * 0.8f
                        }

                        EjectStyle.SCREEN_WAKE -> {
                            val wake = 1f - eject.value
                            scaleX = 0.96f + 0.04f * wake
                            scaleY = 0.96f + 0.04f * wake
                        }
                    }
                }
                .shadow(8.dp, RectangleShape)
                .clickable(enabled = bitmap != null) {
                    if (playing) skipToResult() else bitmap?.let { vm.openGalleryResult(photo, it) }
                },
        ) {
            if (bitmap != null) {
                DevelopCard(
                    photo = bitmap,
                    spec = spec,
                    mode = mode,
                    progress = progress.value,
                    cardWidthPx = layout.width,
                    grain = vm.grain,
                )
            } else {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationY = eject.value * layout.height
                        }
                        .background(colors.surface, RectangleShape)
                        .size(
                            with(density) { layout.width.toDp() },
                            with(density) { layout.height.toDp() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
        }

        if (playing) {
            Text(
                "显影中 · 点按跳过",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
