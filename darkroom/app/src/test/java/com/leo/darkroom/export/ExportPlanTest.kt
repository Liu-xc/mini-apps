package com.leo.darkroom.export

import com.leo.darkroom.develop.DevelopMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanTest {

    @Test
    fun `timeline math for standard speed`() {
        // 标准 8s 显影：lead 0.5s + 8s + hold 1.5s = 10s
        val plan = ExportPlan(developMs = 8_000L)
        assertEquals(10_000L, plan.totalMs)
        assertEquals(300, plan.frameCount)
    }

    @Test
    fun `lead frames are blank paper`() {
        val plan = ExportPlan(developMs = 8_000L)
        assertEquals(0f, plan.frameAt(0).developProgress)
        assertEquals(0f, plan.frameAt(14).developProgress) // 466ms < 500ms
        assertEquals(0f, plan.frameAt(15).developProgress) // 恰 500ms 仍属 lead
        assertTrue(plan.frameAt(16).developProgress > 0f) // 533ms 起显影
    }

    @Test
    fun `develop ramps linearly through middle`() {
        val plan = ExportPlan(developMs = 8_000L)
        // 中点时刻 = 500 + 4000 = 4500ms → frame 135
        val mid = plan.frameAt(135)
        assertEquals(4_500L, mid.timeMs)
        assertEquals(0.5f, mid.developProgress, 0.01f)
    }

    @Test
    fun `hold frames stay fully developed`() {
        val plan = ExportPlan(developMs = 8_000L)
        val last = plan.frameAt(plan.frameCount - 1)
        assertEquals(1f, last.developProgress)
        // 帧时间落在 fps 网格：299×1000/30 = 9966ms（整除截断）
        assertEquals(9_966L, last.timeMs)
    }

    @Test
    fun `frame index out of range clamps to last`() {
        val plan = ExportPlan(developMs = 4_000L)
        assertEquals(plan.frameCount - 1, plan.frameAt(99_999).index)
        assertEquals(0, plan.frameAt(-5).index)
    }

    @Test
    fun `slow and fast speeds keep lead and hold`() {
        val slow = ExportPlan(developMs = 12_000L)
        assertEquals(14_000L, slow.totalMs)
        assertEquals(420, slow.frameCount)
        val fast = ExportPlan(developMs = 4_000L)
        assertEquals(6_000L, fast.totalMs)
    }

    @Test
    fun `film mode leaves a longer lead for the winding animation`() {
        assertEquals(500L, DevelopMode.POLAROID.leadMs)
        assertEquals(500L, DevelopMode.DIGITAL.leadMs)
        assertEquals(800L, DevelopMode.FILM.leadMs)
        val plan = ExportPlan(developMs = 8_000L, leadMs = DevelopMode.FILM.leadMs)
        assertEquals(10_300L, plan.totalMs)
        // 800ms 内仍是起手空白
        assertEquals(0f, plan.frameAt(23).developProgress)
        assertTrue(plan.frameAt(25).developProgress > 0f)
    }
}
