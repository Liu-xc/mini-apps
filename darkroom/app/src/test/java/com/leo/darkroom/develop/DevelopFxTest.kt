package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-008：过程动效纯函数——确定性、包络归零、SWEEP 对位。 */
class DevelopFxTest {

    @Test
    fun `envelope is zero before latent and after 85 percent`() {
        assertEquals(0f, DevelopFx.activityEnvelope(0f))
        assertEquals(0f, DevelopFx.activityEnvelope(0.08f))
        assertEquals(0f, DevelopFx.activityEnvelope(0.85f))
        assertEquals(0f, DevelopFx.activityEnvelope(1f))
        assertTrue("浮现段应接近满强度", DevelopFx.activityEnvelope(0.40f) > 0.95f)
    }

    @Test
    fun `bubbles are deterministic for same input`() {
        for (i in 0 until DevelopFx.BUBBLE_COUNT) {
            for (p in listOf(0f, 0.17f, 0.33f, 0.55f, 0.7f, 0.84f)) {
                val a = DevelopFx.bubbleAt(i, p)
                val b = DevelopFx.bubbleAt(i, p)
                assertEquals(a.x, b.x)
                assertEquals(a.y, b.y)
                assertEquals(a.radius, b.radius)
                assertEquals(a.alpha, b.alpha)
            }
        }
    }

    @Test
    fun `bubbles vanish after 85 percent`() {
        for (i in 0 until DevelopFx.BUBBLE_COUNT) {
            for (p in listOf(0.85f, 0.92f, 1f)) {
                assertEquals("i=$i p=$p", 0f, DevelopFx.bubbleAt(i, p).alpha)
            }
        }
    }

    @Test
    fun `bubble lifecycle rises from bottom and fades within one crossing`() {
        // 单泡相位推进时 y 单调下降（从底部 >1 升到顶部 <0）
        val p0 = DevelopFx.bubbleAt(3, 0.30f)
        val p1 = DevelopFx.bubbleAt(3, 0.42f)
        assertTrue("同一次穿越内 y 应随进度上移", p1.y < p0.y)
        // 生命周期半正弦：中段 alpha 高于两端
        val mid = DevelopFx.bubbleAt(7, 0.5f)
        assertTrue("alpha 应在 0..峰值内", mid.alpha >= 0f && mid.alpha <= 0.16f)
    }

    @Test
    fun `wet band aligns with sweep front formula`() {
        val band0 = DevelopFx.wetBandAt(DevelopMode.FILM, 0.3f, 0f)
        assertEquals(-0.22f, band0.position, 1e-4f)
        val band1 = DevelopFx.wetBandAt(DevelopMode.FILM, 0.3f, 1f)
        assertEquals(1.18f, band1.position, 1e-4f)
        assertTrue(band0.position < band1.position)
    }

    @Test
    fun `wet band is silent for polaroid and digital, any progress`() {
        // it-008 收尾修正：拍立得不叠加湿光层（观感像胶水扩散，见 wetBandAt 注释）
        for (p in listOf(0.05f, 0.3f, 0.5f, 0.7f, 0.85f)) {
            assertEquals(0f, DevelopFx.wetBandAt(DevelopMode.POLAROID, p, p).alpha)
            assertEquals(0f, DevelopFx.wetBandAt(DevelopMode.DIGITAL, p, p).alpha)
        }
        // 胶片在中段保持湿边强度
        assertTrue(DevelopFx.wetBandAt(DevelopMode.FILM, 0.5f, 0.5f).alpha > 0.05f)
    }

    @Test
    fun `chemical mode gets bubbles and digital does not`() {
        assertTrue(DevelopFx.bubblesFor(DevelopMode.POLAROID))
        assertTrue(DevelopFx.bubblesFor(DevelopMode.FILM))
        assertTrue(!DevelopFx.bubblesFor(DevelopMode.DIGITAL))
    }
}
