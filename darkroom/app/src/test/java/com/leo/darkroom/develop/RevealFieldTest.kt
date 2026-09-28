package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealFieldTest {

    private fun revealed(kind: RevealKind, amount: Float, seed: Int = 19): Int =
        RevealField.alphaMask(kind, 96, 96, amount, seed).count { (it ushr 24) < 128 }

    @Test
    fun `same kind seed and progress produce identical mask`() {
        RevealKind.entries.forEach { kind ->
            val first = RevealField.alphaMask(kind, 64, 64, 0.43f, seed = 812)
            val second = RevealField.alphaMask(kind, 64, 64, 0.43f, seed = 812)
            assertTrue("$kind must be deterministic", first.contentEquals(second))
        }
    }

    @Test
    fun `coverage grows as every kind develops`() {
        RevealKind.entries.forEach { kind ->
            val early = revealed(kind, 0.10f)
            val mid = revealed(kind, 0.35f)
            val late = revealed(kind, 0.70f)
            assertTrue("$kind 0.10→0.35 grew", early < mid)
            assertTrue("$kind 0.35→0.70 grew", mid < late)
        }
    }

    @Test
    fun `fully developed mask is cleared`() {
        RevealKind.entries.forEach { kind ->
            val mask = RevealField.alphaMask(kind, 32, 32, 1f, seed = 5)
            assertTrue("$kind must clear at reveal=1", mask.all { it == 0 })
            assertEquals(0, RevealField.alphaAt(kind, 0.5f, 0.5f, 1f, seed = 5))
        }
    }

    @Test
    fun `seed changes the organic edge but not the progress order`() {
        RevealKind.entries.forEach { kind ->
            val first = RevealField.alphaMask(kind, 48, 48, 0.52f, seed = 4)
            val second = RevealField.alphaMask(kind, 48, 48, 0.52f, seed = 5)
            assertFalse("$kind must vary by photo seed", first.contentEquals(second))
        }
    }

    @Test
    fun `chemical front pushes away from the bottom-left inlet`() {
        val seed = 77
        val amount = 0.35f
        // 入口附近先显影（不透明度低），远端仍是乳剂底
        val nearInlet = RevealField.alphaAt(RevealKind.CHEMICAL, 0.12f, 0.90f, amount, seed)
        val farCorner = RevealField.alphaAt(RevealKind.CHEMICAL, 0.96f, 0.04f, amount, seed)
        assertTrue("inlet should be further along", nearInlet < farCorner)
    }

    @Test
    fun `sweep runs left to right like a wash train`() {
        val seed = 77
        val amount = 0.5f
        val left = RevealField.alphaAt(RevealKind.SWEEP, 0.10f, 0.5f, amount, seed)
        val right = RevealField.alphaAt(RevealKind.SWEEP, 0.90f, 0.5f, amount, seed)
        assertTrue("left must lead the wash", left < right)
        assertEquals(0, left)
    }

    @Test
    fun `block reveal lights cells instead of a continuous front`() {
        val amount = 0.5f
        // 同一行里应同时存在已点亮与未点亮的格子（离散格点语义）
        val row = (0 until 14).map { i ->
            RevealField.alphaAt(RevealKind.BLOCKS, (i + 0.5f) / 14f, 0.5f, amount, seed = 9)
        }
        assertTrue("some cells lit", row.any { it < 64 })
        assertTrue("some cells dark", row.any { it > 192 })
    }

    @Test
    fun `bucket stays inside the cached range for every kind`() {
        RevealKind.entries.forEach { kind ->
            assertEquals(0, RevealField.bucket(kind, -1f))
            assertEquals(720, RevealField.bucket(kind, 2f))
            assertTrue(RevealField.bucket(kind, 0.5f) in 1..719)
        }
    }
}
