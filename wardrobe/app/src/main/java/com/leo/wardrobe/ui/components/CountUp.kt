package com.leo.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.leo.wardrobe.ui.theme.EditorialMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** it-071：默认格式提升为顶层常量——稳定身份，调用方不传参时不再每次组合新造 lambda。 */
private val DefaultCountFormat: (Int) -> String = { it.toString() }

/**
 * 统计数字 count-up（DESIGN.md §5 反例 9，it-027）：首进从 0 起数，目标变化（档位切换）时从旧值
 * 过渡到新值，弹簧 EditorialMotion.smooth()；delayMs 用于同排多格错峰。
 */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = DefaultCountFormat,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    delayMs: Long = 0,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (delayMs > 0) delay(delayMs)
        anim.animateTo(target.toFloat(), EditorialMotion.smooth())
    }
    // it-071：anim.value 只在叶组合读取——弹簧期每帧重组止于本组合，不上浮调用方
    CountUpLabel(anim, format, style, color, modifier)
}

@Composable
private fun CountUpLabel(
    anim: Animatable<Float, AnimationVector1D>,
    format: (Int) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    Text(format(anim.value.roundToInt()), style = style, color = color, modifier = modifier)
}
