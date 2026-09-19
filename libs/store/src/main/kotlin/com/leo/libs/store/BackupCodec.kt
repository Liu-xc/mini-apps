package com.leo.libs.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 备份编解码：zip = 快照 JSON + media/ 全量。
 * v1 仅支持 Replace（整包恢复）；各 app 的 zip 对外格式契约由本实现统一，
 * 约定条目名：snapshot.json、media/<fileName>。
 */
class BackupCodec<T : Any>(
    private val store: SnapshotStore<T>,
    private val media: MediaStore,
) {

    suspend fun exportTo(target: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_SNAPSHOT))
                store.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                for (name in media.list()) {
                    val f = media.file(name) ?: continue
                    zip.putNextEntry(ZipEntry("$ENTRY_MEDIA_DIR/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** 整包恢复（Replace）：快照经 decode+migrate 后原子提交，媒体逐个写回，返回恢复后的快照 */
    suspend fun importFrom(zipFile: File): Result<T> = runCatching {
        val names = mutableListOf<String>()
        val mediaBytes = mutableMapOf<String, ByteArray>()
        withContext(Dispatchers.IO) {
            ZipFile(zipFile).use { zip ->
                val snapshotEntry = zip.getEntry(ENTRY_SNAPSHOT)
                    ?: error("备份缺少 $ENTRY_SNAPSHOT 条目")
                val snapshot = store.decode(zip.getInputStream(snapshotEntry).readBytes())
                    ?: error("备份快照解码失败（结构不兼容或已损坏）")
                for (entry in zip.entries()) {
                    val n = entry.name.removePrefix("$ENTRY_MEDIA_DIR/")
                    if (entry.name.startsWith("$ENTRY_MEDIA_DIR/") && !entry.isDirectory && n.isNotEmpty()) {
                        names += n
                        mediaBytes[n] = zip.getInputStream(entry).readBytes()
                    }
                }
                store.commit(snapshot)
            }
        }
        for (n in names) media.put(mediaBytes.getValue(n), preferredName = n)
        store.load()
    }

    companion object {
        const val ENTRY_SNAPSHOT = "snapshot.json"
        const val ENTRY_MEDIA_DIR = "media"
    }
}
