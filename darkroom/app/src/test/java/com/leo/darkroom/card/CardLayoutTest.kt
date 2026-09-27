package com.leo.darkroom.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {

    @Test
    fun `classic polaroid aspect ratio is 1_20`() {
        val layout = CardLayout.solve(1000f)
        assertEquals(1.20f, layout.aspect, 1e-4f)
        assertEquals(1200f, layout.height, 1e-3f)
    }

    @Test
    fun `photo is square with equal side margins`() {
        val w = 960f
        val layout = CardLayout.solve(w)
        assertEquals(layout.photo.width, layout.photo.height, 1e-3f)
        // 左右边距相等
        assertEquals(layout.photo.left, w - layout.photo.right, 1e-3f)
        assertEquals(w * CardLayout.SIDE_FR, layout.photo.left, 1e-3f)
    }

    @Test
    fun `footer band is wider than sides (bottom-heavy polaroid)`() {
        val w = 960f
        val layout = CardLayout.solve(w)
        val footer = layout.height - layout.photo.bottom
        assertTrue(footer > layout.photo.left * 1.5f)
    }

    @Test
    fun `text rects fit inside footer without overlap on title vs stamp`() {
        val layout = CardLayout.solve(1000f)
        val footerTop = layout.photo.bottom
        assertTrue(layout.title.top >= footerTop)
        assertTrue(layout.stamp.top >= footerTop)
        assertTrue(layout.watermark.bottom <= layout.height)
        // it-002 O3：标题域与日期章域互斥（原 0.60/0.58 交叠 2% → 字形相撞）
        assertTrue("title.right ${layout.title.right} must not cross stamp.left ${layout.stamp.left}",
            layout.title.right <= layout.stamp.left)
        assertTrue(layout.stamp.right <= layout.width - 1f)
        // 竖直：标题在水印之上
        assertTrue(layout.title.bottom <= layout.watermark.top + 1f)
    }

    @Test
    fun `available height clamps card width (fits the develop slot)`() {
        // 显影台 weight 槽实测高 1028px 场景：原实现按宽度解出 1158px 越界
        val layout = CardLayout.solve(2000f, maxH = 1028f)
        assertTrue("height ${layout.height} must fit maxH", layout.height <= 1028f + 1e-3f)
        assertEquals(1.20f, layout.aspect, 1e-4f)
        assertEquals(1028f / 1.2f, layout.width, 1e-2f)
    }

    @Test
    fun `unlimited maxH keeps pure-width behavior`() {
        val plain = CardLayout.solve(960f)
        val unlimited = CardLayout.solve(960f, maxH = Float.MAX_VALUE)
        assertEquals(plain.width, unlimited.width, 1e-6f)
        assertEquals(plain.height, unlimited.height, 1e-6f)
        assertEquals(plain.title.right, unlimited.title.right, 1e-6f)
    }

    @Test
    fun `title and stamp zones are mutually exclusive for any width`() {
        for (w in floatArrayOf(320f, 720f, 1080f, 1600f)) {
            val layout = CardLayout.solve(w)
            assertTrue("w=$w title.right=${layout.title.right} stamp.left=${layout.stamp.left}",
                layout.title.right <= layout.stamp.left)
        }
    }

    @Test
    fun `type sizes scale with card width`() {
        val small = CardLayout.solve(320f)
        val large = CardLayout.solve(1280f)
        assertEquals(small.titleSize * 4f, large.titleSize, 1e-2f)
        assertEquals(small.watermarkSize * 4f, large.watermarkSize, 1e-2f)
        // 字号随尺寸缩放保持占位比例
        assertTrue(small.stampSize < large.stampSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width rejected`() {
        CardLayout.solve(0f)
    }
}
