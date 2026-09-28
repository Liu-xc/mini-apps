package com.leo.darkroom.card

import com.leo.darkroom.develop.DevelopSpec

/** Deterministic print treatments shared by the preview, still export, and video frames. */
enum class PhotoLook(
    val label: String,
    val note: String,
    private val saturation: Float,
    private val redGain: Float,
    private val greenGain: Float,
    private val blueGain: Float,
    private val redOffset: Float = 0f,
    private val greenOffset: Float = 0f,
    private val blueOffset: Float = 0f,
    private val contrast: Float = 1f,
    val grainScale: Float = 1f,
    val vignetteScale: Float = 1f,
    val glowStrength: Float = 0f,
    val warmHighlights: Boolean = false,
    val cinematicSplitTone: Boolean = false,
) {
    ORIGINAL("原色", "保留现场的光", 1f, 1f, 1f, 1f),
    WARM("暖片", "柔和一点的暖意", 0.96f, 1.055f, 1.015f, 0.93f, 1f, 0.5f, -1f, 1.015f, 1.08f, 1.03f, warmHighlights = true),
    SILVER("银影", "安静的银盐层次", 0f, 1f, 1f, 1f, contrast = 1.045f, grainScale = 1.12f, vignetteScale = 1.04f),
    CINEMA("青幕", "冷影里留一点暖光", 0.84f, 0.97f, 1f, 1.035f, 1.5f, 0.5f, 1.5f, 1.02f, 0.92f, 1.08f, cinematicSplitTone = true),
    SOFT_GLOW("柔光", "让亮部轻轻漫开", 0.94f, 1.015f, 1.01f, 0.99f, 0.5f, 0.5f, 0.5f, 0.97f, 0.78f, 0.86f, 0.36f);

    /** Compose a restrained look matrix. ORIGINAL intentionally returns identity for pixel parity. */
    fun colorMatrix(): FloatArray {
        if (this == ORIGINAL) return IDENTITY.copyOf()

        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        val sat = floatArrayOf(
            lumR + (1f - lumR) * saturation, lumG - lumG * saturation, lumB - lumB * saturation, 0f, 0f,
            lumR - lumR * saturation, lumG + (1f - lumG) * saturation, lumB - lumB * saturation, 0f, 0f,
            lumR - lumR * saturation, lumG - lumG * saturation, lumB + (1f - lumB) * saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val grade = floatArrayOf(
            redGain, 0f, 0f, 0f, redOffset,
            0f, greenGain, 0f, 0f, greenOffset,
            0f, 0f, blueGain, 0f, blueOffset,
            0f, 0f, 0f, 1f, 0f,
        )
        val contrastMatrix = floatArrayOf(
            contrast, 0f, 0f, 0f, (0.5f - 0.5f * contrast) * 255f,
            0f, contrast, 0f, 0f, (0.5f - 0.5f * contrast) * 255f,
            0f, 0f, contrast, 0f, (0.5f - 0.5f * contrast) * 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        return DevelopSpec.concat(contrastMatrix, DevelopSpec.concat(grade, sat))
    }

    companion object {
        private val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
