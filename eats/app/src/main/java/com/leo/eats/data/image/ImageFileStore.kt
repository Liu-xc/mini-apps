package com.leo.eats.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.leo.eats.domain.repository.ImageStore
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 图片文件存储：解码/EXIF 摆正/缩放/压缩在本类（Android 能力），
 * 文件管理（uuid 命名、删除）由 store SDK 的 [MediaStore] 承担（与 wardrobe 同构）。
 * 照片统一存为 images 目录下的 WebP（specs/03-data-model.md，与 wardrobe 同参）。
 */
class ImageFileStore(
    context: Context,
    private val media: MediaStore = FileMediaStore(context.filesDir, "images"),
    private val resolver: ContentResolver = context.contentResolver,
) : ImageStore {

    override suspend fun importFromUri(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(Uri.parse(uri)) ?: return@runCatching null
            val buffer = ByteArrayOutputStream()
            val ok = compressWebp(bitmap, buffer)
            bitmap.recycle()
            if (ok) media.put(buffer.toByteArray()) else null
        }.getOrNull()
    }

    override fun file(file: String): File = media.file(file)

    override suspend fun delete(file: String) = media.delete(file)

    /** it-012 数据包导入：包内图片字节 → 解码/缩放/WebP 压缩 → 以新 uuid.webp 落盘；不可解码返回 null */
    suspend fun putPackageImage(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val scaled = scaleDown(raw)
            val buffer = ByteArrayOutputStream()
            val ok = compressWebp(scaled, buffer)
            if (raw !== scaled) raw.recycle()
            scaled.recycle()
            if (!ok) null else media.put(buffer.toByteArray())
        }.getOrNull()
    }

    /** it-012 预检：包内图片可解码性（尺寸探针，不解全图） */
    fun isDecodableImage(bytes: ByteArray): Boolean = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.outWidth > 0 && opts.outHeight > 0
    }.getOrDefault(false)

    private fun compressWebp(bitmap: Bitmap, out: java.io.OutputStream): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, QUALITY, out)
        }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val upright = rotateByExif(uri, raw)
        return scaleDown(upright)
    }

    private fun rotateByExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_SIDE) return bitmap
        val ratio = MAX_SIDE.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        const val MAX_SIDE = 1440
        const val QUALITY = 82
    }
}
