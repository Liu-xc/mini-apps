package com.leo.darkroom.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

/** 相册一张照片的索引项（it-011） */
data class AlbumPhoto(
    val id: Long,
    val uri: Uri,
    /** 拍摄时间（DATE_TAKEN 缺失时退回 DATE_ADDED），毫秒 */
    val dateTakenMs: Long,
    /** 成片日期章文案「yyyy MM dd」 */
    val dateText: String,
)

/** 缩略图进程内 LRU（it-011）：按 MediaStore id 缓存解码结果， pager 翻回即取 */
object ThumbCache {
    private const val MAX_ENTRIES = 24
    private val cache = object : LinkedHashMap<Long, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(id: Long): Bitmap? = cache[id]

    @Synchronized
    fun put(id: Long, bitmap: Bitmap) {
        cache[id] = bitmap
    }
}

/**
 * 相册读取（it-011 ADR-008）：MediaStore 近照倒序分页 + 缩略图解码，
 * 全部查询走 IO dispatcher；任何异常都吞成空页（相册不可用时 UI 自然落回选图回退态）。
 */
class AlbumRepository(private val context: Context) {

    suspend fun recent(offset: Int, limit: Int = PAGE_SIZE): List<AlbumPhoto> =
        withContext(Dispatchers.IO) {
            val photos = mutableListOf<AlbumPhoto>()
            runCatching {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                )
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    // SimpleDateFormat 非线程安全：本方法串行于 IO，独立实例
                    val fmt = SimpleDateFormat("yyyy MM dd", Locale.US)
                    var skipped = 0
                    while (cursor.moveToNext() && photos.size < limit) {
                        if (skipped++ < offset) continue
                        val taken = cursor.getLong(takenCol)
                        val addedSec = cursor.getLong(addedCol)
                        val whenMs = if (taken > 0) taken else addedSec * 1000L
                        val id = cursor.getLong(idCol)
                        photos += AlbumPhoto(
                            id = id,
                            uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString(),
                            ),
                            dateTakenMs = whenMs,
                            dateText = fmt.format(Date(whenMs)),
                        )
                    }
                }
            }
            photos
        }

    /** 相册缩略图（约 1080px，够 1080 宽卡片直用）；带进程内缓存 */
    suspend fun thumbnail(photo: AlbumPhoto, size: Int = 1080): Bitmap? =
        withContext(Dispatchers.IO) {
            ThumbCache.get(photo.id) ?: runCatching {
                context.contentResolver.loadThumbnail(photo.uri, Size(size, size), null)
            }.getOrNull()?.also { ThumbCache.put(photo.id, it) }
        }

    companion object {
        const val PAGE_SIZE = 60
    }
}
