package com.leo.lottery.data

import com.leo.lottery.core.Ticket
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 票夹 JSON 存储：tmp → rename 原子写，未知字段忽略（ADR-005）。
 * core/平台无关部分，JVM 单测覆盖。
 */
class TicketStore(private val file: File) {

    @Serializable
    private data class Payload(val tickets: List<Ticket> = emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(): List<Ticket> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<Payload>(file.readText()).tickets
    }.getOrDefault(emptyList())

    fun save(tickets: List<Ticket>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Payload(tickets)))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) error("tickets.json 原子写失败")
        }
    }
}
