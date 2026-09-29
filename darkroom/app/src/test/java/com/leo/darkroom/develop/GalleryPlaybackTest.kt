package com.leo.darkroom.develop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-011 US-18：沉浸相册播放簿记——本次启动内不重复、手动重播。 */
class GalleryPlaybackTest {

    @Test
    fun `first sight animates and marking makes it stick`() {
        val pb = GalleryPlayback()
        assertTrue(pb.shouldAnimate(7L))
        pb.markPlayed(7L)
        assertFalse(pb.shouldAnimate(7L))
        // 其它图片不受影响
        assertTrue(pb.shouldAnimate(8L))
    }

    @Test
    fun `replay removes exactly the requested id`() {
        val pb = GalleryPlayback()
        pb.markPlayed(1L)
        pb.markPlayed(2L)
        pb.replay(1L)
        assertTrue(pb.shouldAnimate(1L))
        assertFalse(pb.shouldAnimate(2L))
    }

    @Test
    fun `snapshot reflects current played set`() {
        val pb = GalleryPlayback()
        pb.markPlayed(3L)
        assertEquals(setOf(3L), pb.snapshot())
        pb.replay(3L)
        assertEquals(emptySet<Long>(), pb.snapshot())
    }
}
