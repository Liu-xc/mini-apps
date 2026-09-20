package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.domain.model.kindOptions
import com.leo.eats.domain.model.labelIn
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

    // ---- it-008：分类过滤 + 只抽愿望 ----

    private fun catStats(
        id: String,
        category: PlaceCategory,
        kind: PlaceKind = PlaceKind.RESTAURANT,
        wishlistedAt: Long? = null,
    ) = PlaceWithStats(Place(id = id, name = id, kind = kind, category = category, wishlistedAt = wishlistedAt))

    private val mixed = listOf(
        catStats("吃-堂食", PlaceCategory.EAT),
        catStats("喝-外送", PlaceCategory.DRINK, kind = PlaceKind.TAKEOUT),
        catStats("玩-出门", PlaceCategory.PLAY),
        catStats("玩-在家", PlaceCategory.PLAY, kind = PlaceKind.HOME),
        catStats("愿望-喝", PlaceCategory.DRINK, wishlistedAt = now - BuildCandidates.DAY_MILLIS),
        catStats("愿望-玩", PlaceCategory.PLAY, wishlistedAt = now - 5 * BuildCandidates.DAY_MILLIS),
    )

    @Test
    fun `分类过滤 - 只抽玩`() {
        val out = BuildCandidates()(mixed, SpinFilter(categories = setOf(PlaceCategory.PLAY), excludeRecentDays = null), now)
        assertEquals(listOf("玩-出门", "玩-在家", "愿望-玩"), out.map { it.place.id })
    }

    @Test
    fun `分类过滤 - 吃喝两组`() {
        val out = BuildCandidates()(
            mixed,
            SpinFilter(categories = setOf(PlaceCategory.EAT, PlaceCategory.DRINK), excludeRecentDays = null),
            now,
        )
        assertEquals(listOf("吃-堂食", "喝-外送", "愿望-喝"), out.map { it.place.id })
    }

    @Test
    fun `只抽愿望 - 仅种草条目进池`() {
        val out = BuildCandidates()(mixed, SpinFilter(wishOnly = true, excludeRecentDays = null), now)
        assertEquals(listOf("愿望-喝", "愿望-玩"), out.map { it.place.id })
    }

    @Test
    fun `只抽愿望 与 分类过滤可组合`() {
        val out = BuildCandidates()(
            mixed,
            SpinFilter(categories = setOf(PlaceCategory.PLAY), wishOnly = true, excludeRecentDays = null),
            now,
        )
        assertEquals(listOf("愿望-玩"), out.map { it.place.id })
    }

    @Test
    fun `只抽愿望时 排除最近不误伤 - 愿望均无记录`() {
        val out = BuildCandidates()(mixed, SpinFilter(wishOnly = true, excludeRecentDays = 14), now)
        assertEquals(2, out.size)
    }

    @Test
    fun `kindOptions - 玩类不含外送 其余三类齐全`() {
        assertEquals(PlaceKind.entries.toList(), PlaceCategory.EAT.kindOptions)
        assertEquals(PlaceKind.entries.toList(), PlaceCategory.DRINK.kindOptions)
        assertEquals(listOf(PlaceKind.RESTAURANT, PlaceKind.HOME), PlaceCategory.PLAY.kindOptions)
    }

    @Test
    fun `labelIn - kind 文案按分类适配`() {
        assertEquals("堂食", PlaceKind.RESTAURANT.labelIn(PlaceCategory.EAT))
        assertEquals("外送", PlaceKind.TAKEOUT.labelIn(PlaceCategory.DRINK))
        assertEquals("在家", PlaceKind.HOME.labelIn(PlaceCategory.PLAY))
        assertEquals("出门", PlaceKind.RESTAURANT.labelIn(PlaceCategory.PLAY))
    }
}
