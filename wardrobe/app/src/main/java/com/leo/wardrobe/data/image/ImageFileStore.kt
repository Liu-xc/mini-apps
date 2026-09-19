package com.leo.wardrobe.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.leo.wardrobe.domain.model.newId
import com.leo.wardrobe.domain.repository.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片文件存储：所有照片（单品/成品图）统一存为 images 目录下的 WebP，
 * 导入时 EXIF 摆正 + 最长边压至 1440 + 质量 82（specs/03-data-model.md）。
 */
class ImageFileStore(
    context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) : ImageStore {

    private val filesRoot = context.filesDir
    private val dir = File(filesRoot, "images").apply { mkdirs() }

    /** 合成图等导出临时文件目录（FileProvider export 路径） */
    fun exportDir(): File = File(filesRoot, "export").apply { mkdirs() }

    override suspend fun importFromUri(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(Uri.parse(uri)) ?: return@runCatching null
            val name = "${newId()}.webp"
            val out = File(dir, name)
            out.outputStream().use { fos ->
                val ok = compressWebp(bitmap, fos)
                bitmap.recycle()
                if (ok) name else null
            }
        }.getOrNull()
    }

    override fun file(file: String): File = File(dir, file)

    override suspend fun delete(file: String) = withContext(Dispatchers.IO) {
        File(dir, file).delete()
        Unit
    }

    /** 读取存储位图（UI/合成图用；失败返回 null） */
    suspend fun decode(file: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(File(dir, file).absolutePath) }.getOrNull()
    }

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
