package com.leo.lottery

import com.leo.lottery.core.physics.*
import org.junit.Assert.*
import org.junit.Test

class DrawSceneTest {
    private fun scene()=DrawScene(listOf(2,5,6,8,23,33),listOf(11),33,16,"scene-test")
    @Test fun skippedAndPlayedResultsContainTheSameSevenIdentities() {
        val played=scene(); played.advance(played.endAt)
        val skipped=scene(); skipped.finish()
        assertEquals(7,played.drawn); assertEquals(7,skipped.drawn)
        val p=played.visuals.filter { it.visible && ((!it.special && it.number in played.main) || (it.special && it.number in played.special)) }
        val s=skipped.visuals.filter { it.visible && ((!it.special && it.number in skipped.main) || (it.special && it.number in skipped.special)) }
        p.zip(s).forEach { (a,b) -> assertEquals(a.x,b.x,0f); assertEquals(a.y,b.y,0f) }
    }
    @Test fun arrivalOccursOnceAndMatchesTheActualRailStop() {
        val s=scene(); val arrival=DrawTiming.start(0,6)+DrawTiming.CAPTURE+DrawTiming.TRAVEL
        s.advance(arrival-1); assertEquals(0,s.drawn)
        s.advance(arrival); assertEquals(1,s.drawn)
        val b=s.visuals.first { !it.special && it.number==2 }
        assertEquals(s.slotX(0),b.x,.001f); assertEquals(s.railY(b.x),b.y,.001f)
        s.advance(arrival+450); assertEquals(1,s.drawn)
    }
    @Test fun outletAndPipeShareCoordinatesAndTravelHasNoLargeFrameJump() {
        val s=scene(); val start=DrawTiming.start(0,6)+DrawTiming.CAPTURE
        s.advance(start)
        val b=s.visuals.first { !it.special && it.number==2 }
        assertEquals(0f,b.x,.001f); assertEquals(-1.02f,b.y,.001f)
        var x=b.x; var y=b.y
        for(t in start+8..start+DrawTiming.TRAVEL step 8) {
            s.advance(t)
            assertTrue("jump at $t", kotlin.math.hypot(b.x-x,b.y-y)<.16f)
            x=b.x; y=b.y
        }
    }
    @Test fun mainBallsStayOnTheRailWhenSpecialMachineStarts() {
        val s=scene(); s.advance(DrawTiming.swap(6)+10)
        assertTrue(s.blue)
        assertEquals(6,s.visuals.count { it.visible && !it.special })
    }
}
