package com.leo.libs.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskToAlphaTest {

    private val side = U2NetPreprocessor.INPUT_SIDE

    private fun solidRgba(width: Int, height: Int): ByteArray =
        ByteArray(width * height * 4) { if (it % 4 == 3) 0 else 127 }

    @Test
    fun `constant mask stays fully opaque`() {
        // 常量掩码（无显著主体信号）→ 保守全 255，不抠
        val mask = FloatArray(side * side) { 0.5f }
        val w = 8
        val h = 8
        val rgba = solidRgba(w, h)
        MaskToAlpha.apply(mask, rgba, w, h)
        for (i in 0 until w * h) {
            assertEquals(255, rgba[i * 4 + 3].toInt() and 0xFF)
        }
    }

    @Test
    fun `all zero mask stays fully opaque`() {
        val mask = FloatArray(side * side)
        val rgba = solidRgba(4, 4)
        MaskToAlpha.apply(mask, rgba, 4, 4)
        for (i in 0 until 16) {
            assertEquals(255, rgba[i * 4 + 3].toInt() and 0xFF)
        }
    }

    @Test
    fun `horizontal gradient spans full alpha range`() {
        // 320×320 水平渐变（左 0 右 1）→ 320×320 恒等缩放，alpha 左 0 右 255
        val mask = FloatArray(side * side) { idx -> (idx % side) / (side - 1f) }
        val rgba = solidRgba(side, side)
        MaskToAlpha.apply(mask, rgba, side, side)
        assertEquals(0, rgba[3].toInt() and 0xFF)
        assertEquals(255, rgba[((side - 1) * side + side - 1) * 4 + 3].toInt() and 0xFF)
        // 单调不减（沿行）
        var prev = -1
        for (x in 0 until side) {
            val a = rgba[(side / 2 * side + x) * 4 + 3].toInt() and 0xFF
            assertTrue(a >= prev)
            prev = a
        }
    }

    @Test
    fun `rgb channels untouched`() {
        val mask = FloatArray(side * side) { 1f }
        val rgba = solidRgba(4, 4)
        val before = rgba.copyOf()
        MaskToAlpha.apply(mask, rgba, 4, 4)
        for (i in 0 until 16) {
            assertEquals(before[i * 4], rgba[i * 4])
            assertEquals(before[i * 4 + 1], rgba[i * 4 + 1])
            assertEquals(before[i * 4 + 2], rgba[i * 4 + 2])
        }
    }
}
