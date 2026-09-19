package com.leo.libs.store

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertNotNull(store.file(name))
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
        assertNull(store.read(name))       // 内容已删
        assertTrue(store.list().isEmpty()) // 文件确已移除
        store.delete(name)                 // 幂等
    }

    @Test
    fun sweepRemovesOrphansKeepsValid() = runTest {
        val keep1 = store.put(byteArrayOf(1))
        val keep2 = store.put(byteArrayOf(2))
        store.put(byteArrayOf(3)) // 孤儿
        val removed = store.sweep(setOf(keep1, keep2))
        assertEquals(1, removed)
        assertEquals(setOf(keep1, keep2), store.list().toSet())
    }

    @Test
    fun listEmptyWhenDirAbsent() = runTest {
        assertTrue(store.list().isEmpty())
    }
}
