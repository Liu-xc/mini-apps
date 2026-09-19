package com.leo.libs.store

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Serializable
data class TestSnap(val schemaVersion: Int = 1, val items: List<String> = emptyList())

class SnapshotStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(
        migrations: List<SnapshotStore.Migration<TestSnap>> = emptyList(),
        expected: Int = 1,
    ) = SnapshotStore(
        dir = tmp.root,
        fileName = "snap.json",
        serializer = TestSnap.serializer(),
        default = { TestSnap() },
        versionOf = { it.schemaVersion },
        migrations = migrations,
        expectedVersion = expected,
    )

    @Test
    fun roundtripAndBakCreated() = runTest {
        val s = store()
        s.commit(TestSnap(items = listOf("a")))
        assertEquals(TestSnap(items = listOf("a")), s.load())
        assertTrue(s.file.exists())
        assertTrue(s.bakFile.exists())
    }

    @Test
    fun corruptedMainFallsBackToBak() = runTest {
        val s = store()
        s.commit(TestSnap(items = listOf("v1")))
        s.commit(TestSnap(items = listOf("v2")))
        s.file.writeText("{ broken !!!")
        val outcome = s.loadDetailed()
        assertTrue(outcome is SnapshotStore.LoadOutcome.Loaded)
        outcome as SnapshotStore.LoadOutcome.Loaded
        assertEquals(SnapshotStore.LoadOutcome.Source.BAK, outcome.source)
        assertEquals(listOf("v1"), outcome.data.items) // bak 是上一成功版本
    }

    @Test
    fun bothCorruptedReturnsDefault() = runTest {
        val s = store()
        s.commit(TestSnap(items = listOf("x")))
        s.file.writeText("bad")
        s.bakFile.writeText("bad too")
        assertEquals(TestSnap(), s.load())
        assertTrue(s.loadDetailed() is SnapshotStore.LoadOutcome.DefaultUsed)
    }

    @Test
    fun migrationChainAppliedAscendingAndPersisted() = runTest {
        // 造一个 v1 旧文件
        val json = Json { encodeDefaults = true }
        File(tmp.root, "snap.json").writeText(json.encodeToString(TestSnap.serializer(), TestSnap(schemaVersion = 1, items = listOf("old"))))

        val s = store(
            migrations = listOf(
                SnapshotStore.Migration(1) { it.copy(schemaVersion = 2, items = it.items + "m1") },
                SnapshotStore.Migration(2) { it.copy(schemaVersion = 3, items = it.items + "m2") },
            ),
            expected = 3,
        )
        val outcome = s.loadDetailed() as SnapshotStore.LoadOutcome.Loaded
        assertEquals(3, outcome.data.schemaVersion)
        assertEquals(listOf("old", "m1", "m2"), outcome.data.items)
        assertEquals(1, outcome.migratedFrom)
        // 迁移结果在下次 load 直接可得（不再重复迁移）
        assertEquals(outcome.data, s.load())
    }

    @Test
    fun forwardVersionTolerated() = runTest {
        val json = Json { encodeDefaults = true }
        File(tmp.root, "snap.json").writeText(
            json.encodeToString(TestSnap.serializer(), TestSnap(schemaVersion = 9, items = listOf("future"))),
        )
        assertEquals(listOf("future"), store().load().items)
    }

    @Test
    fun missingMigrationStepStopsSafely() = runTest {
        val json = Json { encodeDefaults = true }
        File(tmp.root, "snap.json").writeText(json.encodeToString(TestSnap.serializer(), TestSnap(schemaVersion = 1)))
        val s = store(migrations = listOf(SnapshotStore.Migration(2) { it.copy(schemaVersion = 3) }), expected = 3)
        // 没有 1→2 的迁移：停在 v1，不抛错不丢数据
        assertEquals(1, s.load().schemaVersion)
    }

    @Test
    fun commitFailurePropagatesAndOldFileIntact() = runTest {
        val s = store()
        s.commit(TestSnap(items = listOf("good")))
        // 用同名 tmp 目录堵死写入，模拟提交失败（主文件与 bak 均不动）
        File(tmp.root, "snap.json.tmp").mkdirs()
        var thrown = false
        try {
            s.commit(TestSnap(items = listOf("bad")))
        } catch (e: Exception) {
            thrown = true
        }
        assertTrue(thrown)
        assertTrue(s.file.exists())
        assertTrue(s.file.readText().contains("good"))
        assertTrue(s.bakFile.exists())
        assertTrue(s.bakFile.readText().contains("good"))
    }

    @Test
    fun decodeUsedByImport() = runTest {
        val s = store()
        val bytes = Json { encodeDefaults = true }.encodeToString(TestSnap.serializer(), TestSnap(items = listOf("z"))).toByteArray()
        assertEquals(listOf("z"), s.decode(bytes)?.items)
        assertEquals(null, s.decode("{ nope".toByteArray()))
    }
}
