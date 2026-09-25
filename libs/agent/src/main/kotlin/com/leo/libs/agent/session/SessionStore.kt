package com.leo.libs.agent.session

import com.leo.libs.agent.Message
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 会话持久化（接口注入，ADR-005）：SDK 不依赖 libs/store，app 可用自家快照实现或本文件实现。
 * 每条消息产出即 append（it-002 §3）——中断/出错后续跑历史一致。
 */
interface SessionStore {
    suspend fun append(sessionId: String, message: Message)
    suspend fun messages(sessionId: String): List<Message>
    suspend fun clear(sessionId: String)
}

class InMemorySessionStore : SessionStore {
    private val map = LinkedHashMap<String, MutableList<Message>>()
    override suspend fun append(sessionId: String, message: Message) {
        map.getOrPut(sessionId) { mutableListOf() } += message
    }

    override suspend fun messages(sessionId: String): List<Message> =
        map[sessionId]?.toList() ?: emptyList()

    override suspend fun clear(sessionId: String) {
        map.remove(sessionId)
    }
}

/**
 * 文件实现：`<dir>/<sessionId>.json`，整表重写，tmp → ATOMIC_MOVE 原子落盘（it-002 §3）。
 * sessionId 经字符白名单清洗，不可路径穿越。
 */
class FileSessionStore(private val directory: File) : SessionStore {

    private val mutex = Mutex()

    override suspend fun append(sessionId: String, message: Message) = mutex.withLock {
        val file = fileOf(sessionId)
        val list = readLocked(file).toMutableList()
        list += message
        writeAtomic(file, list)
    }

    override suspend fun messages(sessionId: String): List<Message> = mutex.withLock {
        readLocked(fileOf(sessionId))
    }

    override suspend fun clear(sessionId: String): Unit = mutex.withLock {
        fileOf(sessionId).delete()
        Unit
    }

    private fun fileOf(sessionId: String): File {
        require(sessionId.isNotBlank()) { "sessionId 不能为空" }
        val safe = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(directory, "$safe.json")
    }

    private fun readLocked(file: File): List<Message> {
        if (!file.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString(ListSerializer(Message.serializer()), file.readText())
        }.getOrElse { emptyList() } // 损坏文件按空会话处理，不崩
    }

    private fun writeAtomic(file: File, list: List<Message>) {
        file.parentFile?.mkdirs()
        val text = Json.encodeToString(ListSerializer(Message.serializer()), list)
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
}
