package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildCandidatesTest {

    private val now = 1_000L * 24 * 60 * 60 * 1000 // 第 1000 天

    private fun stats(
        id: String,
        kind: PlaceKind = PlaceKind.RESTAURANT,
        tags: List<String> = emptyList(),
        lastVisitAt: Long? = null,
    ) = PlaceWithStats(Place(id = id, name = id, kind = kind, tags = tags), lastVisitAt = lastVisitAt)

    private val all = listOf(
        stats("堂食-吃过", lastVisitAt = now - 3 * BuildCandidates.DAY_MILLIS),
        stats("外卖-今天", kind = PlaceKind.TAKEOUT, lastVisitAt = now),
        stats("自做-没吃过", kind = PlaceKind.HOME),
        stats("忌口辣", tags = listOf("辣", "火锅")),
    )

    @Test
    fun `默认过滤 - 全类型 且 排除最近14天`() {
        val out = BuildCandidates()(all, SpinFilter(), now)
        // 外卖今天吃过被排除；堂食 3 天前也被排除（<14 天）；自做没吃过保留；忌口不带排除标签保留
        assertEquals(listOf("自做-没吃过", "忌口辣"), out.map { it.place.id })
    }

    @Test
    fun `类型过滤`() {
        val out = BuildCandidates()(all, SpinFilter(kinds = setOf(PlaceKind.TAKEOUT), excludeRecentDays = null), now)
        assertEquals(listOf("外卖-今天"), out.map { it.place.id })
    }

    @Test
    fun `忌口标签排除`() {
        val out = BuildCandidates()(all, SpinFilter(excludedTags = setOf("辣")), now)
        assertTrue(out.none { it.place.id == "忌口辣" })
    }

    @Test
    fun `关闭最近排除后 吃过的也进候选`() {
        val out = BuildCandidates()(all, SpinFilter(excludeRecentDays = null), now)
        assertEquals(4, out.size)
    }

    @Test
    fun `最近 N 天边界 - 恰好 N 天前算最近`() {
        val edge = listOf(stats("edge", lastVisitAt = now - 14 * BuildCandidates.DAY_MILLIS))
        assertEquals(0, BuildCandidates()(edge, SpinFilter(excludeRecentDays = 14), now).size)
        assertEquals(1, BuildCandidates()(edge, SpinFilter(excludeRecentDays = 13), now).size)
    }
}
