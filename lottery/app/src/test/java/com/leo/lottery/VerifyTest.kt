package com.leo.lottery

import com.leo.lottery.core.DrawResult
import com.leo.lottery.core.Game
import com.leo.lottery.core.PrizeLevel
import com.leo.lottery.core.Ticket
import com.leo.lottery.core.Verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyTest {

    private fun ssqResult(z1: List<Int>, z2: List<Int>) =
        DrawResult(Game.SSQ, "2026001", "2026-01-01", z1, z2)

    private fun ssqTicket(z1: List<Int>, z2: List<Int>) =
        Ticket("t", Game.SSQ, 0L, "fp", 1, z1, z2, "2026001")

    private fun dltResult(z1: List<Int>, z2: List<Int>) =
        DrawResult(Game.DLT, "2026001", "2026-01-05", z1, z2)

    private fun dltTicket(z1: List<Int>, z2: List<Int>) =
        Ticket("t", Game.DLT, 0L, "fp", 1, z1, z2, "2026001")

    @Test
    fun `ssq prize table every level`() {
        val win = listOf(1, 2, 3, 4, 5, 6)
        val result = ssqResult(win, listOf(16))
        fun prize(z1: List<Int>, z2: List<Int>) =
            Verify.verify(ssqTicket(z1, z2), result).best

        assertEquals(PrizeLevel.L1, prize(win, listOf(16)))
        assertEquals(PrizeLevel.L2, prize(win, listOf(15)))
        assertEquals(PrizeLevel.L3, prize(listOf(1, 2, 3, 4, 5, 7), listOf(16)))
        assertEquals(PrizeLevel.L4, prize(listOf(1, 2, 3, 4, 5, 7), listOf(15)))
        assertEquals(PrizeLevel.L4, prize(listOf(1, 2, 3, 4, 8, 9), listOf(16)))
        assertEquals(PrizeLevel.L5, prize(listOf(1, 2, 3, 4, 8, 9), listOf(15)))
        assertEquals(PrizeLevel.L5, prize(listOf(1, 2, 3, 10, 11, 12), listOf(16)))
        assertEquals(PrizeLevel.L6, prize(listOf(1, 2, 10, 11, 12, 13), listOf(16)))
        assertEquals(PrizeLevel.L6, prize(listOf(9, 10, 11, 12, 13, 14), listOf(16)))
        assertEquals(PrizeLevel.L6, prize(listOf(9, 10, 11, 12, 13, 14), listOf(16)))
        assertNull(prize(listOf(9, 10, 11, 12, 13, 14), listOf(15)))
        assertNull(prize(listOf(1, 2, 3, 10, 11, 12), listOf(15)))
    }

    @Test
    fun `ssq no win cases`() {
        val result = ssqResult(listOf(1, 2, 3, 4, 5, 6), listOf(16))
        assertNull(Verify.verify(ssqTicket(listOf(7, 8, 9, 10, 11, 12), listOf(15)), result).best)
        assertNull(Verify.verify(ssqTicket(listOf(1, 2, 3, 7, 8, 9), listOf(15)), result).best)
    }

    @Test
    fun `dlt prize table every level`() {
        val result = dltResult(listOf(1, 2, 3, 4, 5), listOf(6, 7))
        fun prize(z1: List<Int>, z2: List<Int>) =
            Verify.verify(dltTicket(z1, z2), result).best

        assertEquals(PrizeLevel.L1, prize(listOf(1, 2, 3, 4, 5), listOf(6, 7)))
        assertEquals(PrizeLevel.L2, prize(listOf(1, 2, 3, 4, 5), listOf(6, 8)))
        assertEquals(PrizeLevel.L3, prize(listOf(1, 2, 3, 4, 5), listOf(8, 9)))
        assertEquals(PrizeLevel.L4, prize(listOf(1, 2, 3, 4, 9), listOf(6, 7)))
        assertEquals(PrizeLevel.L5, prize(listOf(1, 2, 3, 4, 9), listOf(6, 8)))
        assertEquals(PrizeLevel.L5, prize(listOf(1, 2, 3, 9, 10), listOf(6, 7)))
        assertEquals(PrizeLevel.L6, prize(listOf(1, 2, 3, 4, 9), listOf(8, 9)))
        assertEquals(PrizeLevel.L7, prize(listOf(1, 2, 3, 9, 10), listOf(6, 8)))
        assertEquals(PrizeLevel.L7, prize(listOf(1, 2, 9, 10, 11), listOf(6, 7)))
        assertEquals(PrizeLevel.L8, prize(listOf(1, 2, 3, 9, 10), listOf(8, 9)))
        assertEquals(PrizeLevel.L8, prize(listOf(1, 9, 10, 11, 12), listOf(6, 7)))
        assertEquals(PrizeLevel.L8, prize(listOf(1, 2, 9, 10, 11), listOf(6, 8)))
        assertEquals(PrizeLevel.L9, prize(listOf(8, 9, 10, 11, 12), listOf(6, 7)))
        assertNull(prize(listOf(8, 9, 10, 11, 12), listOf(8, 9)))
        assertNull(prize(listOf(1, 9, 10, 11, 12), listOf(8, 9)))
    }

    @Test
    fun `ssq combo aggregates prize counts`() {
        val result = ssqResult(listOf(1, 2, 3, 4, 5, 6), listOf(16))
        val ticket = ssqTicket(listOf(1, 2, 3, 4, 5, 6, 7), listOf(16))
        val verdict = Verify.verify(ticket, result)
        assertEquals(7, verdict.combos)
        assertEquals(7, verdict.totalWinning)
        assertEquals(1, verdict.byLevel[PrizeLevel.L1])
        assertEquals(6, verdict.byLevel[PrizeLevel.L3])
        assertEquals(PrizeLevel.L1, verdict.best)
        assertTrue(verdict.won)
        assertEquals(6, verdict.setHits.zone1)
        assertEquals(1, verdict.setHits.zone2)
    }

    @Test
    fun `dlt combo aggregates prize counts`() {
        val result = dltResult(listOf(1, 2, 3, 4, 5), listOf(6, 7))
        val ticket = dltTicket(listOf(1, 2, 3, 4, 5, 6), listOf(6, 7))
        val verdict = Verify.verify(ticket, result)
        assertEquals(6, verdict.combos)
        assertEquals(6, verdict.totalWinning)
        assertEquals(1, verdict.byLevel[PrizeLevel.L1])
        assertEquals(5, verdict.byLevel[PrizeLevel.L4])
        assertEquals(PrizeLevel.L1, verdict.best)
    }

    @Test
    fun `single combo no win marked lost`() {
        val result = ssqResult(listOf(1, 2, 3, 4, 5, 6), listOf(16))
        val verdict = Verify.verify(ssqTicket(listOf(7, 8, 9, 10, 11, 12), listOf(15)), result)
        assertFalse(verdict.won)
        assertEquals(1, verdict.combos)
        assertEquals(0, verdict.totalWinning)
        assertNull(verdict.best)
    }
}
