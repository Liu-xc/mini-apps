package com.leo.darkroom.develop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemicalDiffusionTest {
    @Test
    fun samePhotoSeedAndProgressProduceSameMask() {
        val first = ChemicalDiffusion.alphaMask(64, 64, 0.43f, seed = 812)
        val second = ChemicalDiffusion.alphaMask(64, 64, 0.43f, seed = 812)
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun revealCoverageGrowsAsTheEmulsionDevelops() {
        fun revealed(amount: Float): Int = ChemicalDiffusion.alphaMask(96, 96, amount, 19)
            .count { (it ushr 24) < 128 }

        assertTrue(revealed(0.35f) > revealed(0.10f))
        assertTrue(revealed(0.70f) > revealed(0.35f))
    }

    @Test
    fun photoSeedChangesTheOrganicEdgeButNotItsProgress() {
        val first = ChemicalDiffusion.alphaMask(48, 48, 0.52f, seed = 4)
        val second = ChemicalDiffusion.alphaMask(48, 48, 0.52f, seed = 5)
        assertFalse(first.contentEquals(second))
    }
}
