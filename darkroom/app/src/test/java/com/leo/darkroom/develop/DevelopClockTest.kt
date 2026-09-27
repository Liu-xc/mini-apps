package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopClockTest {

    @Test
    fun `tick accumulates and clamps at full`() {
        val clock = DevelopClock(1_000)
        clock.tick(400)
        assertEquals(0.4f, clock.progress, 1e-6f)
        clock.tick(400)
        assertEquals(0.8f, clock.progress, 1e-6f)
        clock.tick(400) // 超出截断
        assertEquals(1f, clock.progress, 1e-6f)
        assertTrue(clock.isFixed)
    }

    @Test
    fun `negative or zero dt is ignored`() {
        val clock = DevelopClock(1_000)
        clock.tick(0)
        clock.tick(-50)
        assertEquals(0f, clock.progress, 1e-6f)
        assertFalse(clock.isFixed)
    }

    @Test
    fun `boost advances by fraction of total duration`() {
        val clock = DevelopClock(1_000)
        clock.boost(0.10f)
        assertEquals(0.10f, clock.progress, 1e-6f)
        clock.boost(5f) // 超额只到 1
        assertEquals(1f, clock.progress, 1e-6f)
    }

    @Test
    fun `seek positions deterministically both directions`() {
        val clock = DevelopClock(8_000)
        clock.seek(0.55f)
        assertEquals(0.55f, clock.progress, 1e-6f)
        clock.seek(0.2f) // 药水条倒放
        assertEquals(0.2f, clock.progress, 1e-6f)
        clock.seek(9f)
        assertEquals(1f, clock.progress, 1e-6f)
    }

    @Test
    fun `reset returns to zero`() {
        val clock = DevelopClock(8_000)
        clock.tick(3_000)
        clock.reset()
        assertEquals(0f, clock.progress, 1e-6f)
    }

    @Test
    fun `speed presets parse with fallback`() {
        assertEquals(DevelopSpeed.SLOW, DevelopSpeed.fromOrDefault("SLOW"))
        assertEquals(DevelopSpeed.STANDARD, DevelopSpeed.fromOrDefault(null))
        assertEquals(DevelopSpeed.STANDARD, DevelopSpeed.fromOrDefault("nope"))
        assertEquals(12_000L, DevelopSpeed.SLOW.durationMs)
        assertEquals(4_000L, DevelopSpeed.FAST.durationMs)
    }
}
