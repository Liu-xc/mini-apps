package com.leo.wardrobe.data.gen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSourceFileResolverTest {

    @Test
    fun `absolute export path is read directly instead of being prefixed with images directory`() {
        val export = File("/data/user/0/com.leo.wardrobe/files/export/outfit.jpg")
        var mediaStoreCalled = false

        val resolved = resolveImageSourceFile(export.path) {
            mediaStoreCalled = true
            File("/data/user/0/com.leo.wardrobe/files/images", it)
        }!!

        assertEquals(export, resolved)
        assertFalse(mediaStoreCalled)
    }

    @Test
    fun `relative media name still resolves through ImageStore`() {
        var mediaStoreCalled = false

        val resolved = resolveImageSourceFile("wardrobe-item.webp") {
            mediaStoreCalled = true
            File("/data/user/0/com.leo.wardrobe/files/images", it)
        }!!

        assertTrue(mediaStoreCalled)
        assertEquals(File("/data/user/0/com.leo.wardrobe/files/images/wardrobe-item.webp"), resolved)
    }
}
