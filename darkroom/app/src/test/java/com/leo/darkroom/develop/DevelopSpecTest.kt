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
    fun `every mode shows a ghost early without a dead window`() {
        // it-009：8% 进度时三种模式都应有可察觉的淡影——前期不许回到纯白死区
        DevelopMode.entries.forEach { mode ->
            val v = DevelopSpec.visualAt(mode, 0.08f)
            assertTrue("$mode imageAlpha", v.imageAlpha > 0.28f)
            assertTrue("$mode reveal", v.reveal > 0.19f)
        }
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
        // 对角线 = 亮度×对比×影调（染料层定影归位=1），R/B 再叠加色温残留
        // （w=0.08 微暖是定影设计值）；影调层 = 分通道染料增益 × 高光压缩
        val v = DevelopSpec.visualAt(1f)
        val g = v.brightness * v.contrast
        assertEquals(g * (1f + 0.1f * v.warmth) * v.redGain * v.highlightGain, m[0], 1e-3f)
        assertEquals(g * v.greenGain * v.highlightGain, m[6], 1e-3f)
        assertEquals(g * (1f - 0.1f * v.warmth) * v.blueGain * v.highlightGain, m[12], 1e-3f)
        // 染料全部归位、窄动态生效（暗部被抬起）
        assertEquals(1f, v.redGain, 1e-6f)
        assertEquals(1f, v.greenGain, 1e-6f)
        assertEquals(1f, v.blueGain, 1e-6f)
        assertTrue(v.shadowLift > 0.01f)
        assertTrue(v.highlightGain < 1f)
    }

    @Test
    fun `dye layers develop in sequence and settle at the end`() {
        // 中途：青层先压红 → 画面偏青；品红层压绿、黄层压蓝稍晚
        val mid = DevelopSpec.visualAt(0.40f)
        assertTrue(mid.redGain < 1f)
        assertTrue(mid.greenGain >= mid.redGain)
        // 起点与终点染料均归位（不给首帧/末帧色偏）
        assertEquals(1f, DevelopSpec.visualAt(0f).redGain, 1e-6f)
        assertEquals(1f, DevelopSpec.visualAt(1f).blueGain, 1e-6f)
        // 中途色温压向青冷，定影回正微暖
        assertTrue(DevelopSpec.visualAt(0.70f).warmth < 0f)
        assertTrue(DevelopSpec.visualAt(1f).warmth > 0f)
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

    // —— it-007 M3：模式曲线 ——

    @Test
    fun `every mode is deterministic and ends fully developed`() {
        DevelopMode.entries.forEach { mode ->
            val a = DevelopSpec.visualAt(mode, 0.42f)
            val b = DevelopSpec.visualAt(mode, 0.42f)
            assertEquals("$mode must be deterministic", a, b)

            val start = DevelopSpec.visualAt(mode, 0f)
            val end = DevelopSpec.visualAt(mode, 1f)
            assertEquals("$mode end reveal", 1f, end.reveal, 1e-6f)
            assertEquals("$mode end alpha", 1f, end.imageAlpha, 1e-5f)
            assertEquals("$mode end saturation", 1f, end.saturation, 1e-5f)
            assertTrue("$mode starts dimmer", start.brightness < end.brightness)
            assertTrue("$mode starts blurrier", start.blurFraction > end.blurFraction)
        }
    }

    @Test
    fun `film starts as a negative and flips to a positive`() {
        val start = DevelopSpec.visualAt(DevelopMode.FILM, 0f)
        assertTrue("fully negative at start", start.invert >= 0.99f)
        assertEquals("positive by the end", 0f, DevelopSpec.visualAt(DevelopMode.FILM, 1f).invert, 1e-6f)

        val m = DevelopSpec.colorMatrix(start)
        assertTrue("negative inverts the channel gain", m[0] < 0f)
        assertTrue("negative pushes the offset near white", m[4] > 200f)
    }

    @Test
    fun `digital stays neutral with no film tone roll-off`() {
        val v = DevelopSpec.visualAt(DevelopMode.DIGITAL, 1f)
        assertEquals(1f, v.redGain, 1e-6f)
        assertEquals(1f, v.blueGain, 1e-6f)
        assertEquals(0f, v.shadowLift, 1e-6f)
        assertEquals(1f, v.highlightGain, 1e-6f)
        assertEquals(0f, v.warmth, 1e-6f)
        assertEquals(0f, v.invert, 1e-6f)
    }

    @Test
    fun `modes read differently at the same progress`() {
        val mid = 0.5f
        val polaroid = DevelopSpec.visualAt(DevelopMode.POLAROID, mid)
        val digital = DevelopSpec.visualAt(DevelopMode.DIGITAL, mid)
        val film = DevelopSpec.visualAt(DevelopMode.FILM, mid)
        // 颗粒：胶片最重，数码最轻
        assertTrue("film grain > polaroid", film.grain > polaroid.grain)
        assertTrue("polaroid grain > digital", polaroid.grain > digital.grain)
        // 暗角：数码几乎不给
        assertTrue("polaroid vignette > digital", polaroid.vignette > digital.vignette)
        // 中途只有胶片还在反相（p=0.5 时已翻掉约 2/3，仍是部分负片）
        assertTrue("film still part-negative", film.invert > 0.1f)
        assertTrue("film is further from positive than the others", film.invert > digital.invert)
        assertEquals(0f, digital.invert, 1e-6f)
        assertEquals(0f, polaroid.invert, 1e-6f)
    }
}
