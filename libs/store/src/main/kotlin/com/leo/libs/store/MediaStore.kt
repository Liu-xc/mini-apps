package com.leo.libs.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 媒体文件存取：编解码（压缩/旋转）归 app 注入，文件管理（uuid 命名、删除）归 SDK。
 * 云同步时代本地媒体是缓存：正本在云端附件，app 侧按 ref 命中本地或下载后写回。
 */
interface MediaStore {

    /** 写入字节，返回生成的文件名（uuid.ext，或使用 [preferredName]） */
    suspend fun put(bytes: ByteArray, ext: String = "webp", preferredName: String? = null): String

    suspend fun read(name: String): ByteArray?

    suspend fun delete(name: String)

    /** 文件句柄（不校验存在性；读取内容请用 [read]） */
    fun file(name: String): File
}

/** 目录实现：`root/subdir` 下平铺存文件，JVM/Android 通用 */
class FileMediaStore(
    private val root: File,
    private val subdir: String,
) : MediaStore {

    private val dir: File get() = File(root, subdir)

    override suspend fun put(bytes: ByteArray, ext: String, preferredName: String?): String =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val name = preferredName ?: "${UUID.randomUUID()}.$ext"
            File(dir, name).writeBytes(bytes)
            name
        }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = File(dir, name)
        if (f.exists()) f.readBytes() else null
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { File(dir, name).delete() }
    }

    override fun file(name: String): File = File(dir, name)
}
