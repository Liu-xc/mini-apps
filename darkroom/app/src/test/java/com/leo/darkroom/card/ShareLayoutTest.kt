package com.leo.darkroom.card

import com.leo.darkroom.develop.DevelopMode
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

    @Test
    fun everyModeKeepsItsCardInsideEveryFrame() {
        DevelopMode.entries.forEach { mode ->
            ShareFormat.entries.forEach { format ->
                val layout = ShareLayout.solve(
                    format.width.toFloat(), format.height.toFloat(), format, mode,
                )
                assertTrue("$mode/$format left", layout.card.left >= 0f)
                assertTrue("$mode/$format top", layout.card.top >= 0f)
                assertTrue("$mode/$format right", layout.card.right <= layout.width)
                assertTrue("$mode/$format bottom", layout.card.bottom <= layout.height)
                assertEquals(layout.width / 2f, layout.card.centerX, 0.01f)
                assertEquals(
                    CardLayout.aspectOf(mode),
                    layout.card.height / layout.card.width,
                    1e-4f,
                )
            }
        }
    }

    @Test
    fun filmCardStaysClearOfStoryTypography() {
        val format = ShareFormat.STORY
        val layout = ShareLayout.solve(
            format.width.toFloat(), format.height.toFloat(), format, DevelopMode.FILM,
        )
        assertTrue(layout.wordmark.bottom < layout.card.top)
        assertTrue(layout.card.bottom < layout.caption.top)
        assertTrue(layout.card.top >= layout.safeTop)
        assertTrue(layout.card.bottom <= layout.safeBottom)
    }
}
