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
    /**
     * 染料分层上色时序（it-007 M2）：真实相纸的青/品红/黄三层不是同时出现，
     * 三者是各通道的瞬时衰减——青层先压红（中途青灰）、品红层压绿、黄层压蓝，
     * 定影后全部回到 1，白平衡归位。
     */
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    /** 暗部抬升（0..1，写入 0..255 色彩空间的偏移比例）——相纸不给死黑 */
    val shadowLift: Float = 0f,
    /** 高光压缩 0..1（相纸动态范围窄） */
    val highlightGain: Float = 1f,
    /**
     * 负片反相程度 0..1（it-007 M3 胶片模式）：1 = 完全负片，0 = 正片。
     * 4×5 色彩矩阵可完整表达（gain = 1-2t，offset = 255t）。
     */
    val invert: Float = 0f,
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

    /** 确定性映射：进度 → 全部视觉参数（默认拍立得） */
    fun visualAt(progress: Float): DevelopVisual = visualAt(DevelopMode.POLAROID, progress)

    /** 确定性映射：模式 + 进度 → 全部视觉参数（it-007 M3） */
    fun visualAt(mode: DevelopMode, progress: Float): DevelopVisual {
        val p = progress.coerceIn(0f, 1f)
        val latent = smoothstep(0f, LATENT_END, p)
        val emerge = smoothstep(LATENT_END, EMERGING_END, p)
        val fix = smoothstep(EMERGING_END, 1f, p)
        return when (mode) {
            DevelopMode.POLAROID -> polaroidVisual(p, latent, emerge, fix)
            DevelopMode.DIGITAL -> digitalVisual(p, latent, emerge, fix)
            DevelopMode.FILM -> filmVisual(p, latent, emerge, fix)
        }
    }

    /** 拍立得：白浊层均匀消散 + 影调顺序显影（暗部先现、亮部后至）+ 分染料上色 */
    private fun polaroidVisual(
        p: Float,
        latent: Float,
        emerge: Float,
        fix: Float,
    ): DevelopVisual = DevelopVisual(
        phase = phaseAt(p),
        // it-009 早期提速：起手即有淡影透出（0.16），潜影段贡献加大——去掉前 ~15% 的纯白死区
        imageAlpha = 0.16f + 0.30f * latent + 0.54f * emerge,
        saturation = 0.05f + 0.30f * latent + 0.50f * emerge + 0.15f * fix,
        contrast = 0.88f + 0.04f * latent + 0.11f * emerge + 0.07f * fix,
        brightness = 0.66f + 0.14f * latent + 0.19f * emerge + 0.01f * fix,
        // 中途压向青冷、定影回正微暖（真实相纸先冷后暖）
        warmth = -0.38f * latent - 0.18f * emerge + 0.60f * fix,
        // it-008 三次修正：真实显影中影像本身是锐的，糊的只是白浊层——
        // 起手仅极轻柔焦（0.016→末态 0.006），去掉旧版 0.05 的「晕开扩散」观感
        blurFraction = 0.016f - 0.004f * latent - 0.004f * emerge - 0.010f * fix,
        // it-008 二次修正：白浊层全程渐次变薄、定影段仍在收白；
        // it-009 早期提速：起手 11% 已透、潜影段快开（15% 处 ≈30%）——前期不再是空白
        reveal = (0.11f + 0.18f * latent + 0.51f * emerge + 0.20f * fix).coerceAtMost(1f),
        grain = 0.55f - 0.12f * latent - 0.15f * emerge - 0.22f * fix,
        // it-008 三次修正：暗角是成片特征、不是显影特征——起手近乎无（0.04），
        // 定影段才落到纸感 0.20。旧版起手 0.45 的角部压暗让均匀浮现读成「中心晕开」
        vignette = 0.04f + 0.02f * latent + 0.04f * emerge + 0.02f * fix,
        // 染料分层：青层压红（早）→ 黄层压蓝（中）→ 品红层压绿（晚），定影后全部归位
        redGain = 1f - 0.15f * bump(p, 0.10f, 0.38f, 0.88f),
        greenGain = 1f - 0.06f * bump(p, 0.42f, 0.68f, 0.96f),
        blueGain = 1f - 0.05f * bump(p, 0.28f, 0.55f, 0.92f),
        shadowLift = 0.012f + 0.038f * fix,
        // it-008 三次修正：影调显影顺序——亮部高光被压住最后才到位（0.62→0.94），
        // 暗部/中间调先从白浊层后显出，对表真实相纸「先见影、后见光」的浮现次序
        highlightGain = 0.62f + 0.10f * latent + 0.16f * emerge + 0.10f * fix,
        invert = 0f,
    )

    /** 数码相机：屏幕点亮 + 网格块加载——无染料、无窄动态、末态干净锐利 */
    private fun digitalVisual(
        p: Float,
        latent: Float,
        emerge: Float,
        fix: Float,
    ): DevelopVisual = DevelopVisual(
        phase = phaseAt(p),
        // it-009 早期提速：开机即见首批格子亮起
        imageAlpha = 0.10f + 0.36f * latent + 0.54f * emerge,
        saturation = 0.40f + 0.28f * latent + 0.27f * emerge + 0.05f * fix,
        contrast = 0.88f + 0.05f * latent + 0.11f * emerge + 0.06f * fix,
        brightness = 0.76f + 0.10f * latent + 0.12f * emerge + 0.02f * fix,
        warmth = 0f,
        blurFraction = 0.030f - 0.014f * latent - 0.014f * emerge - 0.002f * fix,
        reveal = (0.24f + 0.40f * latent + 0.36f * emerge).coerceAtMost(1f),
        // 颗粒是传感器噪点：起手明显，成像后收得很小
        grain = 0.50f - 0.20f * latent - 0.15f * emerge - 0.09f * fix,
        vignette = 0.16f - 0.04f * latent - 0.06f * emerge - 0.02f * fix,
        redGain = 1f,
        greenGain = 1f,
        blueGain = 1f,
        shadowLift = 0f,
        highlightGain = 1f,
        invert = 0f,
    )

    /** 胶片：负片从反相翻正 + 重颗粒 + 片基影调 */
    private fun filmVisual(
        p: Float,
        latent: Float,
        emerge: Float,
        fix: Float,
    ): DevelopVisual = DevelopVisual(
        phase = phaseAt(p),
        // it-009 早期提速：片盒卷出后负片底灰尽快可见
        imageAlpha = 0.20f + 0.33f * latent + 0.47f * emerge,
        saturation = 0.12f + 0.23f * latent + 0.45f * emerge + 0.20f * fix,
        contrast = 0.80f + 0.06f * latent + 0.14f * emerge + 0.10f * fix,
        brightness = 0.70f + 0.12f * latent + 0.15f * emerge + 0.03f * fix,
        warmth = -0.30f * latent - 0.15f * emerge + 0.55f * fix,
        blurFraction = 0.035f - 0.010f * latent - 0.018f * emerge - 0.013f * fix,
        reveal = (0.20f + 0.34f * latent + 0.46f * emerge).coerceAtMost(1f),
        grain = 0.60f - 0.14f * latent - 0.16f * emerge - 0.22f * fix,
        vignette = 0.40f - 0.06f * latent - 0.12f * emerge - 0.04f * fix,
        shadowLift = 0.015f + 0.035f * fix,
        highlightGain = 1f - 0.05f * fix,
        // 负片在显影中段翻正：起手全反相，72% 处归为正片
        invert = 1f - smoothstep(0.15f, 0.72f, p),
    )

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
        // 相纸/片基影调：染料分层增益 + 窄动态（暗部 lift / 高光 gain）+ 负片反相。
        // 注意本管线工作在 0..255 色彩空间，lift 与反相偏移必须 ×255 写入偏移列。
        val lift = v.shadowLift * 255f
        val invert = v.invert.coerceIn(0f, 1f)
        val invertGain = 1f - 2f * invert
        val invertOffset = 255f * invert
        val tone = floatArrayOf(
            v.redGain * v.highlightGain * invertGain, 0f, 0f, 0f, lift + invertOffset,
            0f, v.greenGain * v.highlightGain * invertGain, 0f, 0f, lift + invertOffset,
            0f, 0f, v.blueGain * v.highlightGain * invertGain, 0f, lift + invertOffset,
            0f, 0f, 0f, 1f, 0f,
        )
        // 列向量约定 v'=M·v：先亮度→对比→色温→饱和→影调 = 矩阵乘序 tone·sat·warm·contrast·bright
        return concat(tone, concat(sat, concat(warm, concat(contrast, bright))))
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

    /** 钟形 bump：[a, peak, b] 起—峰—落，返回 0..1（染料分层上色时序用） */
    fun bump(p: Float, a: Float, peak: Float, b: Float): Float =
        smoothstep(a, peak, p) - smoothstep(peak, b, p)
}
