package com.leo.wardrobe.data.mock

import com.leo.libs.agent.ChatCompletion
import com.leo.libs.agent.ChatEvent
import com.leo.libs.agent.ChatModel
import com.leo.libs.agent.ChatRequest
import com.leo.libs.agent.Message
import com.leo.libs.agent.ProviderPreset
import com.leo.libs.agent.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** it-050：Mock 数据下真实 BYOK 的私有响应缓存。缓存的是单次模型完成态，工具回合也能重放。 */
class MockChatCache(private val directory: File) {
    @Serializable
    private data class Entry(
        val createdAt: Long,
        val completion: StoredCompletion,
    )

    @Serializable
    private data class StoredCompletion(
        val message: Message,
        val finishReason: String?,
        val usage: Usage,
    )

    fun get(key: String): ChatCompletion? {
        val file = File(directory, "$key.json")
        val entry = runCatching { Json.decodeFromString(Entry.serializer(), file.readText()) }.getOrNull()
        if (entry == null || System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            file.delete()
            return null
        }
        return ChatCompletion(entry.completion.message, entry.completion.finishReason, entry.completion.usage)
    }

    fun put(key: String, completion: ChatCompletion) {
        directory.mkdirs()
        val text = Json.encodeToString(
            Entry.serializer(),
            Entry(System.currentTimeMillis(), StoredCompletion(completion.message, completion.finishReason, completion.usage)),
        )
        val dest = File(directory, "$key.json")
        val tmp = File(directory, "$key.tmp")
        tmp.writeText(text)
        runCatching {
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            if (!tmp.renameTo(dest)) {
                dest.writeText(text)
                tmp.delete()
            }
        }
        prune()
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun prune() {
        val files = directory.listFiles { f -> f.extension == "json" }.orEmpty()
        val live = files.filter { it.lastModified() >= System.currentTimeMillis() - TTL_MS }
            .sortedByDescending { it.lastModified() }
        files.filterNot { it in live }.forEach { it.delete() }
        var bytes = live.sumOf { it.length() }
        live.drop(MAX_ENTRIES).forEach { bytes -= it.length(); it.delete() }
        live.take(MAX_ENTRIES).asReversed().forEach { file ->
            if (bytes > MAX_BYTES) { bytes -= file.length(); file.delete() }
        }
    }

    companion object {
        const val MOCK_DATA_VERSION = "mock-wardrobe-v1"
        const val MAX_ENTRIES = 100
        const val MAX_BYTES = 50L * 1024L * 1024L
        const val TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

/** 以请求+测试 Key 指纹命中缓存；不会保存 Key 或请求以外的数据。 */
class CachedMockChatModel(
    private val delegate: ChatModel,
    private val cache: MockChatCache,
    private val keyFingerprint: suspend () -> String,
) : ChatModel {
    private val pending = linkedMapOf<String, ChatCompletion>()
    var hadCacheHit: Boolean = false
        private set

    override val preset: ProviderPreset get() = delegate.preset

    /** W11 connectivity self-check uses complete(); always hit the provider so cached ping cannot fake health. */
    override suspend fun complete(request: ChatRequest): ChatCompletion = delegate.complete(request)

    /** ChatViewModel commits only after AgentRunner completes the whole answer. */
    fun commitPending() {
        pending.forEach { (key, completion) -> cache.put(key, completion) }
        pending.clear()
    }

    fun discardPending() {
        pending.clear()
    }

    fun resetRunStats() {
        hadCacheHit = false
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val key = keyOf(request)
        val cached = cache.get(key)
        if (cached != null) {
            hadCacheHit = true
            // Replayed usage was billed on the cache miss that created this entry; don't count it again.
            val replayed = cached.copy(usage = Usage())
            cached.message.toolCalls.forEachIndexed { index, call ->
                emit(ChatEvent.ToolCallDelta(index, call.id, call.name, call.argumentsJson))
            }
            if (replayed.message.text.isNotEmpty()) {
                replayed.message.text.chunked(12).forEach { emit(ChatEvent.TextDelta(it)) }
            }
            emit(ChatEvent.Completed(replayed))
            return@flow
        }
        var completion: ChatCompletion? = null
        delegate.stream(request).collect { event ->
            if (event is ChatEvent.Completed) completion = event.completion
            emit(event)
        }
        // 暂存当前模型步；整个 AgentRunner 完成后才提交，后续失败/取消会丢弃。
        completion?.let { pending[key] = it }
    }

    private suspend fun keyOf(request: ChatRequest): String {
        val canonical = buildString {
            append(MockChatCache.MOCK_DATA_VERSION).append('|')
            append(preset.id).append('|').append(preset.baseUrl).append('|')
            append(keyFingerprint()).append('|')
            append(request.model).append('|').append(request.temperature).append('|').append(request.maxTokens).append('|')
            append(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Message.serializer()), request.messages))
            request.tools.sortedBy { it.name }.forEach {
                append('|').append(it.name).append('|').append(it.description).append('|').append(it.parametersSchema)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
