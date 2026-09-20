package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.WearLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleItemSelectorTest {

    private fun outfit(id: String, personId: String = "p1", itemIds: List<String>) =
        Outfit(id = id, personId = personId, itemIds = itemIds)
    private fun item(id: String, personId: String = "p1") =
        Item(id = id, personId = personId, category = com.leo.wardrobe.domain.model.WardrobeCategory.TOP, name = id, imageFile = "f")

    private val items = listOf(item("it1"), item("it2"), item("it3", "p2"))
    private val outfits = listOf(outfit("o1", itemIds = listOf("it1", "it2")), outfit("o2", itemIds = listOf("it1")))

    private fun log(outfitId: String, at: Long) = WearLog(id = "w-$outfitId-$at", personId = "p1", outfitId = outfitId, at = at)

    @Test
    fun `聚合 - 同一单品跨多套打卡合并取最晚`() {
        val stats = StaleItemSelector.aggregateWearStats(
            items, outfits,
            listOf(log("o1", 100), log("o2", 300), log("o1", 200)),
        )
        val it1 = stats.first { it.itemId == "it1" }
        assertEquals(3, it1.wearCount)
        assertEquals(300, it1.lastWornAt)
    }

    @Test
    fun `聚合 - 悬空打卡与不含该单品的打卡被忽略`() {
        val stats = StaleItemSelector.aggregateWearStats(
            items, outfits,
            listOf(log("gone", 100), log("o2", 100)),
        )
        // o1 不在列表 → 无 it1 打卡；o2 只含 it1
        assertTrue(stats.none { it.itemId == "it2" })
        assertEquals(1, stats.first { it.itemId == "it1" }.wearCount)
    }

    @Test
    fun `候选 - 恰好 N 天入选（含边界）`() {
        val now = 1_800_000_000_000L
        val stats = listOf(
            ItemWearStat("it1", "p1", wearCount = 2, lastWornAt = now - 90 * StaleItemSelector.DAY_MS),
            ItemWearStat("it2", "p1", wearCount = 2, lastWornAt = now - 90 * StaleItemSelector.DAY_MS - 1),
            ItemWearStat("it3", "p1", wearCount = 2, lastWornAt = now - 90 * StaleItemSelector.DAY_MS + 1),
        )
        val picked = StaleItemSelector.candidates(stats, mapOf("it1" to "a", "it2" to "b", "it3" to "c"), days = 90, now = now)
        // >= 语义：恰好 90 天与超过 90 天入选，差 1ms 的 89.99… 天排除
        assertEquals(listOf("it1", "it2"), picked.map { it.itemId })
    }

    @Test
    fun `候选 - 次数不足 无名字 lastWorn 零 均排除`() {
        val now = 10L * StaleItemSelector.DAY_MS
        val stats = listOf(
            ItemWearStat("a", "p1", wearCount = 1, lastWornAt = 0),          // 次数不足
            ItemWearStat("b", "p1", wearCount = 5, lastWornAt = 0),          // lastWornAt=0
            ItemWearStat("c", "p1", wearCount = 5, lastWornAt = now - 30 * StaleItemSelector.DAY_MS), // 名字缺失
        )
        assertTrue(StaleItemSelector.candidates(stats, mapOf("a" to "x", "b" to "y"), days = 30, now = now).isEmpty())
    }
}
