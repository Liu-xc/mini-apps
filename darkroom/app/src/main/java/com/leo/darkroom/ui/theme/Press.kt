package com.leo.darkroom.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 统一按压反馈（it-010）：按下 0.97 下缩、松手 pop 回弹；减弱动态不缩放。
 * 用法：clickable(interactionSource = src) 与 pressScale(src) 配同一 source。
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = EditorialMotion.reduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = EditorialMotion.pop(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
