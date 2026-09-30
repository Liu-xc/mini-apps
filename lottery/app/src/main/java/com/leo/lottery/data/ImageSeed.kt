package com.leo.lottery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.leo.lottery.core.SeedHash
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Photo Picker URI → 64×64 软件位图 → 种子字节（03-data-model）。 */
object ImageSeed {

    private const val SIDE = 64

    data class SeedImage(val fingerprint: String, val bytes: ByteArray, val thumb: Bitmap) {
        override fun equals(other: Any?): Boolean =
            other is SeedImage && other.fingerprint == fingerprint

        override fun hashCode(): Int = fingerprint.hashCode()
    }

    fun decode(context: Context, uri: Uri): SeedImage? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val raw = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.setTargetSize(SIDE, SIDE)
        }
        val bitmap = if (raw.config == Bitmap.Config.ARGB_8888) raw
        else @Suppress("DEPRECATION") raw.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(SIDE * SIDE)
        bitmap.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
        // 大端固定字节序：种子跨设备稳定（与 ByteBuffer 默认无关）
        val bytes = ByteBuffer.allocate(pixels.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { asIntBuffer().put(pixels) }
            .array()
        val fingerprint = SeedHash.fingerprint(SeedHash.sha256Hex(bytes))
        SeedImage(fingerprint, bytes, bitmap)
    }.getOrNull()
}
