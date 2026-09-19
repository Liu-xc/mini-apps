package com.leo.eats.data.json

import com.leo.eats.domain.model.EatsData
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * eats.json 读写（ADR-003，与 wardrobe 同模式）：原子写（tmp→rename）+ 上一版本 .bak
 * + schemaVersion 迁移入口。只依赖 java.io，JVM 单测可用。
 */
class JsonFileStore(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val file: File get() = File(dir, FILE_NAME)
    private val bak: File get() = File(dir, "$FILE_NAME.bak")

    fun load(): EatsData {
        dir.mkdirs()
        return read(file) ?: read(bak) ?: EatsData()
    }

    private fun read(f: File): EatsData? =
        runCatching { json.decodeFromString<EatsData>(f.readText()) }
            .getOrNull()
            ?.let(::migrate)

    /** 版本迁移：未来 schema 升级在此追加分支 */
    private fun migrate(data: EatsData): EatsData = when (data.schemaVersion) {
        SCHEMA_VERSION -> data
        else -> data
    }

    @Synchronized
    fun save(data: EatsData) {
        val tmp = File(dir, "$FILE_NAME.tmp")
        if (file.exists()) file.copyTo(bak, overwrite = true)
        tmp.writeText(json.encodeToString(EatsData.serializer(), data))
        if (!tmp.renameTo(file)) throw IOException("eats.json 原子替换失败")
        // 首次保存也补一份 bak，保证任意时刻都有可恢复副本
        if (!bak.exists()) file.copyTo(bak, overwrite = true)
    }

    companion object {
        const val FILE_NAME = "eats.json"
        const val SCHEMA_VERSION = 1
    }
}
