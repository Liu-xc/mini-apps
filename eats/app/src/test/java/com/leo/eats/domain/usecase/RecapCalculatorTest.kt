package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.Visit
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RecapCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now: Long =
        LocalDate.of(2026, 9, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12): Long =
        LocalDate.of(y, m, d).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun data(places: List<Place>, visits: List<Visit>) = EatsData(places = places, visits = visits)

    private fun place(id: String, kind: PlaceKind = PlaceKind.RESTAURANT, name: String = id, rating: Int? = null) =
        Place(id = id, name = name, kind = kind, rating = rating)

    @Test
    fun `year range excludes visits of other years`() {
        val d = data(
            places = listOf(place("p1")),
            visits = listOf(
                Visit("v1", "p1", at(2026, 3, 1)),
                Visit("v2", "p1", at(2025, 3, 1)),
            ),
        )
        val stats = d.recap(RecapRange.Year(2026), now, zone)
        assertEquals(1, stats.totalVisits)
        assertEquals(2, d.recap(RecapRange.All, now, zone).totalVisits)
    }

    @Test
    fun `totals cost null when no cost recorded`() {
        val d = data(
            places = listOf(place("p1")),
            visits = listOf(Visit("v1", "p1", at(2026, 3, 1), cost = 12.5), Visit("v2", "p1", at(2026, 4, 1))),
        )
        val stats = d.recap(RecapRange.Year(2026), now, zone)
        assertEquals(12.5, stats.totalCost!!, 1e-6)
        val noCost = d.recap(RecapRange.Year(2025), now, zone)
        assertEquals(0, noCost.totalVisits)
        assertNull(noCost.totalCost)
        assertFalse(noCost.hasData)
    }

    @Test
    fun `top places ordered by count then rating then name`() {
        val d = data(
            places = listOf(place("b"), place("a2"), place("a1"), place("zero")),
            visits = listOf(
                Visit("v1", "a1", at(2026, 2, 1), 5),
                Visit("v2", "a2", at(2026, 2, 2), 3),
                Visit("v3", "b", at(2026, 2, 3), 4),
                Visit("v4", "b", at(2026, 3, 3), 4),
                Visit("v5", "b", at(2026, 4, 3), 5),
            ),
        )
        val tops = d.recap(RecapRange.Year(2026), now, zone).topPlaces
        assertEquals(listOf("b", "a1", "a2"), tops.map { it.place.id })
    }

    @Test
    fun `kind counts by place kind include zero keys`() {
        val d = data(
            places = listOf(place("r", PlaceKind.RESTAURANT), place("t", PlaceKind.TAKEOUT)),
            visits = listOf(Visit("v1", "r", at(2026, 2, 1)), Visit("v2", "t", at(2026, 2, 1))),
        )
        val kinds = d.recap(RecapRange.Year(2026), now, zone).kindCounts
        assertEquals(1, kinds[PlaceKind.RESTAURANT])
        assertEquals(1, kinds[PlaceKind.TAKEOUT])
        assertEquals(0, kinds[PlaceKind.HOME])
    }

    @Test
    fun `monthly buckets aligned to year`() {
        val d = data(
            places = listOf(place("p1")),
            visits = listOf(Visit("v1", "p1", at(2026, 1, 2)), Visit("v2", "p1", at(2026, 12, 30))),
        )
        val months = d.recap(RecapRange.Year(2026), now, zone).monthly
        assertEquals(12, months.size)
        assertEquals(1, months[0].count)
        assertEquals(0, months[10].count)
        assertEquals(1, months[11].count)
        assertEquals("1月", months[0].label)
    }

    @Test
    fun `all mode monthly is rolling 12 months ending current`() {
        val d = data(
            places = listOf(place("p1")),
            visits = listOf(Visit("v1", "p1", at(2026, 9, 1))),
        )
        val months = d.recap(RecapRange.All, now, zone).monthly
        assertEquals(12, months.size)
        assertEquals(1, months[11].count)
        assertEquals(0, months[0].count)
    }

    @Test
    fun `streak counts back from today or yesterday`() {
        val d = data(
            places = listOf(place("p1")),
            visits = listOf(
                Visit("v1", "p1", now),
                Visit("v2", "p1", now - RecapCalculatorDays(1)),
                Visit("v3", "p1", now - RecapCalculatorDays(2)),
                Visit("v4", "p1", now - RecapCalculatorDays(5)),
            ),
        )
        assertEquals(3, d.recap(RecapRange.All, now, zone).currentStreak)
    }

    private fun RecapCalculatorDays(n: Int): Long = n * 86_400_000L

    @Test
    fun `photos flatten chronological capped at 9`() {
        val visits = (1..12).map { i ->
            Visit("v$i", "p1", at(2026, 1, i.coerceIn(1, 28)), photos = listOf("p$i.webp"))
        }
        val d = data(places = listOf(place("p1")), visits = visits)
        assertEquals(9, d.recap(RecapRange.Year(2026), now, zone).photoFiles.size)
    }

    @Test
    fun `empty data yields zeros safely`() {
        val stats = EatsData().recap(RecapRange.Year(2026), now, zone)
        assertEquals(0, stats.totalVisits)
        assertNull(stats.avgRating)
        assertNull(stats.totalCost)
        assertEquals(0, stats.currentStreak)
        assertEquals(12, stats.monthly.size)
    }
}
