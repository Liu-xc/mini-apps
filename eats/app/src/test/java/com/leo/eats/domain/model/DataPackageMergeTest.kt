package com.leo.eats.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-012：数据包合并/差异/eats 特有校验纯函数 */
class DataPackageMergeTest {

    private fun place(id: String, name: String = id, tags: List<String> = emptyList(), updatedAt: Long = 0L) =
        Place(id = id, name = name, kind = PlaceKind.RESTAURANT, tags = tags, photos = listOf("$id.webp"), updatedAt = updatedAt)

    private fun visit(id: String, placeId: String, photos: List<String> = emptyList()) =
        Visit(id = id, placeId = placeId, at = 100L, photos = photos)

    private val local = EatsData(
        places = listOf(place("pl1", tags = listOf("辣")), place("pl2")),
        visits = listOf(visit("v1", "pl1")),
    )

    @Test
    fun `合并：包内同 id 整体覆盖、新实体追加、本地独有保留`() {
        val incoming = EatsData(
            places = listOf(
                place("pl1", tags = listOf("辣", "重油")), // 覆盖
                place("pl9"),                              // 新增
            ),
            visits = listOf(visit("v1", "pl1"), visit("v8", "pl9")),
        )
        val merged = mergeEats(local, incoming)
        assertEquals(listOf("pl1", "pl2", "pl9"), merged.places.map { it.id })
        assertEquals(listOf("辣", "重油"), merged.places.first { it.id == "pl1" }.tags)
        assertTrue(merged.places.any { it.id == "pl2" })
        assertEquals(2, merged.visits.size)
    }

    @Test
    fun `diff 计数与覆盖警示`() {
        val incoming = EatsData(
            places = listOf(place("pl1", tags = listOf("辣", "重油"), updatedAt = 50), place("pl2"), place("pl9")),
            visits = listOf(visit("v1", "pl1")),
        )
        val d = diffEats(local, incoming, exportedAt = 100L)
        assertEquals(1, d.places.added)
        assertEquals(1, d.places.updated)
        assertEquals(1, d.places.unchanged)
        assertEquals(1, d.visits.totalAfter) // v1 内容相同 → 不变

        // 本地 updatedAt 晚于导出且将被覆盖 → 警示计数
        val localNewer = local.copy(places = local.places.map { if (it.id == "pl1") it.copy(updatedAt = 120) else it })
        assertEquals(1, diffEats(localNewer, incoming, exportedAt = 100L).locallyNewer)
    }

    @Test
    fun `eats 特有校验：PLAY 加 TAKEOUT、rating 越界、空链接`() {
        val bad = EatsData(
            places = listOf(
                place("a").copy(category = PlaceCategory.PLAY, kind = PlaceKind.TAKEOUT),
                place("b").copy(rating = 9),
                place("c").copy(links = listOf(PlaceLink("  "))),
            ),
        )
        val reasons = validateEatsData(bad)
        assertEquals(3, reasons.size)
        assertTrue(reasons.any { "无效组合" in it })
        assertTrue(reasons.any { "评分" in it })
        assertTrue(reasons.any { "空链接" in it })
        // 合法数据零问题
        assertTrue(validateEatsData(local).isEmpty())
        assertTrue(validateEatsData(local.copy(places = local.places.map { it.copy(category = PlaceCategory.PLAY) })).isEmpty())
    }

    @Test
    fun `referencedImages 与 remapImageRefs 覆盖 places 与 visits 的 photos`() {
        val data = EatsData(
            places = listOf(place("pl1").copy(photos = listOf("a.jpg", "b.jpg"))),
            visits = listOf(visit("v1", "pl1", photos = listOf("a.jpg", "c.jpg"))),
        )
        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), data.referencedImages())
        val remapped = data.remapImageRefs { "u-$it" }
        assertEquals(listOf("u-a.jpg", "u-b.jpg"), remapped.places[0].photos)
        assertEquals(listOf("u-a.jpg", "u-c.jpg"), remapped.visits[0].photos)
    }
}
