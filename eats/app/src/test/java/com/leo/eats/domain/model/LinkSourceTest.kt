package com.leo.eats.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkSourceTest {

    @Test
    fun `美团链接识别`() {
        assertEquals(LinkSource.MEITUAN, LinkSource.detect("https://h5.waimai.meituan.com/waimai/mindex/home"))
        assertEquals(LinkSource.MEITUAN, LinkSource.detect("https://gdature.meituan.com/awp/h5/block/abc"))
    }

    @Test
    fun `大众点评链接识别`() {
        assertEquals(LinkSource.DIANPING, LinkSource.detect("https://m.dianping.com/shoplist/123"))
        assertEquals(LinkSource.DIANPING, LinkSource.detect("https://www.dianping.com/shop/987"))
    }

    @Test
    fun `其他链接与空值`() {
        assertEquals(LinkSource.OTHER, LinkSource.detect("https://example.com/shop"))
        assertEquals(LinkSource.OTHER, LinkSource.detect("not a url"))
    }

    @Test
    fun `大小写与首尾空白不敏感`() {
        assertEquals(LinkSource.MEITUAN, LinkSource.detect("  HTTPS://WAIMAI.MEITUAN.COM/X  "))
    }

    @Test
    fun `PlaceLink 的 source 派生`() {
        assertEquals(LinkSource.DIANPING, PlaceLink("https://m.dianping.com/x").source)
        assertEquals(LinkSource.MEITUAN, PlaceLink("https://i.meituan.com/awp/h5/xx", "双人套餐").source)
    }
}
