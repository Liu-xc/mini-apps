package com.leo.libs.agent.image

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ApiKeyStore
import com.leo.libs.agent.ProviderSpec
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 百炼异步任务型生图传输（it-077）：提交任务（Header `X-DashScope-Async: enable`）→ 轮询
 * `GET /api/v1/tasks/{task_id}` 直到 SUCCEEDED/FAILED。面向 aitryon/wan 系——**只收公网 URL**
 * （本地照片需图床前置，二期开放）；v1 只做传输与单测，UI 不放出（it-077 拍板）。
 *
 * 轮询为前台交互限定（用户正盯着生成页/对话流），jobId 不持久化——进程被杀即作废该次任务，
 * 厂商侧可能仍在执行但不重试（new-api 同步等待 300s 必断的教训：不把异步任务当 provider 级失败）。
 */
class DashScopeTaskImageModel(
    override val provider: ProviderSpec,
    private val apiKeys: ApiKeyStore,
    private val client: OkHttpClient = OkHttpImageModel.defaultClient(),
    private val pollIntervalMs: Long = 3_000,
    private val deadlineMs: Long = 180_000,
) : ImageModel {

    init {
        require(!provider.imageBaseUrl.isNullOrBlank()) { "provider ${provider.id} 未配置生图端点（imageBaseUrl）" }
    }

    override fun generate(request: ImageGenRequest): Flow<ImageGenEvent> = flow {
        emit(ImageGenEvent.Started)
        try {
            val key = apiKeys.get(provider.id)
                ?: throw AgentError.Auth("未配置 ${provider.displayName} 的 API Key（设置里粘贴后重试）")
            if (request.images.any { it is ImageRef.DataUri }) {
                throw AgentError.Schema("异步任务型模型只接受图片公网 URL（${request.model}）")
            }
            emit(ImageGenEvent.Progress("提交生成任务…"))
            val taskId = submit(request, key)
            emit(ImageGenEvent.Progress("排队中…"))
            val urls = poll(taskId, key)
            val images = urls.map { download(it) }
            emit(ImageGenEvent.Completed(images, images.size))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AgentError) {
            emit(ImageGenEvent.Failed(e))
        } catch (e: IOException) {
            emit(ImageGenEvent.Failed(AgentError.Network(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun endpoint(path: String): String = provider.imageBaseUrl!!.trimEnd('/') + path

    /** 参考图语义按位映射：首张人物照、次张上装、第三张下装（aitryon 入参结构） */
    private suspend fun submit(request: ImageGenRequest, key: String): String {
        val body = buildJsonObject {
            put("model", request.model)
            put("input", buildJsonObject {
                request.images.getOrNull(0)?.let { put("person_image_url", it.value) }
                request.images.getOrNull(1)?.let { put("top_garment_url", it.value) }
                request.images.getOrNull(2)?.let { put("bottom_garment_url", it.value) }
            })
            put("parameters", buildJsonObject {
                request.resolution?.let { put("resolution", it) }
                request.extra.forEach { (k, v) -> put(k, v) }
            })
        }
        val httpRequest = Request.Builder()
            .url(endpoint("/api/v1/services/aigc/image2image/image2image"))
            .header("Authorization", "Bearer $key")
            .header(ASYNC_HEADER, "enable")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val root = execute(httpRequest)
        val taskId = root["output"]?.jsonObject?.get("task_id")?.jsonPrimitive?.contentOrNull
        if (taskId.isNullOrBlank()) throw AgentError.Provider(-1, "提交任务失败：响应无 task_id")
        return taskId
    }

    private suspend fun FlowCollector<ImageGenEvent>.poll(taskId: String, key: String): List<String> {
        val start = System.nanoTime()
        while (true) {
            if ((System.nanoTime() - start) / 1_000_000 > deadlineMs) {
                throw AgentError.Provider(-1, "生成超时（任务可能仍在厂商侧执行，未扣费则不会计费）")
            }
            val httpRequest = Request.Builder()
                .url(endpoint("/api/v1/tasks/$taskId"))
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            val output = execute(httpRequest)["output"]?.jsonObject
                ?: throw AgentError.Provider(-1, "任务查询响应无 output")
            when (output["task_status"]?.jsonPrimitive?.contentOrNull) {
                "PENDING" -> {}
                "RUNNING", "PREVIEWABLE" -> {}
                "SUCCEEDED", "SUCCESS" -> {
                    val urls = output["results"]?.jsonArray
                        ?.mapNotNull { (it as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull }
                        .orEmpty()
                    if (urls.isEmpty()) {
                        val single = output["image_url"]?.jsonPrimitive?.contentOrNull
                        if (single != null) return listOf(single)
                        throw AgentError.Provider(-1, "任务成功但响应无图片")
                    }
                    return urls
                }
                "FAILED", "CANCELED", "UNKNOWN" -> {
                    val code = output["code"]?.jsonPrimitive?.contentOrNull ?: "TaskFailed"
                    val message = output["message"]?.jsonPrimitive?.contentOrNull ?: "任务失败"
                    throw AgentError.Provider(-1, "[$code] $message")
                }
                else -> {}
            }
            emit(ImageGenEvent.Progress("生成中…"))
            delay(pollIntervalMs)
        }
    }

    private suspend fun download(url: String): GeneratedImage {
        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(request).await().use { resp ->
                if (!resp.isSuccessful) throw AgentError.Provider(resp.code, "生成图下载失败 HTTP ${resp.code}")
                val mime = resp.header("Content-Type")?.substringBefore(';')
                    ?.takeIf { it.startsWith("image/") } ?: OkHttpImageModel.guessMime(url)
                GeneratedImage(resp.body?.bytes() ?: throw AgentError.Network(IOException("空响应体")), mime)
            }
        }.getOrElse { e -> throw if (e is AgentError) e else AgentError.Network(e as? IOException ?: IOException(e)) }
    }

    /** 异步桥接（it-077 live 补修）：协程取消即刻取消 HTTP 请求，同 OkHttpImageModel */
    private suspend fun Call.await(): okhttp3.Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: okhttp3.Response) {
                cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    private suspend fun execute(request: Request): JsonObject = runCatching {
        client.newCall(request).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AgentError.fromHttp(resp.code, text, resp.header("Retry-After"))
            Json.parseToJsonElement(text).let { it as? JsonObject ?: throw AgentError.Provider(resp.code, "响应不是 JSON 对象") }
        }
    }.getOrElse { e -> throw if (e is AgentError) e else AgentError.Network(e as? IOException ?: IOException(e)) }

    companion object {
        const val ASYNC_HEADER = "X-DashScope-Async"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
