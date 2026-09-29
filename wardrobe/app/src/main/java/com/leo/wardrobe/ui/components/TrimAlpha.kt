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
        // it-071 快速短路：9 个采样点（4 角 + 4 边中 + 中心）全部不透明 → 非透明素材，
        // 跳过全图 IntArray 分配与行/列两趟扫描（真照片/不透明 WebP 冷解码零扫描）。
        // 透明素材必有贴边透明区，四角/边中必有透明点 → 仍走全扫描，裁剪语义不变。
        if (likelyOpaque(input)) return input
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

    /** 9 点采样全不透明 → 大概率非透明素材。只看边缘可达的点：裁剪包围盒由贴边透明区决定。 */
    private fun likelyOpaque(b: Bitmap): Boolean {
        val w = b.width - 1
        val h = b.height - 1
        val mx = w / 2
        val my = h / 2
        val xs = intArrayOf(0, w, 0, w, mx, mx, 0, w, mx)
        val ys = intArrayOf(0, 0, h, h, 0, h, my, my, my)
        for (i in xs.indices) {
            if (b.getPixel(xs[i], ys[i]) ushr 24 <= ALPHA_TOLERANCE) return false
        }
        return true
    }

    private companion object {
        /** 与人眼「接近透明」一致的容差：半透明抗锯齿边缘不算内容 */
        const val ALPHA_TOLERANCE = 24
    }
}
