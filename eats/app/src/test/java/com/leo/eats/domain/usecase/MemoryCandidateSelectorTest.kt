package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class MemoryCandidateSelectorTest {

    private val now = 1_800_000_000_000L
    private val day = MemoryCandidateSelector.DAY_MS

    private fun s(
        id: String,
        visits: Int = 3,
        rating: Int? = 4,
        avg: Double? = null,
        lastVisit: Long? = now,
    ) = PlaceWithStats(
        place = Place(id = id, name = id, kind = PlaceKind.RESTAURANT, rating = rating),
        lastVisitAt = lastVisit,
        visitCount = visits,
        avgVisitRating = avg,
    )

    @Test
    fun `filters by visits rating fallback and days`() {
        val list = listOf(
            s("young", visits = 5, lastVisit = now - 10 * day),          // 天数不够
            s("lowcount", visits = 2, lastVisit = now - 100 * day),      // 次数不够
            s("lowrating", visits = 5, rating = 2, lastVisit = now - 100 * day),
            s("fallback-ok", visits = 5, rating = null, avg = 4.2, lastVisit = now - 100 * day),
            s("fallback-low", visits = 5, rating = null, avg = 3.9, lastVisit = now - 100 * day),
            s("ok", visits = 3, rating = 4, lastVisit = now - 90 * day), // 恰好 90 天（>=）
        )
        val result = MemoryCandidateSelector.candidates(list, days = 90, now = now)
        assertEquals(listOf("fallback-ok", "ok"), result.map { it.place.id })
    }

    @Test
    fun `exactly threshold days qualifies`() {
        val result = MemoryCandidateSelector.candidates(
            listOf(s("edge", visits = 3, lastVisit = now - 90 * day)),
            days = 90,
            now = now,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `pick avoids last notified when alternatives exist`() {
        val candidates = listOf(s("a"), s("b"))
        val random = Random(42)
        repeat(20) {
            val pick = MemoryCandidateSelector.pick(candidates, "a", random)!!
            assertEquals("b", pick.place.id)
        }
    }

    @Test
    fun `pick falls back to last notified when it is the only candidate`() {
        val candidates = listOf(s("a"))
        assertEquals("a", MemoryCandidateSelector.pick(candidates, "a", Random(1))!!.place.id)
    }

    @Test
    fun `pick returns null on empty candidates`() {
        assertNull(MemoryCandidateSelector.pick(emptyList(), null, Random(1)))
    }
}
