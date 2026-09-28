package com.leo.darkroom.card

import android.graphics.Bitmap
import com.leo.darkroom.develop.RevealField
import com.leo.darkroom.develop.RevealKind
import java.util.LinkedHashMap

/** Small cached masks keep the shared organic front cheap in the 30 fps video renderer. */
object ChemicalMaskBitmap {
    private data class Key(val kind: RevealKind, val bucket: Int, val seed: Int)

    private val cache = object : LinkedHashMap<Key, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?): Boolean = size > 20
    }

    fun forPhoto(kind: RevealKind, photo: Bitmap, reveal: Float): Bitmap {
        val seed = seedFor(photo)
        val bucket = RevealField.bucket(kind, reveal)
        val key = Key(kind, bucket, seed)
        synchronized(cache) {
            cache[key]?.let { return it }
            val pixels = RevealField.alphaMask(
                kind,
                RevealField.MASK_SIZE,
                RevealField.MASK_SIZE,
                bucket / 720f,
                seed,
            )
            val bitmap = Bitmap.createBitmap(
                pixels,
                RevealField.MASK_SIZE,
                RevealField.MASK_SIZE,
                Bitmap.Config.ARGB_8888,
            )
            cache[key] = bitmap
            return bitmap
        }
    }

    private fun seedFor(photo: Bitmap): Int {
        var seed = photo.width * 31 + photo.height
        for (gy in 0..3) {
            for (gx in 0..3) {
                val x = ((gx + 0.5f) * photo.width / 4f).toInt().coerceIn(0, photo.width - 1)
                val y = ((gy + 0.5f) * photo.height / 4f).toInt().coerceIn(0, photo.height - 1)
                seed = seed * 31 + photo.getPixel(x, y)
            }
        }
        return seed
    }
}
