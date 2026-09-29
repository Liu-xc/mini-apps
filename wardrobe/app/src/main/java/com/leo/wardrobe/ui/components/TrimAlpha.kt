package com.leo.wardrobe.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * 透明边裁剪（it-061 修1）：解码后把 alpha 内容包围盒之外的透明边裁掉。
 *
 * 背景：透明底衣物素材（it-060 起）内容仅占图幅 23–44%，mat+Fit 槽位会把
 * 大片透明留白一起缩进格子——鞋/帽缩成一小条，观感「只展示了一半」。
 * 先裁边再 Fit，内容充满格高。非透明图（成品图等）返回原 bitmap 零影响；
 * 各边透明边 ≤2% 视为已贴边不裁，避免 1px 级裁剪徒增缓存条目。
 */
class TrimAlphaTransformation : Transformation {
    override val cacheKey: String = "trim-alpha"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)

        var top = -1
        var bottom = -1
        var left = -1
        var right = -1
        // 行扫描
        outer@ for (y in 0 until h) {
            val rowHas = (0 until w).any { pixels[y * w + it] ushr 24 > ALPHA_TOLERANCE }
            if (rowHas) {
                if (top == -1) top = y
                bottom = y
            }
        }
        if (top == -1) return input // 全透明：不动（异常素材兜底）
        outer2@ for (x in 0 until w) {
            val colHas = (0 until h).any { pixels[it * w + x] ushr 24 > ALPHA_TOLERANCE }
            if (colHas) {
                if (left == -1) left = x
                right = x
            }
        }
        val marginX = w * 0.02f
        val marginY = h * 0.02f
        val l = if (left > marginX) left else 0
        val t = if (top > marginY) top else 0
        val r = if (w - 1 - right > marginX) right + 1 else w
        val b = if (h - 1 - bottom > marginY) bottom + 1 else h
        if (l == 0 && t == 0 && r == w && b == h) return input
        return Bitmap.createBitmap(input, l, t, r - l, b - t)
    }

    private companion object {
        /** 与人眼「接近透明」一致的容差：半透明抗锯齿边缘不算内容 */
        const val ALPHA_TOLERANCE = 24
    }
}
