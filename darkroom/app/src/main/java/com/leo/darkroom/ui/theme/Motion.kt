package com.leo.darkroom.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings

/**
 * 编辑风动效参数（DESIGN.md §3 三件套，与 wardrobe/eats 同基准值）。
 */
object EditorialMotion {
    /** 常规位移动画：高阻尼、丝滑无过冲 */
    fun <T> smooth(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 强调动画：轻微过冲回弹（落定、选中、弹入） */
    fun <T> pop(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f,
    )

    /** 弹跳：明显过冲（庆祝、彩蛋）——常规导航禁用 */
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 700f,
    )

    /**
     * 出纸推进（it-007 M2）：分段机械顿挫——快推 → 微顿 → 再推 → 微顿 → 缓出回正。
     * 原实现在 pop 弹簧里匀速滑出，没有齿轮推进的重量感。
     * 返回 0..1 的推进量（0 = 未出纸，1 = 完全出纸），单调不减。
     */
    val ejectEase = Easing { t ->
        val x = t.coerceIn(0f, 1f)
        when {
            x < 0.14f -> 0.30f * (x / 0.14f)
            x < 0.22f -> 0.30f + 0.025f * ((x - 0.14f) / 0.08f)
            x < 0.36f -> 0.325f + 0.325f * ((x - 0.22f) / 0.14f)
            x < 0.44f -> 0.65f + 0.025f * ((x - 0.36f) / 0.08f)
            else -> {
                val u = ((x - 0.44f) / 0.56f).coerceIn(0f, 1f)
                0.675f + 0.325f * (u * u * (3f - 2f * u))
            }
        }
    }

    /** 出纸总时长：首段 34% 内完成主要推进（≈290ms 的快推） */
    const val EJECT_MS = 850

    /** 落定轻弹：scale 1→up→1 两段单属性动画（非叠加弹簧） */
    suspend fun runSettlePulse(pulse: Animatable<Float, *>, up: Float = 1.03f) {
        pulse.animateTo(up, tween(80))
        pulse.animateTo(1f, pop())
    }

    /**
     * 系统「移除动画」感知（DESIGN.md §3 降级红线）：
     * animator_duration_scale=0 即视为减弱动态，调用方走瞬时落位路径。
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
