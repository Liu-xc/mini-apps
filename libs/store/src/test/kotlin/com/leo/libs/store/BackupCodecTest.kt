package com.leo.libs.store

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Serializable
data class BackupSnap(val schemaVersion: Int = 1, val rows: List<String> = emptyList())

class BackupCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun build(): Triple<SnapshotStore<BackupSnap>, FileMediaStore, BackupCodec<BackupSnap>> {
        val store = SnapshotStore(
            dir = tmp.root, fileName = "data.json",
            serializer = BackupSnap.serializer(), default = { BackupSnap() },
        )
        val media = FileMediaStore(tmp.root, "images")
        return Triple(store, media, BackupCodec(store, media))
    }

    @Test
    fun exportImportRoundtripRestoresSnapshotAndMedia() = runTest {
        val (store, media, codec) = build()
        store.commit(BackupSnap(rows = listOf("r1", "r2")))
        val img = media.put(byteArrayOf(7, 7, 7))

        val zip = File(tmp.root, "backup.zip")
        codec.exportTo(zip).getOrThrow()

        // 清空本地（模拟换机）
        store.commit(BackupSnap())
        media.sweep(emptySet())
        assertEquals(0, media.list().size)

        val restored = codec.importFrom(zip).getOrThrow()
        assertEquals(listOf("r1", "r2"), restored.rows)
        assertArrayEquals(byteArrayOf(7, 7, 7), media.read(img))
    }

    @Test
    fun importRejectsZipWithoutSnapshotEntry() = runTest {
        val (_, _, codec) = build()
        val zip = File(tmp.root, "bad.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("whatever.bin"))
            zos.write(1)
        }
        val result = codec.importFrom(zip)
        assertTrue(result.isFailure)
    }

    @Test
    fun importRejectsCorruptedSnapshot() = runTest {
        val (store, _, codec) = build()
        val zip = File(tmp.root, "bad2.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry(BackupCodec.ENTRY_SNAPSHOT))
            zos.write("{ broken".toByteArray())
        }
        store.commit(BackupSnap(rows = listOf("keep")))
        val result = codec.importFrom(zip)
        assertTrue(result.isFailure)
        // 失败导入不破坏现有本地数据
        assertEquals(listOf("keep"), store.load().rows)
    }
}
