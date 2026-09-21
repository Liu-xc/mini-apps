package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 首屏瀑布入场（specs/05 动效 #7，it-028 落地）：透明度 + 上移 28f，逐项 24ms 错峰。
 * **仅首进播放**（DESIGN.md §3 入场预算）：animate=false 时直接显示，由屏幕级
 * rememberSaveable 标志传入，返回/二次进入不重放。
 */
@Composable
fun StaggeredEntrance(index: Int, animate: Boolean = true, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(index.coerceAtMost(12) * 24L)
        progress.animateTo(1f, tween(360))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28f
        },
    ) { content() }
}
