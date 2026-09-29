package com.leo.darkroom.develop

import kotlin.math.exp
import kotlin.math.floor

/**
 * 确定性显现遮罩（it-007 M3 建立，it-008 修正 CHEMICAL 语义）：预览与导出渲染器共用的真源。
 *
 * 三种 [RevealKind] 共用噪声、桶缓存与 0..1 覆盖语义：
 * - [RevealKind.CHEMICAL] 拍立得——白浊阻光层**均匀消散**：真实相纸的显影发生在夹层内，
 *   用户看到的是整张画面从白里逐步浮现（it-008 修正：原「左下入口偏心前沿 + 堆积暗边」
 *   实机观感像胶水扩散，与真实过程不符）；仅保留低频纸面噪点模拟涂层不完全均匀
 * - [RevealKind.BLOCKS] 数码相机——网格块按种子顺序逐格点亮
 * - [RevealKind.SWEEP] 胶片——从左向右的冲洗推进带，带水洗湿边
 *
 * 未显影区按模式给色：相纸/胶片是中性浅灰乳剂底，数码屏是近黑（屏未点亮）。
 */
object RevealField {
    const val MASK_SIZE = 256
    private const val BUCKETS = 720

    /** 未显影底色（相纸、胶片） */
    private const val PALE = 0x00E7E7E2

    /** 前沿药膜堆积 */
    private const val EDGE = 0x00A9A9A4

    /** 数码屏未点亮的近黑底 / 点亮中的格边 */
    private const val SCREEN_OFF = 0x000C0C0C
    private const val SCREEN_EDGE = 0x002A2A2A

    fun bucket(kind: RevealKind, reveal: Float): Int = (reveal.coerceIn(0f, 1f) * BUCKETS).toInt()

    /** ARGB 像素；调用方用双线性过滤放大。 */
    fun alphaMask(kind: RevealKind, width: Int, height: Int, reveal: Float, seed: Int): IntArray {
        require(width > 0 && height > 0)
        if (reveal.coerceIn(0f, 1f) >= 0.999f) return IntArray(width * height)

        val amount = reveal.coerceIn(0f, 1f)
        val pale = if (kind == RevealKind.BLOCKS) SCREEN_OFF else PALE
        val edgeColor = if (kind == RevealKind.BLOCKS) SCREEN_EDGE else EDGE
        val pixels = IntArray(width * height)
        var index = 0
        for (py in 0 until height) {
            val y = (py + 0.5f) / height
            for (px in 0 until width) {
                val x = (px + 0.5f) / width
                val cell = sample(kind, x, y, amount, seed)
                val color = if (cell.band > 0.10f) edgeColor else pale
                pixels[index++] = (cell.alpha shl 24) or color
            }
        }
        return pixels
    }

    /** 单点不透明度（0..255，255 = 完全未显影）；测试与确定性 seek 用。 */
    fun alphaAt(kind: RevealKind, x: Float, y: Float, reveal: Float, seed: Int): Int {
        val amount = reveal.coerceIn(0f, 1f)
        if (amount >= 0.999f) return 0
        return sample(kind, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), amount, seed).alpha
    }

    private data class Cell(val alpha: Int, val band: Float)

    private fun sample(kind: RevealKind, x: Float, y: Float, amount: Float, seed: Int): Cell =
        when (kind) {
            RevealKind.CHEMICAL -> {
                // 白浊层均匀消散：无方向、无前沿暗边；低频噪点幅度 ≤5.5%，
                // 只提供纸面质感、读不出花斑（it-008 修正，对表真实拍立得观感）
                val mottle = valueNoise(x * 2.6f, y * 2.6f, seed) * 0.055f
                val visible = (amount + mottle).coerceIn(0f, 1f)
                val alpha = ((1f - visible) * 255f).toInt().coerceIn(0, 255)
                Cell(alpha, 0f)
            }

            RevealKind.BLOCKS -> {
                val wave = blockWave(x, y, seed)
                val threshold = amount * 1.25f - 0.10f
                val visible = smoothstep(wave - 0.10f, wave + 0.10f, threshold)
                // 数码不给湿边：格子要么亮要么灭，只留极窄的过渡
                Cell(alphaOf(1f - visible, 0f), 0f)
            }

            RevealKind.SWEEP -> {
                val edge = 0.055f + (1f - amount) * 0.050f
                val frontX = -0.22f + amount * 1.40f
                val wobble = valueNoise(y * 3.5f, 0.5f, seed) * 0.070f +
                    valueNoise(y * 9.5f, 1.5f, seed xor 7) * 0.025f
                val passed = frontX + wobble - x
                val visible = smoothstep(-edge, edge, passed)
                val band = gaussian(passed / edge)
                Cell(alphaOf(1f - visible, band), band)
            }
        }

    private fun alphaOf(cover: Float, band: Float): Int =
        ((cover + 0.32f * band).coerceAtMost(1f) * 255f).toInt().coerceIn(0, 255)

    /** 高斯带：峰值在前沿，向两侧衰减（药膜堆积 / 水洗湿边） */
    private fun gaussian(d: Float): Float = if (d < -3f || d > 3f) 0f else exp(-d * d * 0.55f)

    // —— BLOCKS：网格块逐格点亮 ——

    private fun blockWave(x: Float, y: Float, seed: Int): Float {
        val cells = 14f
        val gx = floor(x * cells)
        val gy = floor(y * cells)
        val fx = x * cells - gx
        val fy = y * cells - gy
        val order = lattice(gx.toInt(), gy.toInt(), seed)
        // 块内从左上向右下扫过，避免整格同时闪
        val local = (fx * 0.55f + fy * 0.45f) * 0.14f
        return order * 0.86f + local
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
