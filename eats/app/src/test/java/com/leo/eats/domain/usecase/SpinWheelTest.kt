package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpinWheelTest {

    private val wheel = SpinWheel()
    private val now = 1_000_000_000_000L

    @Test
    fun `权重公式 - 从未吃过按30天 今天吃过最低`() {
        assertEquals(31.0, wheel.weightOf(null, now), 1e-9)
        assertEquals(1.0, wheel.weightOf(now, now), 1e-9)
        assertEquals(8.0, wheel.weightOf(now - 7 * SpinWheel.DAY_MILLIS, now), 1e-9)
    }

    @Test
    fun `权重抽取分布 - 越久没吃越容易中`() {
        val today = PlaceWithStats(Place("today", "今天", PlaceKind.RESTAURANT), lastVisitAt = now)
        val never = PlaceWithStats(Place("never", "从未", PlaceKind.RESTAURANT), lastVisitAt = null)
        // 权重 1 vs 31 → 从未吃过的应被抽中约 96.9%
        val random = Random(42)
        var neverWins = 0
        repeat(10_000) {
            val w = wheel.pickWeighted(listOf(today, never), { wheel.weightOf(it.lastVisitAt, now) }, random)
            if (w.place.id == "never") neverWins++
        }
        assertTrue("从未吃过的胜率应 > 93%，实际 $neverWins/10000", neverWins > 9_300)
    }

    @Test
    fun `plan 参数在约定区间`() {
        val candidates = (1..5).map {
            PlaceWithStats(Place("p$it", "p$it", PlaceKind.RESTAURANT), lastVisitAt = null)
        }
        repeat(50) {
            val plan = wheel.plan(candidates, now, Random(it))
            assertTrue(plan.turns in 4..6)
            assertTrue(plan.durationMillis in 2600 until 3600)
            assertTrue(plan.offsetDegrees in -3f..3f)
            assertTrue(candidates.any { it.place.id == plan.winner.place.id })
        }
    }

    @Test
    fun `权重抽取覆盖全部候选`() {
        val a = PlaceWithStats(Place("a", "a", PlaceKind.RESTAURANT), lastVisitAt = now)
        val b = PlaceWithStats(Place("b", "b", PlaceKind.RESTAURANT), lastVisitAt = null)
        val picks = mutableSetOf<String>()
        repeat(500) { seed ->
            picks += wheel.pickWeighted(listOf(a, b), { wheel.weightOf(it.lastVisitAt, now) }, Random(seed)).place.id
        }
        assertEquals(setOf("a", "b"), picks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空候选抛异常`() {
        wheel.pickWeighted(emptyList<PlaceWithStats>(), { 1.0 })
    }
}
