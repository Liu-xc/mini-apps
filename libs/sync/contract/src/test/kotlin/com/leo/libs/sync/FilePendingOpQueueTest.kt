package com.leo.libs.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePendingOpQueueTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun queue() = FilePendingOpQueue(tmp.newFile())

    private fun entity(id: String, at: Long = 0) =
        SyncEntity(id, mapOf("name" to SyncValue.Text(id)), at)

    @Test
    fun upsertReplacedByNewerUpsert() = runTest {
        val q = queue()
        q.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 1)))
        q.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 2)))
        val ops = q.all()
        assertEquals(1, ops.size)
        assertEquals(2, (ops.single() as SyncOp.Upsert).entity.updatedAt)
    }

    @Test
    fun upsertThenRemoveBecomesRemove() = runTest {
        val q = queue()
        q.enqueue(SyncOp.Upsert("Items", "i1", entity("i1")))
        q.enqueue(SyncOp.Remove("Items", "i1"))
        assertTrue(q.all().single() is SyncOp.Remove)
    }

    @Test
    fun removeThenUpsertResurrects() = runTest {
        val q = queue()
        q.enqueue(SyncOp.Remove("Items", "i1"))
        q.enqueue(SyncOp.Upsert("Items", "i1", entity("i1")))
        assertTrue(q.all().single() is SyncOp.Upsert)
    }

    @Test
    fun keysAreCollectionScoped() = runTest {
        val q = queue()
        q.enqueue(SyncOp.Remove("Items", "i1"))
        q.enqueue(SyncOp.Remove("Notes", "i1"))
        assertEquals(2, q.all().size)
    }

    @Test
    fun persistsAcrossInstances() = runTest {
        val file = tmp.newFile()
        val q1 = FilePendingOpQueue(file)
        q1.enqueue(SyncOp.Upsert("Items", "i1", entity("i1")))
        val q2 = FilePendingOpQueue(file)
        assertEquals(1, q2.all().size)
        assertEquals("i1", (q2.all().single() as SyncOp.Upsert).entity.id)
    }

    @Test
    fun acknowledgeRemovesByKeyOnly() = runTest {
        val q = queue()
        q.enqueue(SyncOp.Upsert("Items", "i1", entity("i1")))
        q.enqueue(SyncOp.Upsert("Items", "i2", entity("i2")))
        q.acknowledge(listOf(SyncOp.Remove("Items", "i1"))) // 同 key 即可移除，op 类型无关
        assertEquals(listOf("i2"), q.all().map { it.entityId })
    }

    @Test
    fun corruptedFileStartsEmpty() = runTest {
        val file = tmp.newFile().apply { writeText("{ broken") }
        assertTrue(FilePendingOpQueue(file).all().isEmpty())
    }
}
