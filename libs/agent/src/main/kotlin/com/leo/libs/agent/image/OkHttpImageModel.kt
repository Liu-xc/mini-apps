package com.leo.libs.agent.image

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ApiKeyStore
import com.leo.libs.agent.ImageProtocol
import com.leo.libs.agent.ProviderSpec
import com.leo.libs.agent.internal.await
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 同步生图传输（it-077）：一个类吃下两种同步协议——
 * [ImageProtocol.OPENAI_IMAGES_SYNC]（火山 / 硅基流动 / 智谱 CogView，端点 {base}/images/generations）
 * 与 [ImageProtocol.DASHSCOPE_SYNC]（百炼 qwen-image-edit 系，multimodal-generation 端点，base64 直传）。
 * 协议由 ModelSpec.imageProtocol 数据决定；输出 URL 24h 失效——字节在本类内下载完毕再吐给上层。
 */
class OkHttpImageModel(
    override val provider: ProviderSpec,
    private val apiKeys: ApiKeyStore,
    private val client: OkHttpClient = defaultClient(),
) : ImageModel {

    init {
        require(!provider.imageBaseUrl.isNullOrBlank()) { "provider ${provider.id} 未配置生图端点（imageBaseUrl）" }
    }

    override fun generate(request: ImageGenRequest): Flow<ImageGenEvent> = flow {
        emit(ImageGenEvent.Started)
        try {
            val key = apiKeys.get(provider.id)
                ?: throw AgentError.Auth("未配置 ${provider.displayName} 的 API Key（设置里粘贴后重试）")
            validateAgainstSpec(provider.model(request.model), request)
            emit(ImageGenEvent.Progress("生成中…"))
            when (provider.model(request.model)?.imageProtocol ?: ImageProtocol.OPENAI_IMAGES_SYNC) {
                ImageProtocol.OPENAI_IMAGES_SYNC -> openaiSync(request, key)
                ImageProtocol.DASHSCOPE_SYNC -> dashscopeSync(request, key)
                ImageProtocol.DASHSCOPE_ASYNC_TASK ->
                    throw AgentError.Schema("异步任务型模型请使用 DashScopeTaskImageModel（${request.model}）")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AgentError) {
            emit(ImageGenEvent.Failed(e))
        } catch (e: Exception) {
            // it-081/A-7：非 IO 运行时异常（坏 base64、意外 JSON 形状）也归一为 Failed 终态，
            // 不再以 Flow 异常击穿 collector（与 ImageGenEvent 契约「终态两种之后正常结束」对齐）
            emit(ImageGenEvent.Failed(
                if (e is IOException) AgentError.Network(e)
                else AgentError.Provider(-1, "生图失败：${e.message ?: e.javaClass.simpleName}")
            ))
        }
    }.flowOn(Dispatchers.IO)

    /** 声明式预检（cc-switch Rectifier 思想）：图数/输入形态不合规直接拦下，不去上游烧请求 */
    private fun validateAgainstSpec(spec: com.leo.libs.agent.ModelSpec?, request: ImageGenRequest) {
        val range = spec?.inputImages ?: return
        if (request.images.isNotEmpty() && request.images.size !in range) {
            throw AgentError.Schema("模型 ${request.model} 支持 ${range.first}~${range.last} 张参考图，当前 ${request.images.size} 张")
        }
        if (!spec.supportsBase64 && request.images.any { it is ImageRef.DataUri }) {
            throw AgentError.Schema("模型 ${request.model} 只接受图片公网 URL，本地图直传暂不支持（见模型说明）")
        }
    }

    private suspend fun FlowCollector<ImageGenEvent>.openaiSync(request: ImageGenRequest, key: String) {
        val body = buildJsonObject {
            put("model", request.model)
            put("prompt", request.prompt)
            if (request.images.isNotEmpty()) {
                // 单图传字符串、多图传数组（两家文档均按此形态给出；火山最多 14 路）
                val values = request.images.map { JsonPrimitive(it.value) }
                if (values.size == 1) put("image", values[0]) else put("image", JsonArray(values))
            }
            request.resolution?.let { put("size", it) }
            request.extra.forEach { (k, v) -> put(k, v) }
        }
        val root = postJson(endpoint("/images/generations"), body, key)
        val entries = extractImages(root)
            ?: throw AgentError.Provider(-1, "响应无图片字段（data/images 为空）")
        emit(ImageGenEvent.Progress("下载生成图…"))
        val images = entries.map { (url, b64) ->
            when {
                b64 != null -> GeneratedImage(Base64.getMimeDecoder().decode(b64), guessMime(url))
                url != null -> download(url)
                else -> throw AgentError.Provider(-1, "图片项缺 url 与 b64_json")
            }
        }
        emit(ImageGenEvent.Completed(images, images.size))
    }

    private suspend fun FlowCollector<ImageGenEvent>.dashscopeSync(request: ImageGenRequest, key: String) {
        val body = buildJsonObject {
            put("model", request.model)
            put("input", buildJsonObject {
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            request.images.forEach { ref ->
                                add(buildJsonObject { put("image", ref.value) })
                            }
                            add(buildJsonObject { put("text", request.prompt) })
                        })
                    })
                })
            })
            put("parameters", buildJsonObject {
                request.resolution?.let { put("size", it) }
                request.extra.forEach { (k, v) -> put(k, v) }
            })
        }
        val root = postJson(endpoint("/api/v1/services/aigc/multimodal-generation/generation"), body, key)
        val entries = extractImages(root)
            ?: throw AgentError.Provider(-1, "响应无图片字段（output.choices 为空）")
        val count = root["usage"]?.jsonObject?.get("image_count")?.jsonPrimitive?.content?.toIntOrNull()
            ?: entries.size
        emit(ImageGenEvent.Progress("下载生成图…"))
        val images = entries.map { (url, b64) ->
            when {
                b64 != null -> GeneratedImage(Base64.getMimeDecoder().decode(b64), guessMime(url))
                url != null -> download(url)
                else -> throw AgentError.Provider(-1, "图片项缺 url 与 b64_json")
            }
        }
        emit(ImageGenEvent.Completed(images, count))
    }

    private fun endpoint(path: String): String = provider.imageBaseUrl!!.trimEnd('/') + path

    /**
     * 挂起等响应的桥接已上收 internal HttpExt（it-081/O-4，与聊天轨共享）。
     */

    private suspend fun postJson(url: String, body: JsonObject, key: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            client.newCall(request).await().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw AgentError.fromHttp(resp.code, text, resp.header("Retry-After"))
                Json.parseToJsonElement(text).let { it as? JsonObject ?: throw AgentError.Provider(resp.code, "响应不是 JSON 对象") }
            }
        }.getOrElse { e ->
            throw if (e is AgentError) e else AgentError.Network(e as? IOException ?: IOException(e))
        }
    }

    /** 生成图下载（预签名 URL 免鉴权；读超时沿用长超时客户端，取消即断） */
    private suspend fun download(url: String): GeneratedImage {
        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(request).await().use { resp ->
                if (!resp.isSuccessful) throw AgentError.Provider(resp.code, "生成图下载失败 HTTP ${resp.code}")
                val mime = resp.header("Content-Type")?.substringBefore(';')
                    ?.takeIf { it.startsWith("image/") } ?: guessMime(url)
                GeneratedImage(resp.body?.bytes() ?: throw AgentError.Network(IOException("空响应体")), mime)
            }
        }.getOrElse { e ->
            throw if (e is AgentError) e else AgentError.Network(e as? IOException ?: IOException(e))
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** 生图一次 20~120s：读超时必须拉长（OkHttp 默认 10s 必超时） */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(170))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .build()

        internal fun guessMime(url: String?): String = when {
            url == null -> "image/png"
            url.contains(".jpg", true) || url.contains(".jpeg", true) -> "image/jpeg"
            url.contains(".webp", true) -> "image/webp"
            else -> "image/png"
        }
    }
}

/**
 * 宽松图片字段解析：兼容 OpenAI 形（data[].url / data[].b64_json）、
 * 硅基流动形（images[].url）与百炼形（output.choices[].message.content[].image）；
 * 无任何图片字段返回 null。
 */
internal fun extractImages(root: JsonObject): List<Pair<String?, String?>>? {
    val out = mutableListOf<Pair<String?, String?>>()
    for (arrayKey in listOf("data", "images")) {
        root[arrayKey]?.let { el ->
            (el as? JsonArray)?.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                val b64 = obj["b64_json"]?.jsonPrimitive?.contentOrNull
                if (url != null || b64 != null) out += url to b64
            }
        }
        if (out.isNotEmpty()) return out
    }
    runCatching {
        val content = root["output"]!!.jsonObject["choices"]!!.jsonArray.first()!!
            .jsonObject["message"]!!.jsonObject["content"]!!
        (content as? JsonArray)?.forEach { item ->
            val url = (item as? JsonObject)?.get("image")?.jsonPrimitive?.contentOrNull
            if (url != null) out += url to null
        }
    }
    return out.takeIf { it.isNotEmpty() }
}
