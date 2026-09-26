package com.leo.wardrobe.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.leo.libs.carddeck.rememberDeckReduceMotion
import kotlin.math.abs

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

    /**
     * 落定轻弹（05 #2，it-047）：驱动 scale 1→1.03→1 的两段编排——
     * 80ms 快速上行 + [pop] 回落。顺序执行的两段单属性动画，非叠加弹簧。
     */
    suspend fun runSettlePulse(pulse: Animatable<Float, *>, up: Float = 1.03f) {
        pulse.animateTo(up, tween(80))
        pulse.animateTo(1f, pop())
    }

    /**
     * W1/W7 pager 吸附行为（it-047 #9/#11）：
     * - 常态：官方 [PagerDefaults.flingBehavior]，snap 弹簧收敛到 [smooth]；
     * - 系统「移除动画」：返回瞬时 [TargetedFlingBehavior]（就近整页直接落位）。
     *   官方 SnapFlingBehavior 把 decay+snap 全段包进固定 scale=1 的 MotionDurationScale
     *   withContext（foundation 1.8.3 字节码实证），不吃系统动画缩放——必须自实现降级。
     */
    @Composable
    fun pagerFling(state: PagerState, reduce: Boolean): TargetedFlingBehavior {
        val base = PagerDefaults.flingBehavior(state = state, snapAnimationSpec = smooth())
        if (!reduce) return base
        return remember(base) {
            object : TargetedFlingBehavior {
                override suspend fun ScrollScope.performFling(
                    initialVelocity: Float,
                    onRemainingScrollOffset: (Float) -> Unit,
                ): Float {
                    val nearest = state.layoutInfo.visiblePagesInfo.minByOrNull { abs(it.offset) }
                    if (nearest != null) scrollBy(nearest.offset.toFloat())
                    onRemainingScrollOffset(0f)
                    return 0f
                }
            }
        }
    }

    /**
     * 系统「移除动画」感知（DESIGN.md §3 降级红线；与 carddeck `rememberDeckReduceMotion`
     * 共享同一 Settings 事实源，分层各设入口）。
     */
    @Composable
    fun reduceMotion(): Boolean = rememberDeckReduceMotion()
}
