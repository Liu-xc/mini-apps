package com.leo.wardrobe.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 编辑风动效参数（specs/05-design-system.md）。
 * material3 1.4.0 稳定版尚未公开 Expressive motionScheme（ADR-004），
 * 因此用统一的弹簧参数自建「高表现力但克制」的动效基调。
 */
object EditorialMotion {
    /** 常规位移动画：高阻尼、丝滑无过冲（卡片、内容切换） */
    fun <T> smooth(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 强调动画：轻微过冲回弹（落定、收藏、选中） */
    fun <T> pop(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f,
    )

    /** 老虎机式弹跳：明显过冲（随机落定、彩屑触发） */
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 700f,
    )
}
