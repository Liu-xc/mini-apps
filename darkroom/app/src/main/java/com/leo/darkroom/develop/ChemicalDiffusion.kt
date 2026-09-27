package com.leo.darkroom.develop

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** Deterministic low-frequency emulsion front shared by preview and export renderers. */
object ChemicalDiffusion {
    const val MASK_SIZE = 192
    private const val MASK_RGB = 0x00262B22
    private const val BUCKETS = 720

    fun bucket(reveal: Float): Int = (reveal.coerceIn(0f, 1f) * BUCKETS).toInt()

    /** ARGB pixels for an opaque-to-transparent olive mask; callers upscale with bilinear filtering. */
    fun alphaMask(width: Int, height: Int, reveal: Float, seed: Int): IntArray {
        require(width > 0 && height > 0)
        val amount = reveal.coerceIn(0f, 1f)
        if (amount >= 0.999f) return IntArray(width * height)

        val pixels = IntArray(width * height)
        val edge = 0.035f + (1f - amount) * 0.025f
        val threshold = 1.12f - amount * 1.82f
        var index = 0
        for (py in 0 until height) {
            val y = (py + 0.5f) / height
            for (px in 0 until width) {
                val x = (px + 0.5f) / width
                val front = frontAt(x, y, seed)
                val visible = smoothstep(threshold - edge, threshold + edge, front)
                val alpha = ((1f - visible) * 255f).toInt().coerceIn(0, 255)
                pixels[index++] = (alpha shl 24) or MASK_RGB
            }
        }
        return pixels
    }

    /** Stable for a given position, amount and seed; useful for tests and deterministic seeking. */
    fun alphaAt(x: Float, y: Float, reveal: Float, seed: Int): Int {
        val amount = reveal.coerceIn(0f, 1f)
        if (amount >= 0.999f) return 0
        val edge = 0.035f + (1f - amount) * 0.025f
        val threshold = 1.12f - amount * 1.82f
        return ((1f - smoothstep(threshold - edge, threshold + edge, frontAt(x, y, seed))) * 255f)
            .toInt().coerceIn(0, 255)
    }

    private fun frontAt(x: Float, y: Float, seed: Int): Float {
        // Three offset elliptical blooms merge into one asymmetrical front.
        val driftX = (lattice(17, -8, seed) - 0.5f) * 0.05f
        val driftY = (lattice(-4, 23, seed) - 0.5f) * 0.05f
        val d0 = ellipseDistance(x, y, 0.31f + driftX, 0.34f + driftY, 0.56f, 0.66f)
        val d1 = ellipseDistance(x, y, 0.68f + driftX, 0.54f + driftY, 0.61f, 0.55f)
        val d2 = ellipseDistance(x, y, 0.45f + driftX, 0.78f + driftY, 0.64f, 0.50f)
        val distance = min(d0, min(d1, d2))
        val broad = valueNoise(x * 5.2f, y * 5.2f, seed) * 0.16f
        val detail = valueNoise(x * 12.7f, y * 12.7f, seed xor 0x5A17) * 0.045f
        return 1f - distance + broad + detail
    }

    private fun ellipseDistance(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return sqrt(dx * dx + dy * dy)
    }

    private fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smoothstep(0f, 1f, x - x0)
        val fy = smoothstep(0f, 1f, y - y0)
        val a = lerp(lattice(x0, y0, seed), lattice(x0 + 1, y0, seed), fx)
        val b = lerp(lattice(x0, y0 + 1, seed), lattice(x0 + 1, y0 + 1, seed), fx)
        return lerp(a, b, fy) * 2f - 1f
    }

    private fun lattice(x: Int, y: Int, seed: Int): Float {
        var n = seed xor (x * 0x1f123bb5) xor (y * 0x5f356495)
        n = (n xor (n ushr 16)) * 0x7feb352d
        n = (n xor (n ushr 15)) * 0x846ca68b.toInt()
        n = n xor (n ushr 16)
        return (n ushr 8).toFloat() / 0x00ffffff.toFloat()
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
