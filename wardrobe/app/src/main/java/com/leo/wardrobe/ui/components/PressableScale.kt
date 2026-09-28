package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.leo.wardrobe.ui.theme.EditorialMotion
import kotlinx.coroutines.launch

/**
 * 按压缩放反馈（it-058 C3，DESIGN.md §3「点击必须立刻有视觉回应」）：
 * 按下快速缩到 [target]，抬手/取消以 [EditorialMotion.pop] 回弹。
 * 挂在已有 clickable 的同一元素上即可（自行侦测按压，不依赖 InteractionSource）。
 * 系统「移除动画」时不缩放；大体积感元素（照片卡/大 CTA）适用，
 * 文字按钮保留涟漪即可，不叠加双层反馈。
 */
@Composable
fun Modifier.pressScale(target: Float = 0.97f): Modifier {
    val reduce = EditorialMotion.reduceMotion()
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(target, reduce) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!reduce) scope.launch {
                    scale.animateTo(target, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                }
                waitForUpOrCancellation()
                scope.launch { scale.animateTo(1f, EditorialMotion.pop()) }
            }
        }
}
