package com.leo.lottery

import com.leo.lottery.core.Game
import com.leo.lottery.core.Generator
import com.leo.lottery.core.combination
import com.leo.lottery.core.comboCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {

    private val fp = "0123456789abcdef"

    @Test
    fun `same seed and take always same numbers`() {
        val a = Generator.generate(fp, Game.SSQ, 6, 1, 1)
        val b = Generator.generate(fp, Game.SSQ, 6, 1, 1)
        assertEquals(a, b)
    }

    @Test
    fun `different take gives different numbers`() {
        val a = Generator.generate(fp, Game.SSQ, 6, 1, 1)
        val b = Generator.generate(fp, Game.SSQ, 6, 1, 2)
        assertNotEquals(a, b)
    }

    @Test
    fun `different fingerprint gives different numbers`() {
        val a = Generator.generate(fp, Game.DLT, 5, 2, 1)
        val b = Generator.generate("fedcba9876543210", Game.DLT, 5, 2, 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `ssq single is sorted unique in range`() {
        val n = Generator.generate(fp, Game.SSQ, 6, 1, 1)
        assertEquals(6, n.zone1.size)
        assertEquals(1, n.zone2.size)
        assertEquals(n.zone1, n.zone1.sorted().distinct())
        assertTrue(n.zone1.all { it in 1..33 })
        assertTrue(n.zone2.all { it in 1..16 })
    }

    @Test
    fun `dlt combo respects zone sizes`() {
        val n = Generator.generate(fp, Game.DLT, 10, 2, 3)
        assertEquals(10, n.zone1.size)
        assertEquals(2, n.zone2.size)
        assertEquals(n.zone1, n.zone1.sorted().distinct())
        assertTrue(n.zone1.all { it in 1..35 })
        assertTrue(n.zone2.all { it in 1..12 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zone2 size beyond base rejected`() {
        Generator.generate(fp, Game.SSQ, 6, 2, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `combo below base rejected`() {
        Generator.generate(fp, Game.SSQ, 5, 1, 1)
    }

    @Test
    fun `combo counts match official math`() {
        assertEquals(1L, Game.SSQ.comboCount(6, 1))
        assertEquals(7L, Game.SSQ.comboCount(7, 1))
        assertEquals(924L, Game.SSQ.comboCount(12, 1))
        assertEquals(1L, Game.DLT.comboCount(5, 2))
        assertEquals(252L, Game.DLT.comboCount(10, 2))
        assertEquals(5005L, combination(15, 6))
    }
}
