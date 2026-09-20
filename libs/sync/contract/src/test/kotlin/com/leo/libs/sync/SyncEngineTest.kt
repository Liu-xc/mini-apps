package com.leo.libs.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 引擎一致性测试：跑任何 SyncSource 实现都应通过（可移植性即测试） */
class SyncEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- 测试桩 ----

    private open class FakeSource : SyncSource {
        override val backendId = "fake"
        val cloud = mutableListOf<SyncEntity>()
        val pushedUpserts = mutableListOf<SyncEntity>()
        val pushedRemoves = mutableListOf<String>()
        val uploaded = mutableListOf<String>()
        private var tokenSeq = 0

        override suspend fun connect(config: SyncConfig, collections: List<SyncCollection>) = Unit

        override suspend fun pull(collection: SyncCollection) = PullResult(entities = cloud.toList())

        override suspend fun push(collection: SyncCollection, upserts: List<SyncEntity>, removes: List<String>): PushResult {
            pushedUpserts += upserts
            pushedRemoves += removes
            return PushResult(applied = upserts.map { it.id } + removes, failed = emptyMap())
        }

        override suspend fun putAttachment(name: String, bytes: ByteArray): SyncValue.Attachment {
            uploaded += name
            return SyncValue.Attachment(ref = "tok-${++tokenSeq}")
        }

        override suspend fun fetchAttachment(ref: String): ByteArray = byteArrayOf(1)
    }

    private class FakeAdapter(override val collection: SyncCollection) : CollectionAdapter {
        val local = LinkedHashMap<String, Long>() // id → updatedAt
        val appliedUpserts = mutableListOf<SyncEntity>()
        val appliedDeletes = mutableListOf<String>()
        val pushedEntities = mutableListOf<SyncEntity>()
        var rejectIds: Set<String> = emptySet()
        var attachmentBytes: ByteArray? = null

        override suspend fun localVersions(): Map<String, Long> = local.toMap()
        override suspend fun applyCloud(upserts: List<SyncEntity>, deletes: List<String>): List<Rejected> {
            appliedUpserts += upserts
            appliedDeletes += deletes
            upserts.forEach { local[it.id] = it.updatedAt }
            deletes.forEach { local.remove(it) }
            return upserts.filter { it.id in rejectIds }.map { Rejected(it.id, "测试隔离") }
        }

        override suspend fun attachmentsFor(entity: SyncEntity): List<PendingAttachment> =
            entity.fields.entries
                .filter { (_, v) -> v is SyncValue.Attachment && v.ref.startsWith(LOCAL_REF_PREFIX) }
                .map { (name, v) ->
                    PendingAttachment(fieldName = name, fileName = (v as SyncValue.Attachment).ref.removePrefix(LOCAL_REF_PREFIX)) {
                        attachmentBytes ?: byteArrayOf(0)
                    }
                }

        override suspend fun onPushed(entities: List<SyncEntity>) {
            pushedEntities += entities
        }
    }

    private fun entity(id: String, at: Long, fields: Map<String, SyncValue> = mapOf("name" to SyncValue.Text(id))) =
        SyncEntity(id, fields, at)

    private fun engine(
        source: SyncSource,
        adapter: FakeAdapter,
        queue: FilePendingOpQueue = FilePendingOpQueue(tmp.newFile()),
    ) = SyncEngine(source, listOf(adapter), queue).also { eng ->
        kotlinx.coroutines.runBlocking { eng.connect(SyncConfig("fake", emptyMap())) }
    }

    // ---- push ----

    @Test
    fun pushSendsQueuedOpsAndAcks() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items"))
        val queue = FilePendingOpQueue(tmp.newFile())
        val engine = engine(source, adapter, queue)

        queue.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 10)))
        queue.enqueue(SyncOp.Remove("Items", "i9"))

        val summary = engine.push()
        assertEquals(2, summary.pushed)
        assertEquals(listOf("i1"), source.pushedUpserts.map { it.id })
        assertEquals(listOf("i9"), source.pushedRemoves)
        assertTrue(queue.all().isEmpty()) // 全部 ack
        assertEquals(1, adapter.pushedEntities.size) // onPushed 回调
    }

    @Test
    fun pushResolvesLocalAttachmentRefs() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply { attachmentBytes = byteArrayOf(9) }
        val queue = FilePendingOpQueue(tmp.newFile())
        val engine = engine(source, adapter, queue)

        queue.enqueue(
            SyncOp.Upsert(
                "Items", "i1",
                entity("i1", 1, mapOf("image" to SyncValue.Attachment(ref = "local:uuid.webp"))),
            ),
        )
        engine.push()

        assertEquals(listOf("uuid.webp"), source.uploaded)
        val pushed = source.pushedUpserts.single()
        assertTrue((pushed.fields["image"] as SyncValue.Attachment).ref.startsWith("tok-")) // 占位换真实 ref
        assertTrue(adapter.pushedEntities.single().fields["image"] is SyncValue.Attachment)
    }

    @Test
    fun pushSkipsAlreadyUploadedAttachments() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply { attachmentBytes = byteArrayOf(9) }
        val queue = FilePendingOpQueue(tmp.newFile())
        val engine = engine(source, adapter, queue)

        queue.enqueue(
            SyncOp.Upsert("Items", "i1", entity("i1", 1, mapOf("image" to SyncValue.Attachment(ref = "tok-existing")))),
        )
        engine.push()
        assertTrue(source.uploaded.isEmpty()) // 已是真实 ref，不重复上传
    }

    // ---- pull ----

    @Test
    fun pullAppliesNewAndNewerEntities() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply {
            local["old"] = 10 // 云端 old 版本 5 < 本地 10 → 不应用
        }
        source.cloud += listOf(entity("new", 100), entity("old", 5))
        val engine = engine(source, adapter)

        val summary = engine.pull()
        assertEquals(1, summary.pulled)
        assertEquals(listOf("new"), adapter.appliedUpserts.map { it.id })
    }

    @Test
    fun pullReconcilesDeletesButKeepsPendingCreations() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply {
            local["kept"] = 1
            local["deleted-remotely"] = 1
            local["pending-new"] = 1 // 本地新建未推（在队列里），云端无 → 不能当删除
        }
        source.cloud += entity("kept", 1)
        val queue = FilePendingOpQueue(tmp.newFile())
        queue.enqueue(SyncOp.Upsert("Items", "pending-new", entity("pending-new", 1)))
        val engine = engine(source, adapter, queue)

        val summary = engine.pull()
        assertEquals(listOf("deleted-remotely"), adapter.appliedDeletes)
        assertEquals(1, summary.deleted)
    }

    @Test
    fun conflictCloudNewerWinsAndDropsPendingOp() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items"))
        source.cloud += entity("i1", 100) // 云端更晚
        val queue = FilePendingOpQueue(tmp.newFile())
        queue.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 10)))
        val engine = engine(source, adapter, queue)

        engine.pull()
        assertTrue(queue.all().isEmpty()) // 本地输 → 弃队列
        assertEquals(listOf("i1"), adapter.appliedUpserts.map { it.id }) // 采用云端
    }

    @Test
    fun conflictLocalNewerKeepsPendingOp() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items"))
        source.cloud += entity("i1", 5) // 云端更早
        val queue = FilePendingOpQueue(tmp.newFile())
        queue.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 100)))
        val engine = engine(source, adapter, queue)

        engine.pull()
        assertEquals(1, queue.all().size) // 本地赢 → 队列保留（下次 push 覆盖云端）
        assertTrue(adapter.appliedUpserts.isEmpty()) // 云端版本不落地
    }

    @Test
    fun rejectedEntitiesSurfaceInSummary() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply { rejectIds = setOf("bad") }
        source.cloud += listOf(entity("good", 1), entity("bad", 1))
        val engine = engine(source, adapter)

        val summary = engine.pull()
        assertEquals(listOf("bad"), summary.rejected.map { it.id })
    }

    // ---- 状态机 ----

    @Test
    fun connectFailureSetsFailedStateAndThrows() = runTest {
        val failing = object : SyncSource by FakeSource() {
            override suspend fun connect(config: SyncConfig, collections: List<SyncCollection>) {
                throw SyncException(SyncError.AuthFailed("secret 失效"))
            }
        }
        val engine = SyncEngine(failing, emptyList(), FilePendingOpQueue(tmp.newFile()))
        var thrown = false
        try {
            engine.connect(SyncConfig("fake", emptyMap()))
        } catch (e: SyncException) {
            thrown = true
        }
        assertTrue(thrown)
        val state = engine.state.value as SyncState.Failed
        assertTrue(state.error is SyncError.AuthFailed)
        assertTrue(!state.retryable)
    }

    @Test
    fun pushBeforeConnectThrows() = runTest {
        val engine = SyncEngine(FakeSource(), emptyList(), FilePendingOpQueue(tmp.newFile()))
        var thrown = false
        try {
            engine.push()
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun pushEndsInDoneState() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items"))
        val queue = FilePendingOpQueue(tmp.newFile())
        val engine = engine(source, adapter, queue)
        queue.enqueue(SyncOp.Upsert("Items", "i1", entity("i1", 1)))

        engine.push()
        val done = engine.state.value as SyncState.Done
        assertEquals(1, done.pushed)
        assertEquals(0, done.pulled)
    }

    @Test
    fun pullEndsInDoneStateWithCounts() = runTest {
        val source = FakeSource()
        val adapter = FakeAdapter(SyncCollection("Items")).apply { local["gone"] = 1 }
        source.cloud += entity("new", 1)
        val engine = engine(source, adapter)

        engine.pull()
        val done = engine.state.value as SyncState.Done
        assertEquals(1, done.pulled)
        assertEquals(1, done.deleted)
    }

    @Test
    fun pullTransportFailureSetsFailedStateAndThrows() = runTest {
        val failing = object : SyncSource by FakeSource() {
            override suspend fun pull(collection: SyncCollection): PullResult =
                throw SyncException(SyncError.Network("断网"))
        }
        val engine = SyncEngine(failing, listOf(FakeAdapter(SyncCollection("Items"))), FilePendingOpQueue(tmp.newFile()))
        kotlinx.coroutines.runBlocking { engine.connect(SyncConfig("fake", emptyMap())) }

        var thrown = false
        try {
            engine.pull()
        } catch (e: SyncException) {
            thrown = true
        }
        assertTrue(thrown)
        val state = engine.state.value as SyncState.Failed
        assertTrue(state.error is SyncError.Network)
        assertTrue(state.retryable)
    }

    @Test
    fun attachmentUploadFailureIsolatesOp() = runTest {
        val source = object : SyncSource by FakeSource() {
            override suspend fun putAttachment(name: String, bytes: ByteArray): SyncValue.Attachment =
                throw SyncException(SyncError.Network("上传失败"))
        }
        val adapter = FakeAdapter(SyncCollection("Items")).apply { attachmentBytes = byteArrayOf(1) }
        val queue = FilePendingOpQueue(tmp.newFile())
        val engine = engine(source, adapter, queue)

        queue.enqueue(
            SyncOp.Upsert("Items", "withImg", entity("withImg", 1, mapOf("image" to SyncValue.Attachment("local:x.webp")))),
        )
        queue.enqueue(SyncOp.Upsert("Items", "plain", entity("plain", 1)))

        val summary = engine.push()
        // 带附件的失败并留队列；无附件的正常推走（ack 移除）
        assertEquals(1, summary.pushed)
        assertTrue(summary.failed.containsKey("withImg"))
        assertEquals(listOf("withImg"), queue.all().map { it.entityId })
    }
}
