package com.leo.darkroom.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.leo.darkroom.card.SampleArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 选图与照片源（it-001 US-1 / AC8）：
 * 相册（Photo Picker URI）/ 拍照回传 URI / 内置示例图 → 统一解码为 ≤1600px 的 ARGB_8888。
 */
class PhotoRepository(private val context: Context) {

    suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            var bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim <= 0) return@runCatching null
            var sample = 1
            while (maxDim / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            applyExif(uri, decoded)
        }.getOrNull()
    }

    private fun applyExif(uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    /** 内置示例图（轻量缓存，避免重复程序化绘制） */
    private val sampleCache = arrayOfNulls<Bitmap>(SampleArt.titles.size)

    fun sample(index: Int): Bitmap {
        sampleCache[index]?.let { return it }
        val bmp = SampleArt.render(index)
        sampleCache[index] = bmp
        return bmp
    }

    /** 分享用 PNG 编码到缓存（媒体内容暂存不在本对象职责内） */
    fun toPngBytes(bmp: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
