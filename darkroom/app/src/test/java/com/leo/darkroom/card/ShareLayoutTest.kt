package com.leo.darkroom.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLayoutTest {
    @Test
    fun allFormatsKeepThePolaroidCardInsideTheFrame() {
        ShareFormat.entries.forEach { format ->
            val layout = ShareLayout.solve(format.width.toFloat(), format.height.toFloat(), format)
            assertTrue(layout.card.left >= 0f)
            assertTrue(layout.card.top >= 0f)
            assertTrue(layout.card.right <= layout.width)
            assertTrue(layout.card.bottom <= layout.height)
            assertEquals(layout.width / 2f, layout.card.centerX, 0.01f)
        }
    }

    @Test
    fun storyTypographyAndCardStayWithinPlatformSafeArea() {
        val format = ShareFormat.STORY
        val layout = ShareLayout.solve(format.width.toFloat(), format.height.toFloat(), format)
        assertTrue(layout.wordmark.top >= layout.safeTop)
        assertTrue(layout.caption.bottom <= layout.safeBottom)
        assertTrue(layout.wordmark.bottom < layout.card.top)
        assertTrue(layout.card.bottom < layout.caption.top)
        assertTrue(layout.card.top >= layout.safeTop)
        assertTrue(layout.card.bottom <= layout.safeBottom)
    }
}
