package com.leo.libs.agent

import com.leo.libs.agent.internal.SseDecoder
import com.leo.libs.agent.internal.StreamAssembler
import com.leo.libs.agent.internal.WireChatRequest
import com.leo.libs.agent.internal.WireChatResponse
import com.leo.libs.agent.internal.toCompletion
import com.leo.libs.agent.internal.toWire
import com.leo.libs.agent.internal.wireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 唯一 HTTP 实现：OpenAI Chat Completions 兼容传输（ADR-002/004）。
 * baseUrl 即完整前缀，仅追加 /chat/completions——GLM 无 /v1 与 MiMo /v1 的差异由 preset 数据表达。
 * SSE 自解析（不引 okhttp-sse）：解析器可单测、quirks 可控。
 */
class OkHttpChatModel(
    override val preset: ProviderPreset,
    private val apiKeys: ApiKeyStore,
    private val client: OkHttpClient = OkHttpClient(),
) : ChatModel {

    init {
        require(preset.baseUrl.isNotBlank()) {
            "preset ${preset.id} 的 baseUrl 未配置（MiMo 待 M0 校准回填，或使用 ProviderPreset.custom）"
        }
        require(preset.models.isNotEmpty()) { "preset ${preset.id} 未配置模型" }
    }

    private val endpoint: String get() = preset.baseUrl.trimEnd('/') + "/chat/completions"

    override suspend fun complete(request: ChatRequest): ChatCompletion = withContext(Dispatchers.IO) {
        // 阻塞式 execute 必须在 IO 线程——直接在 Main 上调会 NetworkOnMainThreadException
        // （it-041 阶段 A 模拟器实测抓到，M1 单测环境未覆盖）
        val call = newCall(request, stream = false)
        try {
            call.execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AgentError.fromHttp(resp.code, body, resp.header("Retry-After"))
                }
                return@withContext wireJson.decodeFromString(WireChatResponse.serializer(), body).toCompletion()
            }
        } catch (e: IOException) {
            throw AgentError.Network(e)
        }
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val call = newCall(request, stream = true)
        val assembler = StreamAssembler(preset.quirks)
        try {
            call.execute().use { resp ->
                val body = resp.body ?: throw AgentError.Network(IOException("空响应体"))
                if (!resp.isSuccessful) {
                    throw AgentError.fromHttp(resp.code, body.string(), resp.header("Retry-After"))
                }
                val decoder = SseDecoder()
                for (line in body.charStream().buffered().lineSequence()) {
                    currentCoroutineContext().ensureActive()
                    val payload = decoder.onLine(line) ?: continue
                    if (payload == DONE) break
                    assembler.feed(payload).forEach { emit(it) }
                }
                decoder.flush()?.takeIf { it != DONE }?.let { assembler.feed(it) }
            }
            emit(assembler.completed())
        } catch (e: IOException) {
            throw AgentError.Network(e)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun newCall(request: ChatRequest, stream: Boolean): okhttp3.Call {
        val key = apiKeys.get(preset.id)
            ?: throw AgentError.Auth("未配置 ${preset.displayName} 的 API Key（设置里粘贴后重试）")
        val wireRequest = WireChatRequest(
            model = request.model ?: preset.defaultModel,
            messages = request.messages.map { it.toWire() },
            tools = request.tools.takeIf { it.isNotEmpty() }?.map { it.toWire() },
            temperature = request.temperature,
            maxTokens = request.maxTokens,
            stream = stream,
        )
        val body = wireJson.encodeToString(WireChatRequest.serializer(), wireRequest)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.newCall(httpRequest)
    }

    private companion object {
        const val DONE = "[DONE]"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
