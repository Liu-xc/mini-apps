package com.leo.darkroom.export

import org.junit.Assert.assertTrue
import org.junit.Test

class SfxSynthTest {

    @Test
    fun `track duration matches plan`() {
        val track = SfxSynth.buildDevelopTrack(10_000L, 8_500L)
        // 44100Hz × 10s = 441000 samples → 毫秒取整
        assertTrue(track.durationMs in 9_990..10_010)
    }

    @Test
    fun `paper noise loudest at start and quieter later`() {
        val track = SfxSynth.buildDevelopTrack(10_000L, 8_500L)
        val head = SfxSynth.rms(track, 0, 300)
        val tail = SfxSynth.rms(track, 5_000, 5_300)
        assertTrue("head=$head tail=$tail", head > tail)
    }

    @Test
    fun `tick lands at develop end`() {
        val track = SfxSynth.buildDevelopTrack(10_000L, 8_500L)
        val aroundTick = SfxSynth.rms(track, 8_500, 8_650)
        val beforeTick = SfxSynth.rms(track, 8_200, 8_350)
        assertTrue("tick=$aroundTick before=$beforeTick", aroundTick > beforeTick * 1.3)
    }

    @Test
    fun `deterministic for same seed`() {
        val a = SfxSynth.buildDevelopTrack(2_000L, 1_500L, seed = 42L)
        val b = SfxSynth.buildDevelopTrack(2_000L, 1_500L, seed = 42L)
        assertTrue(a.pcm.contentEquals(b.pcm))
    }

    @Test
    fun `no sample clipping beyond bounds`() {
        val track = SfxSynth.buildDevelopTrack(10_000L, 8_500L)
        assertTrue(track.pcm.all { it in -32768..32767 })
    }
}
