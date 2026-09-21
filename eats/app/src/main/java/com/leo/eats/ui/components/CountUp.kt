package com.leo.eats.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.leo.eats.ui.theme.EatsMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 统计数字 count-up（DESIGN.md §5 反例 9，it-015）：首进从 0 起数，目标变化（档位切换）时从旧值
 * 过渡到新值，弹簧 EatsMotion.smooth()；delayMs 用于同排多格错峰。
 */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    delayMs: Long = 0,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (delayMs > 0) delay(delayMs)
        anim.animateTo(target.toFloat(), EatsMotion.smooth())
    }
    Text(format(anim.value.roundToInt()), style = style, color = color, modifier = modifier)
}

/** 花费等浮点口径的 count-up（it-015），格式化交给调用方（如 ¥ + trim） */
@Composable
fun CountUpFloatText(
    target: Float,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    delayMs: Long = 0,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (delayMs > 0) delay(delayMs)
        anim.animateTo(target, EatsMotion.smooth())
    }
    Text(format(anim.value), style = style, color = color, modifier = modifier)
}
