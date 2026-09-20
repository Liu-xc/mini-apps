package com.leo.libs.cutout

/**
 * 掩码后处理（纯函数，rembg 同规格 + 边缘锐化，wardrobe it-016）：
 * 320×320 显著性掩码 → 双线性放大到 w×h → min-max 拉伸 → 边缘锐化窗 → 写回 RGBA 的 alpha 通道。
 *
 * 锐化窗 [EDGE_LO, EDGE_HI]：掩码软边像素的 RGB 仍是原背景色，半透明带越宽「背景色晕边」越明显；
 * 压缩半透明带可显著抑制晕边，同时保留 1px 级抗锯齿（ADR-005）。
 * 全常量掩码（模型认为无显著主体或全显著）保守输出全 255（完全不抠），避免整图误透明。
 */
internal object MaskToAlpha {

    /** 锐化窗下/上阈（归一化掩码）：低于 LO 判背景、高于 HI 判主体、之间线性过渡。 */
    const val EDGE_LO = 0.35f
    const val EDGE_HI = 0.85f

    /** 原地更新 [rgba] 的 alpha 通道；RGB 不动。 */
    fun apply(mask: FloatArray, rgba: ByteArray, width: Int, height: Int) {
        require(mask.size == U2NetPreprocessor.INPUT_SIDE * U2NetPreprocessor.INPUT_SIDE) {
            "mask size ${mask.size}"
        }
        // 1) 320×320 → w×h 双线性
        val scaled = FloatArray(width * height)
        for (y in 0 until height) {
            val gy = (y + 0.5f) * U2NetPreprocessor.INPUT_SIDE / height - 0.5f
            val gy0 = kotlin.math.floor(gy)
            val y0 = gy0.toInt().coerceIn(0, U2NetPreprocessor.INPUT_SIDE - 1)
            val y1 = (y0 + 1).coerceIn(0, U2NetPreprocessor.INPUT_SIDE - 1)
            val fy = (gy - gy0).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val gx = (x + 0.5f) * U2NetPreprocessor.INPUT_SIDE / width - 0.5f
                val gx0 = kotlin.math.floor(gx)
                val x0 = gx0.toInt().coerceIn(0, U2NetPreprocessor.INPUT_SIDE - 1)
                val x1 = (x0 + 1).coerceIn(0, U2NetPreprocessor.INPUT_SIDE - 1)
                val fx = (gx - gx0).coerceIn(0f, 1f)
                val m00 = mask[y0 * U2NetPreprocessor.INPUT_SIDE + x0]
                val m10 = mask[y0 * U2NetPreprocessor.INPUT_SIDE + x1]
                val m01 = mask[y1 * U2NetPreprocessor.INPUT_SIDE + x0]
                val m11 = mask[y1 * U2NetPreprocessor.INPUT_SIDE + x1]
                scaled[y * width + x] =
                    m00 * (1f - fx) * (1f - fy) + m10 * fx * (1f - fy) +
                    m01 * (1f - fx) * fy + m11 * fx * fy
            }
        }
        // 2) min-max 拉伸 + 锐化窗后写 alpha（rembg 在 resize 之后归一化，同序）
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in scaled) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val span = mx - mn
        val band = EDGE_HI - EDGE_LO
        for (i in scaled.indices) {
            val a = if (span > 0f) {
                val n = ((scaled[i] - mn) / span).coerceIn(0f, 1f)
                (((n - EDGE_LO) / band).coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            } else 255
            rgba[i * 4 + 3] = a.toByte()
        }
    }
}
