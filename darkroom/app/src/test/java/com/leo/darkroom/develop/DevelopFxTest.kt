package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * it-008 过程动效纯函数。二次修正后只剩湿光带（仅胶片）与包络——
 * 气泡已删（真实显影看不见泡，Leo 实机反馈）。
 */
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
}
