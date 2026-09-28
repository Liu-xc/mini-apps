package com.leo.wardrobe.data.chat

import com.leo.libs.agent.Message
import com.leo.libs.agent.session.FileSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 应用侧会话目录（it-050）：消息仍由 agent 的 FileSessionStore 管理；本文件只保存
 * 会话列表所需的摘要。它不属于 WardrobeData，不会进入数据包。
 */
@Serializable
data class ChatSessionSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String = "",
)

class ChatSessionIndex(
    private val file: File,
    private val sessions: FileSessionStore,
) {
    private val mutex = Mutex()

    suspend fun list(): List<ChatSessionSummary> = mutex.withLock {
        ensureLegacyLocked()
        readLocked().sortedByDescending { it.updatedAt }
    }

    suspend fun create(): ChatSessionSummary = mutex.withLock {
        val now = System.currentTimeMillis()
        val result = ChatSessionSummary(
            id = "chat-${UUID.randomUUID()}",
            title = "新对话",
            createdAt = now,
            updatedAt = now,
        )
        writeLocked(readLocked() + result)
        result
    }

    suspend fun ensure(sessionId: String) = mutex.withLock {
        val current = readLocked()
        if (current.none { it.id == sessionId }) {
            val now = System.currentTimeMillis()
            writeLocked(current + ChatSessionSummary(sessionId, "穿搭顾问", now, now))
        }
    }

    suspend fun refresh(sessionId: String, messages: List<Message>) = mutex.withLock {
        val current = readLocked()
        val now = System.currentTimeMillis()
        val firstUser = messages.firstOrNull { it.role == com.leo.libs.agent.Role.User }?.text.orEmpty()
        val latest = messages.lastOrNull { it.text.isNotBlank() }?.text.orEmpty()
        val existing = current.find { it.id == sessionId }
        val updated = ChatSessionSummary(
            id = sessionId,
            title = firstUser.take(24).ifBlank { existing?.title ?: "新对话" },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            preview = latest.replace('\n', ' ').take(60),
        )
        writeLocked(current.filterNot { it.id == sessionId } + updated)
    }

    /** 将 it-041 的旧全局单会话登记为可读历史，迁移不改原消息文件。 */
    private suspend fun ensureLegacyLocked() {
        val current = readLocked()
        if (current.any { it.id == LEGACY_SESSION }) return
        val messages = sessions.messages(LEGACY_SESSION)
        if (messages.isNotEmpty()) {
            val created = messages.firstOrNull()?.createdAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
            val latest = messages.lastOrNull { it.text.isNotBlank() }?.text.orEmpty()
            val title = messages.firstOrNull { it.role == com.leo.libs.agent.Role.User }?.text.orEmpty()
            writeLocked(current + ChatSessionSummary(
                id = LEGACY_SESSION,
                title = title.take(24).ifBlank { "历史对话" },
                createdAt = created,
                updatedAt = messages.lastOrNull()?.createdAt?.takeIf { it > 0L } ?: created,
                preview = latest.replace('\n', ' ').take(60),
            ))
        }
    }

    private fun readLocked(): List<ChatSessionSummary> = runCatching {
        if (!file.exists()) emptyList()
        else Json.decodeFromString(ListSerializer(ChatSessionSummary.serializer()), file.readText())
    }.getOrDefault(emptyList())

    private fun writeLocked(value: List<ChatSessionSummary>) {
        file.parentFile?.mkdirs()
        val text = Json.encodeToString(ListSerializer(ChatSessionSummary.serializer()), value)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val LEGACY_SESSION = "wardrobe-chat"
    }
}
