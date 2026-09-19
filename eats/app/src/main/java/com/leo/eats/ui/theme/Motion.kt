package com.leo.eats.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 统一弹簧动效基调（specs/05-design-system.md，与 wardrobe 的 EditorialMotion 同参数）。
 */
object EatsMotion {
    /** 常规位移动画：高阻尼、丝滑无过冲（卡片、内容切换） */
    fun <T> smooth(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 强调动画：轻微过冲回弹（落定、选中、结果卡） */
    fun <T> pop(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f,
    )

    /** 弹跳：明显过冲（marker 点击、转盘指针落定） */
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 700f,
    )
}
