package com.leo.wardrobe.data.mock

import com.leo.libs.agent.ModelCatalog
import com.leo.libs.agent.image.ImageGenEvent
import com.leo.libs.agent.image.ImageGenRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockImageModelTest {

    private val request = ImageGenRequest(
        model = "Kwai-Kolors/Kolors",
        prompt = "生成一张真人穿搭效果图",
    )

    @Test
    fun `no reference image returns the bundled sample rather than a one pixel placeholder`() = runBlocking {
        val sample = ByteArray(2048) { index -> (index % 251).toByte() }
        val model = MockImageModel(ModelCatalog.siliconflow) { sample }

        val events = model.generate(request).toList()
        val completed = events.filterIsInstance<ImageGenEvent.Completed>().single()

        assertTrue(events.first() is ImageGenEvent.Started)
        assertTrue(events.any { it is ImageGenEvent.Progress })
        assertEquals(1, completed.images.size)
        assertEquals("image/png", completed.images.single().mime)
        assertArrayEquals(sample, completed.images.single().bytes)
        assertTrue(completed.images.single().bytes.size > 1)
    }

    @Test
    fun `missing sample fails explicitly instead of returning a blank image`() = runBlocking {
        val model = MockImageModel(ModelCatalog.siliconflow) { null }

        val events = model.generate(request).toList()

        assertTrue(events.none { it is ImageGenEvent.Completed })
        assertEquals(1, events.filterIsInstance<ImageGenEvent.Failed>().size)
    }
}
