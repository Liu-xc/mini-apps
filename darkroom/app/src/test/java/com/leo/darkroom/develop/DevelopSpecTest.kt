package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopSpecTest {

    @Test
    fun `phase boundaries match spec curve`() {
        assertEquals(DevelopPhase.LATENT, DevelopSpec.phaseAt(0f))
        assertEquals(DevelopPhase.LATENT, DevelopSpec.phaseAt(0.149f))
        assertEquals(DevelopPhase.EMERGING, DevelopSpec.phaseAt(0.15f))
        assertEquals(DevelopPhase.EMERGING, DevelopSpec.phaseAt(0.699f))
        assertEquals(DevelopPhase.FIXED, DevelopSpec.phaseAt(0.70f))
        assertEquals(DevelopPhase.FIXED, DevelopSpec.phaseAt(1f))
    }

    @Test
    fun `visualAt is deterministic and monotone in key params`() {
        val a = DevelopSpec.visualAt(0.42f)
        val b = DevelopSpec.visualAt(0.42f)
        assertEquals(a, b)

        val start = DevelopSpec.visualAt(0f)
        val mid = DevelopSpec.visualAt(0.5f)
        val end = DevelopSpec.visualAt(1f)

        // 浮现方向：饱和度上升、模糊下降、显影半径扩大
        assertTrue(start.saturation < mid.saturation)
        assertTrue(mid.saturation < end.saturation)
        assertTrue(start.blurFraction > end.blurFraction)
        assertTrue(start.reveal < mid.reveal)
        assertEquals(1f, end.reveal, 1e-6f)
        // 潜影偏暗 → 定影归一
        assertTrue(start.brightness < end.brightness)
    }

    @Test
    fun `progress out of range is coerced`() {
        val below = DevelopSpec.visualAt(-3f)
        val atZero = DevelopSpec.visualAt(0f)
        assertEquals(atZero, below)
        val above = DevelopSpec.visualAt(2f)
        val atOne = DevelopSpec.visualAt(1f)
        assertEquals(atOne, above)
    }

    @Test
    fun `colorMatrix is 20 floats with identity-ish end state`() {
        val m = DevelopSpec.colorMatrix(DevelopSpec.visualAt(1f))
        assertEquals(20, m.size)
        // 定影终态：饱和度=1（饱和矩阵=单位阵），亮度≈1.0、对比≈1.1、色温≈0
        // 行 3（alpha 行）必须是 [0,0,0,1,0]
        assertEquals(0f, m[15], 1e-6f)
        assertEquals(0f, m[16], 1e-6f)
        assertEquals(0f, m[17], 1e-6f)
        assertEquals(1f, m[18], 1e-6f)
        assertEquals(0f, m[19], 1e-6f)
        // 对角线 = 亮度×对比，R/B 叠加色温残留（w=0.08 微暖是定影设计值）
        val v = DevelopSpec.visualAt(1f)
        val g = v.brightness * v.contrast
        assertEquals(g * (1f + 0.1f * v.warmth), m[0], 1e-3f)
        assertEquals(g, m[6], 1e-3f)
        assertEquals(g * (1f - 0.1f * v.warmth), m[12], 1e-3f)
    }

    @Test
    fun `concat multiplies 4x5 matrices in correct order`() {
        // b 是缩放（行 [s,0,0,0,0]…），a 是平移（行 [1,0,0,0,t]…）
        // concat(a,b) = a∘b：先缩放后平移 → 对角 s、偏移 t
        val scale = floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 2f, 0f, 0f, 0f,
            0f, 0f, 2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val translate = floatArrayOf(
            1f, 0f, 0f, 0f, 10f,
            0f, 1f, 0f, 0f, 10f,
            0f, 0f, 1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        )
        val out = DevelopSpec.concat(translate, scale)
        assertEquals(2f, out[0], 1e-6f)
        assertEquals(10f, out[4], 1e-6f) // 偏移 = a.offset×1 + b.offset×s... 见下断言
        // 精确语义：out = a·b，偏移列 = a_diag*b_offset + a_offset = 1×0 + 10 = 10
        assertEquals(10f, out[4], 1e-6f)
    }

    @Test
    fun `smoothstep clamps outside edges`() {
        assertEquals(0f, DevelopSpec.smoothstep(0.15f, 0.7f, -1f), 1e-6f)
        assertEquals(1f, DevelopSpec.smoothstep(0.15f, 0.7f, 5f), 1e-6f)
        assertEquals(0.5f, DevelopSpec.smoothstep(0f, 1f, 0.5f), 1e-6f)
    }
}
