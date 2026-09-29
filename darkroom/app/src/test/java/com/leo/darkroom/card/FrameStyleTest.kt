package com.leo.darkroom.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** it-010：相纸框型调色板与创作选项参数——打印可读性与缩放限幅锁死。 */
class FrameStyleTest {

    @Test
    fun `every frame keeps print-level ink contrast on its paper`() {
        FrameStyle.entries.forEach { frame ->
            val p = CardPalette.forFrame(frame)
            val ratio = contrast(p.ink, p.paper)
            assertTrue("$frame ink/paper ${ratio}", ratio >= 7.0)
            val faint = contrast(p.inkFaint, p.paper)
            assertTrue("$frame inkFaint/paper ${faint}", faint >= 3.0)
        }
    }

    @Test
    fun `frames are pairwise distinct in paper tone`() {
        val papers = FrameStyle.entries.map { CardPalette.forFrame(it).paper }
        val distinct = papers.distinct()
        assertEquals(FrameStyle.entries.size, distinct.size)
        // 任意两款纸面明度差不小于 6/255，选择在缩略图上可辨
        for (i in papers.indices) for (j in i + 1 until papers.size) {
            assertTrue(abs(luma(papers[i]) - luma(papers[j])) >= 6f)
        }
    }

    @Test
    fun `title size options are bounded`() {
        TitleSizeOption.entries.forEach { opt ->
            assertTrue(opt.scale in 0.8f..1.2f)
        }
        assertEquals(1.0f, TitleSizeOption.REGULAR.scale)
    }

    @Test
    fun `frame only applies to polaroid cards`() {
        // 数码/胶片无视相纸选择：机器与片基配色恒定
        val d1 = CardPalette.forMode(com.leo.darkroom.develop.DevelopMode.DIGITAL, FrameStyle.NOIR)
        val d2 = CardPalette.forMode(com.leo.darkroom.develop.DevelopMode.DIGITAL, FrameStyle.CLASSIC)
        assertEquals(d1, d2)
        val p1 = CardPalette.forMode(com.leo.darkroom.develop.DevelopMode.POLAROID, FrameStyle.NOIR)
        val p2 = CardPalette.forMode(com.leo.darkroom.develop.DevelopMode.POLAROID, FrameStyle.CLASSIC)
        assertTrue(p1 != p2)
    }

    /** WCAG 相对亮度对比 */
    private fun contrast(a: Int, b: Int): Double {
        val la = relLuma(a); val lb = relLuma(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relLuma(argb: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun luma(argb: Int): Float =
        0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)
}
