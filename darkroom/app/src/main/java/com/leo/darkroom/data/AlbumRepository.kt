package com.leo.darkroom.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.ImageDecoder
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

/** 图片进程内 LRU（it-011；it-012 收尾 2 分两层）：
 *  小图层=网格缩略（loadThumbnail 256，轻）；页图层=画册/导出用的原图降采样（1440 长边，
 *  软件位图——android.graphics 渲染端画不了硬件位图），条数少防内存超标。 */
object ThumbCache {
    private const val SMALL_MAX = 24
    private const val PAGE_MAX = 6

    private val small = lru(SMALL_MAX)
    private val page = lru(PAGE_MAX)

    private fun lru(max: Int) = object : LinkedHashMap<Pair<Long, Int>, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Int>, Bitmap>?): Boolean = size > max
    }

    @Synchronized
    fun getSmall(id: Long): Bitmap? = small[id to 256]

    @Synchronized
    fun putSmall(id: Long, bitmap: Bitmap) {
        small[id to 256] = bitmap
    }

    @Synchronized
    fun getPage(id: Long): Bitmap? = page[id to PAGE_KEY]

    @Synchronized
    fun putPage(id: Long, bitmap: Bitmap) {
        page[id to PAGE_KEY] = bitmap
    }

    const val PAGE_KEY = 0
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

    /** 网格缩略（256px）——小图场景够用，走系统缩略 */
    suspend fun gridThumb(photo: AlbumPhoto): Bitmap? = withContext(Dispatchers.IO) {
        ThumbCache.getSmall(photo.id) ?: runCatching {
            context.contentResolver.loadThumbnail(photo.uri, Size(256, 256), null)
        }.getOrNull()?.also { ThumbCache.putSmall(photo.id, it) }
    }

    /**
     * 画册页图（it-012 收尾 2：画质修复）：loadThumbnail 返回系统存的低质缩略（常见 512px JPEG），
     * 上千像素卡面与全屏大图均糊——改 ImageDecoder 解码原图并降采样到 1440 长边；
     * 强制软件位图（导出端 android.graphics.Canvas 画不了硬件位图）。
     */
    suspend fun pageImage(photo: AlbumPhoto, longEdge: Int = 1440): Bitmap? =
        withContext(Dispatchers.IO) {
            ThumbCache.getPage(photo.id) ?: runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxEdge = maxOf(info.size.width, info.size.height)
                    if (maxEdge > longEdge) {
                        val scale = maxEdge.toFloat() / longEdge
                        decoder.setTargetSize(
                            (info.size.width / scale).toInt().coerceAtLeast(1),
                            (info.size.height / scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }.getOrNull()?.also { ThumbCache.putPage(photo.id, it) }
        }

    companion object {
        const val PAGE_SIZE = 60
    }
}
