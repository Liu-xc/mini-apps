package com.leo.libs.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class U2NetPreprocessorTest {

    @Test
    fun `bad size throws BadInput`() {
        assertThrows(CutoutException.BadInput::class.java) {
            U2NetPreprocessor.preprocess(ByteArray(4), 0, 1)
        }
        assertThrows(CutoutException.BadInput::class.java) {
            U2NetPreprocessor.preprocess(ByteArray(3), 1, 1)
        }
    }

    @Test
    fun `uniform red pixel normalizes per channel`() {
        // 1×1 纯红 RGBA(255,0,0,255) → 拉伸为 320×320 常量场
        val out = U2NetPreprocessor.preprocess(byteArrayOf(255.toByte(), 0, 0, 255.toByte()), 1, 1)
        val area = U2NetPreprocessor.INPUT_SIDE * U2NetPreprocessor.INPUT_SIDE
        assertEquals(3 * area, out.size)
        val expectR = (1f - 0.485f) / 0.229f
        val expectG = (0f - 0.456f) / 0.224f
        val expectB = (0f - 0.406f) / 0.225f
        for (i in 0 until area) {
            assertEquals(expectR, out[i], 1e-4f)
            assertEquals(expectG, out[area + i], 1e-4f)
            assertEquals(expectB, out[2 * area + i], 1e-4f)
        }
    }

    @Test
    fun `input at model side resizes identity`() {
        // 320×320 纯绿输入 → 无插值混色，G 通道恒定
        val side = U2NetPreprocessor.INPUT_SIDE
        val rgba = ByteArray(side * side * 4)
        for (i in 0 until side * side) {
            rgba[i * 4] = 0
            rgba[i * 4 + 1] = 255.toByte()
            rgba[i * 4 + 2] = 0
            rgba[i * 4 + 3] = 255.toByte()
        }
        val out = U2NetPreprocessor.preprocess(rgba, side, side)
        val area = side * side
        val expectG = (1f - 0.456f) / 0.224f
        val expectR = (0f - 0.485f) / 0.229f
        for (i in 0 until area) {
            assertEquals(expectG, out[area + i], 1e-4f)
            assertEquals(expectR, out[i], 1e-4f)
        }
    }
}
