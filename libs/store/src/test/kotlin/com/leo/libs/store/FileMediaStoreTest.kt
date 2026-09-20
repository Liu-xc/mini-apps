package com.leo.libs.store

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileMediaStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store get() = FileMediaStore(tmp.root, "images")

    @Test
    fun putReadRoundtrip() = runTest {
        val name = store.put(byteArrayOf(1, 2, 3))
        assertTrue(name.endsWith(".webp"))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.read(name))
        assertTrue(store.file(name).exists())
    }

    @Test
    fun preferredNameKept() = runTest {
        val name = store.put(byteArrayOf(9), preferredName = "fixed.webp")
        assertEquals("fixed.webp", name)
        assertArrayEquals(byteArrayOf(9), store.read("fixed.webp"))
    }

    @Test
    fun deleteAndMissingRead() = runTest {
        val name = store.put(byteArrayOf(1))
        store.delete(name)
        assertTrue(store.read(name) == null) // 内容已删
        store.delete(name)                   // 幂等
        assertTrue(store.read(name) == null)
    }
}
