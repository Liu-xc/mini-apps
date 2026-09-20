package com.leo.libs.cutout

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.ZERO

/**
 * 真模型自测（it-016 阶段 A 验收核心）：桌面 JVM + 真实 u2netp.onnx 完整跑通
 * 预处理 → ONNX 推理 → mask→alpha 管线。无 Android、无模拟器依赖。
 */
class RealModelCutoutTest {

    @Test
    fun `synthetic disk on white background is segmented end to end`() = runBlocking {
        val modelBytes = javaClass.classLoader.getResourceAsStream("u2netp.onnx")!!
            .use { it.readBytes() }
        val engine = OnnxCutoutEngine({ modelBytes }, idleTimeout = ZERO)

        // 640×640 白底 + 中央实心深蓝圆盘 r=220
        val w = 640
        val h = 640
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val inDisk = (x - 320) * (x - 320) + (y - 320) * (y - 320) <= 220 * 220
                rgba[i] = (if (inDisk) 30 else 255).toByte()
                rgba[i + 1] = (if (inDisk) 60 else 255).toByte()
                rgba[i + 2] = (if (inDisk) 120 else 255).toByte()
                rgba[i + 3] = 255.toByte()
            }
        }

        val t0 = System.currentTimeMillis()
        val out = engine.cutout(rgba, w, h)
        val ms = System.currentTimeMillis() - t0

        fun alpha(x: Int, y: Int) = out[(y * w + x) * 4 + 3].toInt() and 0xFF
        val center = alpha(320, 320)
        val corner = alpha(4, 4)
        println("real u2netp cutout took ${ms}ms; centerAlpha=$center cornerAlpha=$corner")

        assertTrue("center alpha=$center too low (subject not kept)", center > 150)
        assertTrue("corner alpha=$corner too high (background not removed)", corner < 100)
        // RGB 通道原样保留
        assertEquals(30.toByte(), out[(320 * w + 320) * 4])
        assertEquals(255.toByte(), out[(4 * w + 4) * 4])
    }
}
