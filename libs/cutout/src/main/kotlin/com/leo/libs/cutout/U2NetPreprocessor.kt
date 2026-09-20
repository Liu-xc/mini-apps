package com.leo.libs.cutout

/**
 * u2netp 输入预处理（纯函数，rembg 同规格，wardrobe it-016）：
 * RGBA（w×h）→ 双线性缩放到 320×320 → NCHW 1×3×320×320 float32，ImageNet mean/std 归一化。
 */
internal object U2NetPreprocessor {

    const val INPUT_SIDE = 320
    private const val AREA = INPUT_SIDE * INPUT_SIDE

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** @param rgba 长度必须为 width×height×4，每像素 R,G,B,A 顺序 */
    fun preprocess(rgba: ByteArray, width: Int, height: Int): FloatArray {
        if (width <= 0 || height <= 0) throw CutoutException.BadInput("bad size ${width}x$height")
        if (rgba.size != width * height * 4) {
            throw CutoutException.BadInput("rgba size ${rgba.size} != ${width}x$height x4")
        }
        val out = FloatArray(3 * AREA)
        for (y in 0 until INPUT_SIDE) {
            val gy = (y + 0.5f) * height / INPUT_SIDE - 0.5f
            val gy0 = floor(gy)
            val y0 = gy0.toInt().coerceIn(0, height - 1)
            val y1 = (y0 + 1).coerceIn(0, height - 1)
            val fy = (gy - gy0).coerceIn(0f, 1f)
            for (x in 0 until INPUT_SIDE) {
                val gx = (x + 0.5f) * width / INPUT_SIDE - 0.5f
                val gx0 = floor(gx)
                val x0 = gx0.toInt().coerceIn(0, width - 1)
                val x1 = (x0 + 1).coerceIn(0, width - 1)
                val fx = (gx - gx0).coerceIn(0f, 1f)
                val i00 = (y0 * width + x0) * 4
                val i10 = (y0 * width + x1) * 4
                val i01 = (y1 * width + x0) * 4
                val i11 = (y1 * width + x1) * 4
                val w00 = (1f - fx) * (1f - fy)
                val w10 = fx * (1f - fy)
                val w01 = (1f - fx) * fy
                val w11 = fx * fy
                val dst = y * INPUT_SIDE + x
                for (c in 0 until 3) {
                    val v = (rgba[i00 + c].toInt() and 0xFF) * w00 +
                        (rgba[i10 + c].toInt() and 0xFF) * w10 +
                        (rgba[i01 + c].toInt() and 0xFF) * w01 +
                        (rgba[i11 + c].toInt() and 0xFF) * w11
                    out[c * AREA + dst] = ((v / 255f) - MEAN[c]) / STD[c]
                }
            }
        }
        return out
    }

    private fun floor(v: Float): Float = kotlin.math.floor(v)
}
