package com.leo.lottery.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 摇奖动效参数（DESIGN.md §3 三件套基准值）。
 */
object Motion {
    /** 常规位移：丝滑无过冲。 */
    fun <T> smooth(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 落定、确认、选中：轻微过冲。 */
    fun <T> pop(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f,
    )

    /** 随机落定、庆祝：明显过冲（常规导航禁用）。 */
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 700f,
    )

    /**
     * 系统「移除动画」感知（DESIGN §3 降级红线）：
     * animator_duration_scale=0 即减弱动态，调用方走瞬时落位路径。
     */
    @Composable
    fun reduceMotion(): Boolean {
        val context = LocalContext.current
        return remember {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }
    }
}
