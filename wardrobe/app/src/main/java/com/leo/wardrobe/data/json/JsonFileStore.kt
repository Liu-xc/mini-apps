package com.leo.wardrobe.data.json

import com.leo.wardrobe.domain.model.WardrobeData
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * wardrobe.json 读写（ADR-002）：原子写（tmp→rename）+ 上一版本 .bak + schemaVersion 迁移入口。
 * 只依赖 java.io，JVM 单测可用。
 */
class JsonFileStore(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val file: File get() = File(dir, FILE_NAME)
    private val bak: File get() = File(dir, "$FILE_NAME.bak")

    fun load(): WardrobeData {
        dir.mkdirs()
        return read(file) ?: read(bak) ?: WardrobeData()
    }

    private fun read(f: File): WardrobeData? =
        runCatching { json.decodeFromString<WardrobeData>(f.readText()) }
            .getOrNull()
            ?.let(::migrate)

    /** 版本迁移：未来 schema 升级在此追加分支 */
    private fun migrate(data: WardrobeData): WardrobeData = when (data.schemaVersion) {
        SCHEMA_VERSION -> data
        else -> data
    }

    @Synchronized
    fun save(data: WardrobeData) {
        val tmp = File(dir, "$FILE_NAME.tmp")
        if (file.exists()) file.copyTo(bak, overwrite = true)
        tmp.writeText(json.encodeToString(WardrobeData.serializer(), data))
        if (!tmp.renameTo(file)) throw IOException("wardrobe.json 原子替换失败")
        // 首次保存也补一份 bak，保证任意时刻都有可恢复副本
        if (!bak.exists()) file.copyTo(bak, overwrite = true)
    }

    companion object {
        const val FILE_NAME = "wardrobe.json"
        const val SCHEMA_VERSION = 1
    }
}
