package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WardrobeRecapCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now: Long =
        LocalDate.of(2026, 9, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun day(n: Int): Long = n * 86_400_000L

    private fun data(vararg persons: Person, items: List<Item> = emptyList(), outfits: List<Outfit> = emptyList(), wearLogs: List<WearLog> = emptyList()) =
        WardrobeData(persons = persons.toList(), items = items, outfits = outfits, wearLogs = wearLogs)

    private val leo = Person("p1", "Leo", createdAt = 0L)

    private fun item(id: String, category: WardrobeCategory = WardrobeCategory.TOP, createdAgo: Long = 1000L) =
        Item(id = id, personId = "p1", category = category, name = id, imageFile = "$id.webp", createdAt = createdAgo)

    private fun outfit(id: String, vararg itemIds: String) =
        Outfit(id = id, personId = "p1", itemIds = itemIds.toList())

    private fun wear(id: String, outfitId: String, at: Long) = WearLog(id, "p1", outfitId, at, at)

    @Test
    fun `counts are structural except wear metrics`() {
        val d = data(leo,
            items = listOf(item("a"), item("b"), item("c")),
            outfits = listOf(outfit("o1", "a", "b")),
            wearLogs = listOf(wear("w1", "o1", at(2026, 3, 1)), wear("w2", "o1", at(2025, 3, 1))),
        )
        val stats = d.wardrobeRecap("p1", WardrobeRecapRange.Year(2026), now, zone)
        assertEquals(3, stats.itemCount)
        assertEquals(1, stats.outfitCount)
        assertEquals(1, stats.wearCount) // 档内
        assertEquals(2, d.wardrobeRecap("p1", WardrobeRecapRange.All, now, zone).wearCount)
    }

    @Test
    fun `versatile ranks by outfit count then wear`() {
        val d = data(leo,
            items = listOf(item("a"), item("b"), item("c"), item("d")),
            outfits = listOf(outfit("o1", "a", "d"), outfit("o2", "a", "d"), outfit("o3", "b")),
            wearLogs = listOf(
                wear("w1", "o1", at(2026, 2, 1)), wear("w2", "o1", at(2026, 2, 2)),
                wear("w3", "o3", at(2026, 2, 3)), wear("w4", "o3", at(2026, 2, 4)),
                wear("w5", "o3", at(2026, 2, 5)),
            ),
        )
        val tops = d.wardrobeRecap("p1", WardrobeRecapRange.Year(2026), now, zone).topVersatile
        // a/d 均为 2 套 2 穿（并列按名称序），b 为 1 套 3 穿 → a, d, b
        assertEquals(listOf("a", "d", "b"), tops.map { it.item.id })
        assertEquals(2, tops[0].outfitCount)
        assertEquals(2, tops[0].wearCount)
    }

    @Test
    fun `idle items are those never worn via any outfit`() {
        val d = data(leo,
            items = listOf(item("a"), item("b"), item("c")),
            outfits = listOf(outfit("o1", "a", "b"), outfit("o2", "c")),
            wearLogs = listOf(wear("w1", "o1", at(2026, 2, 1))),
        )
        val stats = d.wardrobeRecap("p1", WardrobeRecapRange.Year(2026), now, zone)
        assertEquals(listOf("c"), stats.idleItems.map { it.id })
        assertEquals(2.0 / 3.0, stats.utilization, 1e-9)
    }

    @Test
    fun `idle sorted by owned time ascending`() {
        val d = data(leo,
            items = listOf(item("boughtRecently", createdAgo = 900L), item("boughtLongAgo", createdAgo = 100L)),
            wearLogs = emptyList(),
        )
        // createdAt 升序 = 最早买入的排最前
        assertEquals(
            listOf("boughtLongAgo", "boughtRecently"),
            d.wardrobeRecap("p1", WardrobeRecapRange.All, now, zone).idleItems.map { it.id },
        )
    }

    @Test
    fun `top outfit picked from range logs with last worn`() {
        val d = data(leo,
            items = listOf(item("a")),
            outfits = listOf(outfit("o1", "a"), outfit("o2", "a")),
            wearLogs = listOf(
                wear("w1", "o1", at(2026, 2, 1)), wear("w2", "o1", at(2026, 3, 1)),
                wear("w3", "o2", at(2026, 8, 1)),
                wear("w4", "o2", at(2025, 8, 1)),
            ),
        )
        val top = d.wardrobeRecap("p1", WardrobeRecapRange.Year(2026), now, zone).topOutfit
        assertEquals("o1", top!!.outfit.id)
        assertEquals(2, top.wearCount)
        assertEquals(at(2026, 3, 1), top.lastWornAt)
        // 2025 档内只有 w4（o2 一次）
        assertEquals("o2", d.wardrobeRecap("p1", WardrobeRecapRange.Year(2025), now, zone).topOutfit!!.outfit.id)
        assertNull(d.wardrobeRecap("p1", WardrobeRecapRange.Year(2024), now, zone).topOutfit)
    }

    @Test
    fun `streak counts back from today or yesterday`() {
        val d = data(leo,
            items = listOf(item("a")),
            outfits = listOf(outfit("o1", "a")),
            wearLogs = listOf(
                wear("w1", "o1", now), wear("w2", "o1", now - day(1)),
                wear("w3", "o1", now - day(2)), wear("w4", "o1", now - day(6)),
            ),
        )
        assertEquals(3, d.wardrobeRecap("p1", WardrobeRecapRange.All, now, zone).currentStreak)
    }

    @Test
    fun `photos dedup by file capped at 9 in range`() {
        val outfit = Outfit(
            id = "o1", personId = "p1", itemIds = listOf("a"),
            effectImages = (1..12).map { com.leo.wardrobe.domain.model.OutfitImage("p$it.webp", at(2026, 1, it.coerceIn(1, 28))) },
        )
        val d = data(leo,
            items = listOf(item("a")),
            outfits = listOf(outfit),
            wearLogs = (1..12).map { wear("w$it", "o1", at(2026, 2, it.coerceIn(1, 28))) } +
                wear("w99", "o1", at(2024, 2, 1)),
        )
        assertEquals(9, d.wardrobeRecap("p1", WardrobeRecapRange.Year(2026), now, zone).photoFiles.size)
    }

    @Test
    fun `person isolation and missing person`() {
        val other = Person("p2", "小满", createdAt = 0L)
        val d = data(leo, other,
            items = listOf(item("a")),
            outfits = listOf(outfit("o1", "a")),
            wearLogs = listOf(wear("w1", "o1", at(2026, 2, 1))),
        )
        assertEquals(0, d.wardrobeRecap("p2", WardrobeRecapRange.All, now, zone).wearCount)
        assertEquals("", d.wardrobeRecap("ghost", WardrobeRecapRange.All, now, zone).personName)
    }
}
