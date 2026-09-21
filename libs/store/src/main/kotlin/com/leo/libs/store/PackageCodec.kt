package com.leo.libs.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 数据包 manifest.json（it-024 / it-012 包格式 v1）：
 * 自描述头——app 归属、双方 schema 版本、导出时间与来源，供导入预检与 agent 识别。
 */
@Serializable
data class PackageManifest(
    val packageFormat: Int = CURRENT_PACKAGE_FORMAT,
    /** 数据包归属应用 id（"wardrobe" / "eats"） */
    val app: String,
    /** 包内数据的应用 schemaVersion（与应用自身 schemaVersion 同一数轴） */
    val schemaVersion: Int,
    val exportedAt: Long,
    /** 产出方标识：app 导出写「应用名+版本」，agent 产包写工具名 */
    val generator: String,
    val counts: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val CURRENT_PACKAGE_FORMAT = 1
        const val FILE_NAME = "manifest.json"
        const val IMAGES_DIR = "images"
    }
}

/** 包结构/内容不合规；[appReason] 由应用层转成面向用户的拒绝理由（it-024 注记 10） */
sealed class PackageException(message: String) : Exception(message) {
    /** 解不开 zip / 缺 manifest / manifest 不是合法 JSON */
    class NotZip(cause: Throwable? = null) : PackageException("NOT_ZIP:${cause?.message ?: ""}")
    data class WrongApp(val actualApp: String) : PackageException("WRONG_APP:$actualApp")
    data class UnsupportedFormat(val format: Int) : PackageException("UNSUPPORTED_FORMAT:$format")
    data class NewerSchema(val version: Int, val expected: Int) : PackageException("NEWER_SCHEMA:$version>$expected")
    /** 数据文件无法反序列化为应用快照根 */
    class BadData(cause: Throwable) : PackageException("BAD_DATA:${cause.message}")
}

/**
 * 数据包编解码（it-024 / it-012；v1 设计稿 BackupCodec 的按需回归，0.2.0）：
 * zip = manifest.json + `<dataFileName>`（应用 SSOT 根原样，prettyPrint 便于 agent 阅读）+ images 目录。
 *
 * 职责边界：本类只管 zip 结构与 manifest 契约（零业务概念）；按 id 合并、实体级校验归应用 domain 层。
 */
class PackageCodec<T : Any>(
    private val appId: String,
    private val dataFileName: String,
    private val serializer: KSerializer<T>,
    private val expectedSchemaVersion: Int,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** 解析后的包：数据已反序列化，图片按名惰性读取；用完必须 [close] */
    class RawPackage<T : Any>(
        val manifest: PackageManifest,
        val data: T,
        zip: ZipFile,
    ) : AutoCloseable {
        internal val file: ZipFile = zip

        /** 包内 images/ 下全部文件名（已剥目录前缀） */
        val imageNames: Set<String> = file.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith("${PackageManifest.IMAGES_DIR}/") }
            .map { it.name.removePrefix("${PackageManifest.IMAGES_DIR}/") }
            .toSet()

        fun hasImage(name: String): Boolean = file.getEntry("${PackageManifest.IMAGES_DIR}/$name") != null

        fun imageBytes(name: String): ByteArray? =
            file.getEntry("${PackageManifest.IMAGES_DIR}/$name")
                ?.let { file.getInputStream(it).use { s -> s.readBytes() } }

        override fun close() = file.close()
    }

    /**
     * 读包并完成结构校验（不触碰应用本地数据）。校验顺序：zip 可解 → manifest 存在且合法
     * → app 归属 → 包格式版本 → schema 不高于当前 → 数据文件可反序列化。失败抛 [PackageException]。
     */
    fun read(file: File): RawPackage<T> {
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            throw PackageException.NotZip(e)
        } catch (e: java.io.IOException) {
            throw PackageException.NotZip(e)
        }
        try {
            val manifestEntry = zip.getEntry(PackageManifest.FILE_NAME) ?: throw PackageException.NotZip()
            val manifest = try {
                json.decodeFromString(
                    PackageManifest.serializer(),
                    zip.getInputStream(manifestEntry).use { it.readBytes().decodeToString() },
                )
            } catch (e: Throwable) {
                throw PackageException.NotZip(e)
            }
            if (manifest.app != appId) throw PackageException.WrongApp(manifest.app)
            if (manifest.packageFormat != PackageManifest.CURRENT_PACKAGE_FORMAT) {
                throw PackageException.UnsupportedFormat(manifest.packageFormat)
            }
            if (manifest.schemaVersion > expectedSchemaVersion) {
                throw PackageException.NewerSchema(manifest.schemaVersion, expectedSchemaVersion)
            }
            val dataEntry = zip.getEntry(dataFileName) ?: throw PackageException.NotZip()
            val data = try {
                json.decodeFromString(serializer, zip.getInputStream(dataEntry).use { it.readBytes().decodeToString() })
            } catch (e: Throwable) {
                throw PackageException.BadData(e)
            }
            return RawPackage(manifest, data, zip)
        } catch (t: Throwable) {
            runCatching { zip.close() }
            throw t
        }
    }

    /**
     * 写包：manifest + 数据文件 + images/<name>（图片内容经 [readImage] 获取，读不到的跳过）。
     * [onProgress] 按图片张数回调 (done, total)。
     */
    suspend fun write(
        out: OutputStream,
        data: T,
        exportedAt: Long,
        generator: String,
        counts: Map<String, Int>,
        imageNames: Collection<String>,
        readImage: suspend (String) -> ByteArray?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val manifest = PackageManifest(
            app = appId,
            schemaVersion = expectedSchemaVersion,
            exportedAt = exportedAt,
            generator = generator,
            counts = counts,
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(PackageManifest.FILE_NAME))
            zip.write(json.encodeToString(PackageManifest.serializer(), manifest).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(dataFileName))
            zip.write(json.encodeToString(serializer, data).encodeToByteArray())
            zip.closeEntry()

            val names = imageNames.toList()
            names.forEachIndexed { i, name ->
                val bytes = readImage(name) ?: return@forEachIndexed
                zip.putNextEntry(ZipEntry("${PackageManifest.IMAGES_DIR}/$name"))
                zip.write(bytes)
                zip.closeEntry()
                onProgress(i + 1, names.size)
            }
        }
    }
}
