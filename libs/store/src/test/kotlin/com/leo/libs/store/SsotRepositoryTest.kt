package com.leo.libs.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Serializable
data class Counters(val schemaVersion: Int = 1, val n: Int = 0)

class RepoForTest(store: SnapshotStore<Counters>) : SsotRepository<Counters>(store) {
    suspend fun inc() = mutate { it.copy(n = it.n + 1) }
    suspend fun boom() = mutate { error("transform failed") }
}

class SsotRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newRepo(): Pair<SnapshotStore<Counters>, RepoForTest> {
        val store = SnapshotStore(
            dir = tmp.root,
            fileName = "c.json",
            serializer = Counters.serializer(),
            default = { Counters() },
        )
        return store to RepoForTest(store)
    }

    @Test
    fun mutatePersistsAndBroadcasts() = runTest {
        val (store, repo) = newRepo()
        repo.inc()
        repo.inc()
        assertEquals(2, repo.data.first().n)
        assertEquals(2, store.load().n)
    }

    @Test
    fun transformFailureKeepsOldStateAndFile() = runTest {
        val (store, repo) = newRepo()
        repo.inc()
        var thrown = false
        try {
            repo.boom()
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(1, repo.data.first().n)     // 内存快照回滚（未赋值）
        assertEquals(1, store.load().n)          // 落盘文件未变
    }

    @Test
    fun writeHookFiresAfterCommitWithNewSnapshot() = runTest {
        val (store, repo) = newRepo()
        val seen = mutableListOf<Int>()
        repo.writeHook = { seen += it.n }
        repo.inc()
        repo.inc()
        assertEquals(listOf(1, 2), seen)
        assertFalse(seen.contains(0))
    }

    @Test
    fun newRepositoryInstanceResumesFromFile() = runTest {
        val (store, repo) = newRepo()
        repo.inc()
        repo.inc()
        val resumed = RepoForTest(store)
        assertEquals(2, resumed.data.first().n)
    }
}
