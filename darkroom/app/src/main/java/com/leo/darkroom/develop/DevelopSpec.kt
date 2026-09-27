package com.leo.darkroom.develop

import androidx.compose.runtime.Immutable

/** 显影阶段（it-001 体验设计：潜影 → 浮现 → 定影 三阶段曲线） */
enum class DevelopPhase(val label: String) {
    LATENT("潜影"),
    EMERGING("浮现"),
    FIXED("定影"),
}

/**
 * 某一显影进度下的全部视觉参数——显影台预览、导出位图、视频逐帧三渲染器共用的单一真源。
 * 确定性：同一进度永远映射到同一画面（it-001 AC2 药水条可倒放重看的根据）。
 */
@Immutable
data class DevelopVisual(
    val phase: DevelopPhase,
    /** 照片整体不透明度 0..1（影像在相纸上浮现的程度） */
    val imageAlpha: Float,
    /** 饱和度 0..1 */
    val saturation: Float,
    /** 对比度 0.8..1.1 */
    val contrast: Float,
    /** 亮度增益 0.55..1（潜影期偏暗） */
    val brightness: Float,
    /** 色温偏移 -0.5..0.5（负=青冷，正=暖；显影趋稳时回正） */
    val warmth: Float,
    /** 漫射模糊半径（相对照片短边的比例 0..0.05） */
    val blurFraction: Float,
    /** 中心向外晕开的显影半径 0..1（相片半对角线比例） */
    val reveal: Float,
    /** 颗粒强度 0..1（定影后保留细颗粒） */
    val grain: Float,
    /** 暗角强度 0..1 */
    val vignette: Float,
)

/**
 * 显影曲线（it-001 体验设计表）：
 * P1 潜影 0–15%：灰绿暗哑负片感；P2 浮现 15–70%：中心晕开、渐显色、漂移模糊；
 * P3 定影 70–100%：对比锐度归位、颗粒沉降、色温回正。
 */
object DevelopSpec {

    const val LATENT_END = 0.15f
    const val EMERGING_END = 0.70f

    fun phaseAt(progress: Float): DevelopPhase = when {
        progress < LATENT_END -> DevelopPhase.LATENT
        progress < EMERGING_END -> DevelopPhase.EMERGING
        else -> DevelopPhase.FIXED
    }

    /** 确定性映射：进度 → 全部视觉参数 */
    fun visualAt(progress: Float): DevelopVisual {
        val p = progress.coerceIn(0f, 1f)
        val latent = smoothstep(0f, LATENT_END, p)
        val emerge = smoothstep(LATENT_END, EMERGING_END, p)
        val fix = smoothstep(EMERGING_END, 1f, p)
        return DevelopVisual(
            phase = phaseAt(p),
            imageAlpha = 0.10f + 0.25f * latent + 0.65f * emerge,
            saturation = 0.05f + 0.30f * latent + 0.50f * emerge + 0.15f * fix,
            contrast = 0.85f + 0.05f * latent + 0.12f * emerge + 0.08f * fix,
            brightness = 0.60f + 0.15f * latent + 0.22f * emerge + 0.03f * fix,
            warmth = -0.35f * latent + 0.85f * emerge - 0.42f * fix,
            blurFraction = 0.045f - 0.012f * latent - 0.029f * emerge - 0.004f * fix,
            reveal = (0.10f + 0.15f * latent + 0.75f * emerge).coerceAtMost(1f),
            grain = 0.55f - 0.15f * latent - 0.18f * emerge - 0.10f * fix,
            vignette = 0.75f - 0.12f * latent - 0.30f * emerge - 0.13f * fix,
        )
    }

    /**
     * 进度 → RGBA 4×5 色彩矩阵（20 floats，行优先、每行末列为偏移）。
     * 合成顺序：亮度 → 对比度 → 色温 → 饱和度。纯 float 运算，
     * Compose ColorFilter 与 android.graphics ColorMatrix 两条渲染管线共用。
     */
    fun colorMatrix(v: DevelopVisual): FloatArray {
        val b = v.brightness
        val c = v.contrast
        val w = v.warmth.coerceIn(-0.5f, 0.5f)
        val s = v.saturation.coerceIn(0f, 1f)

        // 亮度：直接增益
        val bright = floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        // 对比度：绕 0.5 灰轴拉伸
        val contrast = floatArrayOf(
            c, 0f, 0f, 0f, (0.5f - 0.5f * c),
            0f, c, 0f, 0f, (0.5f - 0.5f * c),
            0f, 0f, c, 0f, (0.5f - 0.5f * c),
            0f, 0f, 0f, 1f, 0f,
        )
        // 色温：暖 = R 增 B 减（负值反向），G 微降防过曝
        val warm = floatArrayOf(
            1f + 0.10f * w, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - 0.10f * w, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        // 饱和度：标准亮度加权
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        val sat = floatArrayOf(
            lumR + (1 - lumR) * s, lumG - lumG * s, lumB - lumB * s, 0f, 0f,
            lumR - lumR * s, lumG + (1 - lumG) * s, lumB - lumB * s, 0f, 0f,
            lumR - lumR * s, lumG - lumG * s, lumB + (1 - lumB) * s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        // 列向量约定 v'=M·v：先亮度→对比→色温→饱和 = 矩阵乘序 sat·warm·contrast·bright
        return concat(sat, concat(warm, concat(contrast, bright)))
    }

    /** 模糊像素数：比例 × 照片短边 */
    fun blurPx(v: DevelopVisual, photoShortSidePx: Float): Float = v.blurFraction * photoShortSidePx

    /** 4×5 仿射色彩矩阵合成：out = a·b（列向量约定，a 在 b 之后作用于输入） */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 20 && b.size == 20) { "color matrix must be 4x5 (20 floats)" }
        val out = FloatArray(20)
        for (row in 0 until 4) {
            // 线性部分：Σ_k a[row][k]·b[k][col]
            for (col in 0 until 4) {
                var acc = 0f
                for (k in 0 until 4) acc += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = acc
            }
            // 偏移列（col=4）：a.linear·b.t + a.t（末列是仿射平移，无第 5 行）
            var off = a[row * 5 + 4]
            for (k in 0 until 4) off += a[row * 5 + k] * b[k * 5 + 4]
            out[row * 5 + 4] = off
        }
        return out
    }

    /** 平滑阶梯（Hermite），边缘不突跳 */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
