package com.leo.libs.sync.bitable

import com.leo.libs.sync.SyncCollection
import com.leo.libs.sync.SyncConfig
import com.leo.libs.sync.SyncEntity
import com.leo.libs.sync.SyncError
import com.leo.libs.sync.SyncException
import com.leo.libs.sync.SyncValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BitableSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val config = SyncConfig(
        backend = BitableSource.BACKEND_ID,
        params = mapOf("appId" to "cli_x", "appSecret" to "sec", "appToken" to "BascT"),
    )

    private val items = SyncCollection("Items")

    private fun entity(id: String, at: Long, fields: Map<String, SyncValue> = mapOf("name" to SyncValue.Text(id))) =
        SyncEntity(id, fields, at)

    // ---- connect ----

    @Test
    fun connectCreatesMissingTable() = runTest {
        val transport = FakeTransport { req ->
            when {
                req.url.contains("/auth/v3/") -> tokenOk()
                req.url.contains("/tables?") -> ok("""{"items":[],"has_more":false}""")
                req.url.endsWith("/tables") && req.method == "POST" ->
                    ok("""{"table_id":"tblNEW"}""")
                else -> ok()
            }
        }
        val source = BitableSource(transport)
        source.connect(config, listOf(items))
        val create = transport.requests.first { it.url.endsWith("/tables") && it.method == "POST" }
        val body = create.body!!.decodeToString()
        assertTrue(body.contains("\"name\":\"Items\""))
        assertTrue(body.contains("\"field_name\":\"id\"")) // 默认 id 文本列
    }

    @Test
    fun connectReusesExistingTableByName() = runTest {
        val transport = FakeTransport { req ->
            when {
                req.url.contains("/auth/v3/") -> tokenOk()
                req.url.contains("/tables?") ->
                    ok("""{"items":[{"table_id":"tblEX","name":"Items"}],"has_more":false}""")
                req.url.contains("/fields?") -> ok("""{"items":[{"field_name":"id","type":1}],"has_more":false}""")
                else -> ok()
            }
        }
        val source = BitableSource(transport)
        source.connect(config, listOf(items))
        // 无建表请求
        assertTrue(transport.requests.none { it.url.endsWith("/tables") && it.method == "POST" })
    }

    @Test
    fun connectWithBadSecretThrowsAuthFailed() = runTest {
        val transport = FakeTransport { _ ->
            err(200, 99991663, "app secret invalid")
        }
        val source = BitableSource(transport)
        var error: SyncError? = null
        try {
            source.connect(config, listOf(items))
        } catch (e: SyncException) {
            error = e.error
        }
        assertTrue(error is SyncError.AuthFailed)
    }

    @Test
    fun connectMissingParamThrowsAuthFailed() = runTest {
        val source = BitableSource(FakeTransport { ok() })
        var error: SyncError? = null
        try {
            source.connect(
                SyncConfig(BitableSource.BACKEND_ID, mapOf("appId" to "x", "appSecret" to "y")),
                listOf(items),
            )
        } catch (e: SyncException) {
            error = e.error
        }
        assertTrue(error is SyncError.AuthFailed)
    }

    // ---- push ----

    private fun standardTransport(vararg pages: String): FakeTransport {
        val pageQueue = pages.toMutableList()
        return FakeTransport { req ->
            when {
                req.url.contains("/auth/v3/") -> tokenOk()
                req.url.contains("/records/search") -> ok(pageQueue.removeFirstOrNull() ?: """{"items":[],"has_more":false}""")
                req.url.contains("/tables?") -> ok("""{"items":[{"table_id":"tbl1","name":"Items"}],"has_more":false}""")
                req.url.contains("/fields?") ->
                    ok("""{"items":[{"field_name":"id","type":1},{"field_name":"name","type":1}],"has_more":false}""")
                req.url.endsWith("/fields") && req.method == "POST" -> ok("""{"field":{}}""")
                req.url.contains("batch_create") ->
                    ok("""{"records":[{"record_id":"recNew1"},{"record_id":"recNew2"}]}""")
                req.url.contains("batch_update") -> ok("""{"records":[]}""")
                req.url.contains("batch_delete") -> ok("""{"records":[]}""")
                req.url.endsWith("/tables") -> ok("""{"table_id":"tblX"}""")
                else -> ok()
            }
        }
    }

    @Test
    fun pushCreateWritesIdFieldAndRegistersIndex() = runTest {
        val transport = standardTransport()
        val indexFile = tmp.newFile()
        val source = BitableSource(transport, indexFile = indexFile)
        source.connect(config, listOf(items))

        val result = source.push(items, listOf(entity("i1", 1), entity("i2", 2)), emptyList())

        assertEquals(listOf("i1", "i2"), result.applied)
        val createReq = transport.requests.first { it.url.contains("batch_create") }
        val body = createReq.body!!.decodeToString()
        assertTrue(body.contains("\"id\":\"i1\"")) // 全量写 id 列
        assertTrue(body.contains("\"name\":\"i1\""))

        // 第二次 push 同 id → 走 batch_update（索引已登记）
        source.push(items, listOf(entity("i1", 3)), emptyList())
        val updateReq = transport.requests.last { it.url.contains("batch_update") }
        assertTrue(updateReq.body!!.decodeToString().contains("recNew1"))
    }

    @Test
    fun pushAddsMissingColumnsByInference() = runTest {
        val transport = standardTransport()
        val source = BitableSource(transport)
        source.connect(config, listOf(items))

        // tags 列不存在（fields 列表里没有）→ 应 POST 建列，类型多选
        source.push(items, listOf(entity("i1", 1, mapOf("tags" to SyncValue.Options(listOf("通勤"))))), emptyList())

        val addField = transport.requests.first { it.url.endsWith("/fields") && it.method == "POST" }
        val body = addField.body!!.decodeToString()
        assertTrue(body.contains("\"field_name\":\"tags\""))
        assertTrue(body.contains("\"type\":4")) // 多选
    }

    @Test
    fun pushRemoveIsIdempotentForUnknownId() = runTest {
        val transport = standardTransport()
        val source = BitableSource(transport)
        source.connect(config, listOf(items))

        val result = source.push(items, emptyList(), listOf("ghost-id"))
        assertEquals(listOf("ghost-id"), result.applied) // 云端已无此行 → 视为成功
        assertTrue(transport.requests.none { it.url.contains("batch_delete") })
    }

    @Test
    fun readOnlyConfigRejectsPush() = runTest {
        val source = BitableSource(standardTransport())
        source.connect(config.copy(readOnly = true), listOf(items))
        var error: SyncError? = null
        try {
            source.push(items, listOf(entity("i1", 1)), emptyList())
        } catch (e: SyncException) {
            error = e.error
        }
        assertTrue(error is SyncError.PermissionDenied)
    }

    // ---- pull ----

    @Test
    fun pullPaginatesAndAdoptsUiCreatedRows() = runTest {
        val page1 = """
            {"items":[
              {"record_id":"recA","fields":{"id":"iA","name":"A"},"last_modified_time":100},
              {"record_id":"recB","fields":{"name":"B"},"last_modified_time":200}
            ],"has_more":true,"page_token":"p2"}
        """.trimIndent()
        val page2 = """
            {"items":[
              {"record_id":"recC","fields":{"id":"iC","name":"C"},"last_modified_time":300}
            ],"has_more":false}
        """.trimIndent()
        val transport = standardTransport(page1, page2)
        val indexFile = tmp.newFile()
        val source = BitableSource(transport, indexFile = indexFile)
        source.connect(config, listOf(items))

        val result = source.pull(items)
        assertEquals(3, result.entities.size)
        assertEquals(setOf("iA", "recB", "iC"), result.entities.map { it.id }.toSet()) // recB 无 id 列 → 收编 recordId
        assertEquals(200L, result.entities.first { it.id == "recB" }.updatedAt)

        // 收编后登记索引：下次 push recB 实体 → batch_update 而非 create
        source.push(items, listOf(SyncEntity("recB", mapOf("name" to SyncValue.Text("B2")), 2)), emptyList())
        assertTrue(transport.requests.any { it.url.contains("batch_update") })
    }

    @Test
    fun pullUpdatedAtFallsBackToUpdatedAtField() = runTest {
        val page = """
            {"items":[{"record_id":"recA","fields":{"id":"iA","updatedAt":777}}],"has_more":false}
        """.trimIndent()
        val source = BitableSource(standardTransport(page))
        source.connect(config, listOf(items))
        assertEquals(777L, source.pull(items).entities.single().updatedAt)
    }

    // ---- 错误折叠 & 限流 ----

    @Test
    fun rateLimitedFoldedWithResetFromHeader() = runTest {
        val t = FakeTransport { req ->
            when {
                req.url.contains("/auth/v3/") -> tokenOk()
                req.url.contains("/tables?") -> ok("""{"items":[{"table_id":"tbl1","name":"Items"}],"has_more":false}""")
                req.url.contains("/fields?") -> ok("""{"items":[{"field_name":"id","type":1}],"has_more":false}""")
                else -> err(429, 99991400, "freq", "x-ogw-ratelimit-reset" to "7")
            }
        }
        val source = BitableSource(t)
        source.connect(config, listOf(items))
        // WriteGate 重试后仍 429 → 记入 failed 而非中断，错误已折叠为 RateLimited
        val result = source.push(items, listOf(entity("i1", 1)), emptyList())
        val error = result.failed["i1"] as? SyncError.RateLimited
        assertTrue(error != null)
        assertEquals(7L, error!!.resetAfterSec)
    }

    @Test
    fun noScopeFoldedToPermissionDenied() {
        val e = fold(err(403, 1061073, "no scope auth"), """{"code":1061073}""")
        assertTrue(e.error is SyncError.PermissionDenied)
        assertTrue((e.error as SyncError.PermissionDenied).recoverHint.contains("发布版本"))
    }

    @Test
    fun writeGateRetriesOnRateLimitedThenSucceeds() = runTest {
        val delays = mutableListOf<Long>()
        val gate = WriteGate(maxAttempts = 3) { delays += it }
        var calls = 0
        val result = gate.write {
            calls++
            if (calls == 1) throw SyncException(SyncError.RateLimited(5))
            "done"
        }
        assertEquals("done", result)
        assertEquals(listOf(6L), delays) // reset+1
    }

    @Test
    fun writeGateGivesUpAfterMaxAttempts() = runTest {
        val gate = WriteGate(maxAttempts = 2) { }
        var calls = 0
        var thrown = false
        try {
            gate.write {
                calls++
                throw SyncException(SyncError.RateLimited(1))
            }
        } catch (e: SyncException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(2, calls)
    }
}
