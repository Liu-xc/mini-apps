package com.leo.libs.sync.bitable

import com.leo.libs.sync.AttachmentMeta
import com.leo.libs.sync.PullCursor
import com.leo.libs.sync.PullResult
import com.leo.libs.sync.PushResult
import com.leo.libs.sync.SyncCollection
import com.leo.libs.sync.SyncConfig
import com.leo.libs.sync.SyncEntity
import com.leo.libs.sync.SyncError
import com.leo.libs.sync.SyncException
import com.leo.libs.sync.SyncSource
import com.leo.libs.sync.SyncValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * 飞书多维表格 SyncSource 适配器（it-002 调研结论的实现载体）。
 *
 * 链路：appId/appSecret 换 tenant_access_token（2h 缓存）→ 按表名自动发现/建表 →
 * pull=searchRecords 全量分页（行内 id 列缺失时收编 recordId）→
 * push=batch_create/update/delete（RecordIndex 决定 create vs update，全量写 id 列）→
 * 附件=medias/upload_all 两步上传。所有写经 WriteGate 串行 + 429 退避。
 */
class BitableSource(
    private val transport: HttpTransport,
    indexFile: File? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    gate: WriteGate = WriteGate(),
) : SyncSource {

    override val backendId: String = BACKEND_ID

    private val index = RecordIndex(indexFile)
    private val writeGate = gate
    private val json = Json { ignoreUnknownKeys = true }

    // ---- 连接态（connect 后可用） ----
    private var config: SyncConfig? = null
    private var tokenCache: String? = null
    private var tokenExpireAt: Long = 0
    private var appToken: String = ""
    private val tableIds = mutableMapOf<String, String>()           // collection name → table_id
    private val fieldTypes = mutableMapOf<String, Map<String, Int>>() // table_id → field name → type

    // ---- SyncSource ----

    override suspend fun connect(config: SyncConfig, collections: List<SyncCollection>) {
        check(config.backend == BACKEND_ID) { "backend 不匹配：${config.backend}" }
        val appId = config.params.require("appId")
        val appSecret = config.params.require("appSecret")
        appToken = config.params.require("appToken")
        this.config = config

        fetchToken(appId, appSecret) // 连通性 + 凭证校验，失败抛 AuthFailed

        // 已有的表按名登记；缺失的建表（默认仅 id 文本列，其余列在首次 push 按值推断补齐）
        val existing = listTables()
        for (c in collections) {
            val found = existing[c.name]
            if (found != null) {
                tableIds[c.name] = found
            } else {
                val tableId = writeGate.write { createTable(c.name) }
                tableIds[c.name] = tableId
            }
            fieldTypes[tableIds.getValue(c.name)] = listFields(tableIds.getValue(c.name))
        }
    }

    override suspend fun pull(collection: SyncCollection, since: PullCursor?): PullResult {
        val tableId = tableIdOf(collection)
        ensureFieldTypesLoaded(tableId)
        val types = fieldTypes.getValue(tableId)

        val entities = mutableListOf<SyncEntity>()
        var pageToken: String? = null
        do {
            val data = call(
                method = "POST",
                path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/search" +
                    "?page_size=$PAGE_SIZE${pageToken?.let { "&page_token=$it" } ?: ""}",
                body = buildJsonObject { },
            )
            val items = data["items"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            for (item in items) {
                val record = item.jsonObject
                val recordId = record["record_id"]?.jsonPrimitive?.contentOrNull ?: continue
                val rawFields = record["fields"] as? JsonObject ?: JsonObject(emptyMap())
                val decoded = rawFields.entries.associate { (name, element) ->
                    name to ValueCodec.decode(types[name], element)
                }
                // id 策略：行内 id 列优先；飞书 UI 手建行无 id → 收编 recordId
                val entityId = (decoded[ID_FIELD] as? SyncValue.Text)?.value?.takeIf { it.isNotBlank() } ?: recordId
                index.put(collection.name, entityId, recordId)
                val lastModified = record["last_modified_time"]?.jsonPrimitive?.longOrNull
                    ?: (rawFields[UPDATED_AT_FIELD] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
                    ?: 0L
                entities += SyncEntity(id = entityId, fields = decoded, updatedAt = lastModified)
            }
            val hasMore = data["has_more"]?.jsonPrimitive?.contentOrNull == "true"
            pageToken = data["page_token"]?.jsonPrimitive?.contentOrNull?.takeIf { hasMore }
        } while (pageToken != null)

        return PullResult(entities = entities)
    }

    override suspend fun push(collection: SyncCollection, upserts: List<SyncEntity>, removes: List<String>): PushResult {
        checkNotReadOnly()
        val tableId = tableIdOf(collection)
        ensureFieldsFor(tableId, upserts)

        val applied = mutableListOf<String>()
        val failed = mutableMapOf<String, SyncError>()

        if (upserts.isNotEmpty()) {
            val toUpdate = mutableListOf<Pair<SyncEntity, String>>() // entity → recordId
            val toCreate = mutableListOf<SyncEntity>()
            for (e in upserts) {
                val rid = index.recordIdOf(collection.name, e.id)
                if (rid != null) toUpdate += e to rid else toCreate += e
            }

            toCreate.chunked(BATCH_SIZE).forEach { batch ->
                try {
                    val records = writeGate.write { batchCreate(tableId, batch) }
                    batch.forEachIndexed { i, entity ->
                        index.put(collection.name, entity.id, records[i])
                        applied += entity.id
                    }
                } catch (e: SyncException) {
                    batch.forEach { failed[it.id] = e.error }
                }
            }
            toUpdate.chunked(BATCH_SIZE).forEach { chunk ->
                try {
                    writeGate.write { batchUpdate(tableId, chunk) }
                    chunk.forEach { (entity, _) -> applied += entity.id }
                } catch (e: SyncException) {
                    chunk.forEach { (entity, _) -> failed[entity.id] = e.error }
                }
            }
        }

        if (removes.isNotEmpty()) {
            val pairs = removes.mapNotNull { id ->
                index.recordIdOf(collection.name, id)?.let { id to it }
            }
            // 云端已不存在的 id：幂等视为成功
            applied += (removes - pairs.map { it.first }.toSet())
            pairs.chunked(BATCH_SIZE).forEach { chunk ->
                try {
                    writeGate.write { batchDelete(tableId, chunk.map { it.second }) }
                    applied += chunk.map { it.first }
                } catch (e: SyncException) {
                    chunk.forEach { failed[it.first] = e.error }
                }
            }
        }
        return PushResult(applied = applied, failed = failed)
    }

    override suspend fun putAttachment(name: String, bytes: ByteArray): SyncValue.Attachment {
        checkNotReadOnly()
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            throw SyncException(SyncError.Unknown(0, detail = "附件超过 20MB 上限：$name"))
        }
        val token = writeGate.write {
            val data = callForm(
                url = "$BASE_URL/open-apis/drive/v1/medias/upload_all",
                parts = listOf(
                    FormPart("file_name", name),
                    FormPart("parent_type", "bitable_image"),
                    FormPart("parent_node", appToken),
                    FormPart("size", bytes.size.toString()),
                    FormPart("file", fileName = name, bytes = bytes, contentType = "image/webp"),
                ),
            )
            data["file_token"]?.jsonPrimitive?.contentOrNull
                ?: throw SyncException(SyncError.Unknown(0, detail = "上传未返回 file_token"))
        }
        return SyncValue.Attachment(ref = token, meta = AttachmentMeta(name = name, sizeBytes = bytes.size.toLong()))
    }

    override suspend fun fetchAttachment(ref: String): ByteArray {
        val data = call(
            method = "GET",
            path = "/open-apis/drive/v1/medias/batch_get_tmp_download_url?file_tokens=$ref",
        )
        val url = (data["tmp_download_url"] as? JsonObject)?.get(ref)?.jsonPrimitive?.contentOrNull
            ?: data["tmp_download_urls"]?.jsonArrayOrNull()?.let { arr ->
                (arr.firstOrNull() as? JsonObject)?.get("tmp_download_url")?.jsonPrimitive?.contentOrNull
            }
            ?: throw SyncException(SyncError.Unknown(0, detail = "未取得附件下载链接"))
        val resp = transport.execute(
            HttpRequest(method = "GET", url = url),
        )
        if (resp.status / 100 != 2) throw fold(resp, resp.bodyText)
        return resp.body
    }

    // ---- REST 封装 ----

    private suspend fun fetchToken(appId: String, appSecret: String) {
        val cached = tokenCache
        if (cached != null && now() < tokenExpireAt) return
        val resp = transport.execute(
            HttpRequest(
                method = "POST",
                url = "$BASE_URL/open-apis/auth/v3/tenant_access_token/internal",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                body = buildJsonObject {
                    put("app_id", appId)
                    put("app_secret", appSecret)
                }.toString().toByteArray(),
            ),
        )
        val body = json.parseToJsonElement(resp.bodyText).jsonObject
        val token = body["tenant_access_token"]?.jsonPrimitive?.contentOrNull
        if (resp.status / 100 != 2 || token == null) {
            throw fold(resp, resp.bodyText)
        }
        tokenCache = token
        tokenExpireAt = now() + ((body["expire"]?.jsonPrimitive?.longOrNull ?: 3600L) - EXPIRE_MARGIN_SEC) * 1000
    }

    private suspend fun token(): String {
        val cfg = config ?: throw SyncException(SyncError.AuthFailed("未 connect"))
        fetchToken(cfg.params.getValue("appId"), cfg.params.getValue("appSecret"))
        return tokenCache ?: throw SyncException(SyncError.AuthFailed("token 获取失败"))
    }

    private suspend fun listTables(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var pageToken: String? = null
        do {
            val data = call(
                method = "GET",
                path = "/open-apis/bitable/v1/apps/$appToken/tables?page_size=100" +
                    "${pageToken?.let { "&page_token=$it" } ?: ""}",
            )
            for (item in data["items"]?.jsonArrayOrNull() ?: JsonArray(emptyList())) {
                val obj = item.jsonObject
                val id = obj["table_id"]?.jsonPrimitive?.contentOrNull ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                result[name] = id
            }
            val hasMore = data["has_more"]?.jsonPrimitive?.contentOrNull == "true"
            pageToken = data["page_token"]?.jsonPrimitive?.contentOrNull?.takeIf { hasMore }
        } while (pageToken != null)
        return result
    }

    private suspend fun createTable(name: String): String {
        val data = call(
            method = "POST",
            path = "/open-apis/bitable/v1/apps/$appToken/tables",
            body = buildJsonObject {
                put(
                    "table",
                    buildJsonObject {
                        put("name", name)
                        put(
                            "fields",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("field_name", ID_FIELD)
                                        put("type", ValueCodec.TYPE_TEXT)
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        )
        return data["table_id"]?.jsonPrimitive?.contentOrNull
            ?: (data["table"] as? JsonObject)?.get("table_id")?.jsonPrimitive?.contentOrNull
            ?: throw SyncException(SyncError.Unknown(0, detail = "建表未返回 table_id"))
    }

    private suspend fun listFields(tableId: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var pageToken: String? = null
        do {
            val data = call(
                method = "GET",
                path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/fields?page_size=100" +
                    "${pageToken?.let { "&page_token=$it" } ?: ""}",
            )
            for (item in data["items"]?.jsonArrayOrNull() ?: JsonArray(emptyList())) {
                val obj = item.jsonObject
                val fieldName = obj["field_name"]?.jsonPrimitive?.contentOrNull ?: continue
                val type = obj["type"]?.jsonPrimitive?.intOrNullCompat() ?: continue
                result[fieldName] = type
            }
            val hasMore = data["has_more"]?.jsonPrimitive?.contentOrNull == "true"
            pageToken = data["page_token"]?.jsonPrimitive?.contentOrNull?.takeIf { hasMore }
        } while (pageToken != null)
        return result
    }

    /** push 前补列：实体里出现而表里没有的字段，按值类型推断建列 */
    private suspend fun ensureFieldsFor(tableId: String, upserts: List<SyncEntity>) {
        val existing = fieldTypes[tableId] ?: listFields(tableId).also { fieldTypes[tableId] = it }
        val missing = upserts.flatMap { it.fields.entries }
            .groupBy({ it.key }, { it.value })
            .filterKeys { it !in existing && it != ID_FIELD }
        for ((name, values) in missing) {
            val type = ValueCodec.inferFieldType(values.first())
            runCatching {
                call(
                    method = "POST",
                    path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/fields",
                    body = buildJsonObject {
                        put("field_name", name)
                        put("type", type)
                    },
                )
            }.onSuccess { fieldTypes[tableId] = existing + (name to type) }
        }
    }

    private fun encodeFields(entity: SyncEntity): JsonObject = buildJsonObject {
        put(ID_FIELD, JsonPrimitive(entity.id))
        for ((name, value) in entity.fields) {
            if (name == ID_FIELD) continue
            put(name, ValueCodec.encode(value))
        }
    }

    private suspend fun batchCreate(tableId: String, batch: List<SyncEntity>): List<String> {
        val data = call(
            method = "POST",
            path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/batch_create",
            body = buildJsonObject {
                put(
                    "records",
                    JsonArray(batch.map { buildJsonObject { put("fields", encodeFields(it)) } }),
                )
            },
        )
        return data["records"]?.jsonArrayOrNull()?.mapNotNull {
            (it as? JsonObject)?.get("record_id")?.jsonPrimitive?.contentOrNull
        } ?: throw SyncException(SyncError.Unknown(0, detail = "batch_create 未返回 records"))
    }

    private suspend fun batchUpdate(tableId: String, chunk: List<Pair<SyncEntity, String>>) {
        call(
            method = "POST",
            path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/batch_update",
            body = buildJsonObject {
                put(
                    "records",
                    JsonArray(
                        chunk.map { (entity, recordId) ->
                            buildJsonObject {
                                put("record_id", recordId)
                                put("fields", encodeFields(entity))
                            }
                        },
                    ),
                )
            },
        )
    }

    private suspend fun batchDelete(tableId: String, recordIds: List<String>) {
        call(
            method = "POST",
            path = "/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records/batch_delete",
            body = buildJsonObject {
                put("records", JsonArray(recordIds.map { JsonPrimitive(it) }))
            },
        )
    }

    // ---- 请求通道 + 错误折叠 ----

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject {
        val resp = transport.execute(
            HttpRequest(
                method = method,
                url = "$BASE_URL$path",
                headers = mapOf(
                    "Authorization" to "Bearer ${token()}",
                    "Content-Type" to "application/json; charset=utf-8",
                ),
                body = body?.toString()?.toByteArray(),
            ),
        )
        return unwrap(resp)
    }

    private suspend fun callForm(url: String, parts: List<FormPart>): JsonObject {
        val resp = transport.execute(
            HttpRequest(
                method = "POST",
                url = url,
                headers = mapOf("Authorization" to "Bearer ${token()}"),
                formParts = parts,
            ),
        )
        return unwrap(resp)
    }

    private fun unwrap(resp: HttpResponse): JsonObject {
        if (resp.status / 100 != 2) throw fold(resp, resp.bodyText)
        val body = runCatching { json.parseToJsonElement(resp.bodyText).jsonObject }
            .getOrElse { throw SyncException(SyncError.Unknown(resp.status, detail = "响应非 JSON")) }
        val code = body["code"]?.jsonPrimitive?.intOrNullCompat() ?: 0
        if (code != 0) throw fold(resp, resp.bodyText)
        return body["data"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun tableIdOf(collection: SyncCollection): String =
        tableIds[collection.name]
            ?: throw SyncException(SyncError.InvalidRecord(collection.name, null, "集合未在 connect 时登记"))

    private suspend fun ensureFieldTypesLoaded(tableId: String) {
        if (tableId !in fieldTypes) fieldTypes[tableId] = listFields(tableId)
    }

    private fun checkNotReadOnly() {
        if (config?.readOnly == true) {
            throw SyncException(SyncError.PermissionDenied("只读凭证不能写入"))
        }
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw SyncException(SyncError.AuthFailed("配置缺少参数：$key"))

    companion object {
        const val BACKEND_ID = "feishu-bitable"
        const val BASE_URL = "https://open.feishu.cn"
        const val ID_FIELD = "id"
        const val UPDATED_AT_FIELD = "updatedAt"
        const val PAGE_SIZE = 500
        const val BATCH_SIZE = 100
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
        private const val EXPIRE_MARGIN_SEC = 300L
    }
}

/** 错误折叠：HTTP/业务码 → 中立 SyncError（附平台化恢复文案） */
internal fun fold(resp: HttpResponse, bodyText: String): SyncException {
    val body = runCatching { Json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
    val code = body?.get("code")?.jsonPrimitive?.intOrNullCompat()
    val msg = body?.get("msg")?.jsonPrimitive?.contentOrNull ?: bodyText.take(200)

    val error: SyncError = when {
        resp.status == 429 || code == 99991400 -> {
            val reset = resp.header("x-ogw-ratelimit-reset")?.toLongOrNull() ?: 1L
            SyncError.RateLimited(reset, msg)
        }
        code == 1061073 -> SyncError.PermissionDenied("请到开发者后台为应用开通多维表格权限并发布版本")
        code == 1061061 -> SyncError.QuotaExceeded(msg)
        code in AUTH_FAILED_CODES -> SyncError.AuthFailed("App ID/Secret 无效或已被重置")
        resp.status == 403 || code in PERMISSION_CODES ->
            SyncError.PermissionDenied("请到多维表格「··· → 添加文档应用」把应用挂为协作者")
        else -> SyncError.Unknown(resp.status, code, msg)
    }
    return SyncException(error)
}

private val AUTH_FAILED_CODES = setOf(99991661, 99991663, 99991668)
private val PERMISSION_CODES = setOf(1254302, 1254304, 91403)

// JsonElement 兼容小工具
internal fun kotlinx.serialization.json.JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray
internal fun kotlinx.serialization.json.JsonPrimitive.intOrNullCompat(): Int? =
    if (isString) null else content.toIntOrNull()
