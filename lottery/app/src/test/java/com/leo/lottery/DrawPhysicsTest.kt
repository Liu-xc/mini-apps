package com.leo.lottery

import com.leo.lottery.core.physics.DrawPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class DrawPhysicsTest {
    @Test fun settledPopulationHasDistinctContactsAndStaysInside() {
        val w = DrawPhysics(35, "settle")
        assertTrue(w.balls.map { it.y }.average() > .35)
        assertBounds(w)
        for (i in w.balls.indices) for (j in i+1 until w.balls.size) {
            val a=w.balls[i]; val b=w.balls[j]
            val d=sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)+(a.z-b.z)*(a.z-b.z))
            assertTrue("contact $i/$j distance=$d", d > w.radius*1.90f)
        }
    }
    @Test fun longRunIsFiniteThenSettlesAfterFanStops() {
        val w=DrawPhysics(35,"long")
        repeat(120*90) { w.step(1f/120f,1f); if(it%120==0) assertBounds(w) }
        val mixedY=w.balls.map { it.y }.average()
        repeat(120*8) { w.step(1f/120f,0f) }
        assertBounds(w)
        assertTrue(w.balls.map { it.y }.average() > mixedY+.15)
        assertTrue(w.balls.map { it.vx*it.vx+it.vy*it.vy+it.vz*it.vz }.average() < .08)
    }
    @Test fun seedAndFixedStepsReproduceExactly() {
        val a=DrawPhysics(33,"same"); val b=DrawPhysics(33,"same")
        repeat(500) { a.step(1f/120f,.9f); b.step(1f/120f,.9f) }
        a.balls.zip(b.balls).forEach { (x,y) -> assertEquals(x.x,y.x,0f); assertEquals(x.y,y.y,0f); assertEquals(x.z,y.z,0f) }
    }
    @Test fun captureStartsContinuouslyAndRemovesExactlyOneBall() {
        val w=DrawPhysics(16,"capture")
        repeat(120) { w.step(1f/120f,1f) }
        val b=w.balls[6]; val x=b.x; val y=b.y
        w.capture(7)
        assertEquals(x,b.x,0f); assertEquals(y,b.y,0f)
        repeat(80) { w.step(1f/120f,1f) }
        assertFalse(b.active); assertEquals(-1.02f,b.y,.001f)
        assertEquals(15,w.balls.count { it.active })
    }
    private fun assertBounds(w: DrawPhysics) {
        w.balls.filter { it.active && !it.captured }.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite())
            assertTrue(sqrt(it.x*it.x+it.y*it.y+it.z*it.z) <= 1f-w.radius+.002f)
        }
    }
}
