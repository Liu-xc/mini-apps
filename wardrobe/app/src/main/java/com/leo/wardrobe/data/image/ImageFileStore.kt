package com.leo.wardrobe.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.MediaStore
import com.leo.wardrobe.domain.repository.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 图片文件存储：解码/EXIF 摆正/缩放/压缩在本类（Android 能力），
 * 文件管理（uuid 命名、删除）由 store SDK 的 [MediaStore] 承担。
 * 照片统一存为 images 目录下的 WebP（specs/03-data-model.md）。
 */
class ImageFileStore(
    context: Context,
    private val media: MediaStore = FileMediaStore(context.filesDir, "images"),
    private val resolver: ContentResolver = context.contentResolver,
) : ImageStore {

    private val filesRoot = context.filesDir

    /** 合成图等导出临时文件目录（FileProvider export 路径） */
    fun exportDir(): File = File(filesRoot, "export").apply { mkdirs() }

    override suspend fun importFromUri(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(Uri.parse(uri)) ?: run {
                android.util.Log.e(TAG, "decode failed: open/decode/bounds 阶段返回空 uri=$uri")
                return@runCatching null
            }
            val buffer = ByteArrayOutputStream()
            val ok = compressWebp(bitmap, buffer)
            bitmap.recycle()
            if (!ok) {
                android.util.Log.e(TAG, "webp compress returned false")
                return@runCatching null
            }
            media.put(buffer.toByteArray())
        }.onFailure { android.util.Log.e(TAG, "importFromUri 异常 uri=$uri", it) }
            .getOrNull()
    }

    override fun file(file: String): File? = media.file(file)

    override suspend fun delete(file: String) = media.delete(file)

    /** 读取存储位图（UI/合成图用；失败返回 null） */
    suspend fun decode(file: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(media.file(file)?.absolutePath) }.getOrNull()
    }

    private fun compressWebp(bitmap: Bitmap, out: java.io.OutputStream): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, QUALITY, out)
        }

    private fun decodeScaled(uri: Uri): Bitmap? {
        // 第一遍：只量尺寸（inJustDecodeBounds 模式下 decodeStream 按设计返回 null，
        // 返回值不可当失败信号——hotfix it-002+1 修复的根因，判断依据是 bounds 尺寸）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: run {
            android.util.Log.e(TAG, "openInputStream 为 null uri=$uri")
            return null
        }
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            android.util.Log.e(TAG, "尺寸解析失败（格式不支持?）w=${bounds.outWidth} h=${bounds.outHeight} uri=$uri")
            return null
        }

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
        private const val TAG = "Wardrobe"
    }
}
