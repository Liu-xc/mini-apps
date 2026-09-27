package com.leo.darkroom.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.random.Random

/**
 * 胶片颗粒噪声图：预生成一张灰度噪声瓦片，渲染时以 Overlay 混合 + alpha 叠加。
 * 预览与导出共用同一张图（确定性种子），保证所见即所得。
 */
object GrainNoise {

    const val TILE_SIZE = 256

    fun bitmap(size: Int = TILE_SIZE, seed: Long = 2026_0927L): Bitmap {
        val pixels = IntArray(size * size)
        val rng = Random(seed)
        for (i in pixels.indices) {
            // 均值 128 的灰度 ±22，Overlay 混合后呈中性颗粒
            val v = (128 + (rng.nextFloat() - 0.5f) * 44f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** 预览用：从打包内置 PNG 读取；导出用 bitmap() 内存生成即可 */
    fun fromPng(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
