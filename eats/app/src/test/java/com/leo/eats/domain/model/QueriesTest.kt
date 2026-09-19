package com.leo.eats.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueriesTest {

    private val data = EatsData(
        places = listOf(
            Place(id = "p1", name = "火锅店", kind = PlaceKind.RESTAURANT),
            Place(id = "p2", name = "外卖店", kind = PlaceKind.TAKEOUT),
        ),
        visits = listOf(
            Visit(id = "v1", placeId = "p1", at = 300, rating = 5),
            Visit(id = "v2", placeId = "p1", at = 100, rating = 3),
            Visit(id = "v3", placeId = "p1", at = 200, rating = null),
        ),
    )

    @Test
    fun `statsOf - 派生最近一次 次数 平均分`() {
        val s = data.statsOf("p1")
        assertEquals(300L, s.lastVisitAt)
        assertEquals(3, s.visitCount)
        assertEquals(4.0, s.avgVisitRating!!, 1e-9)
    }

    @Test
    fun `statsOf - 没吃过的食堂`() {
        val s = data.statsOf("p2")
        assertNull(s.lastVisitAt)
        assertEquals(0, s.visitCount)
        assertNull(s.avgVisitRating)
    }

    @Test
    fun `visitsOf - 按时间倒序`() {
        assertEquals(listOf("v1", "v3", "v2"), data.visitsOf("p1").map { it.id })
    }

    @Test
    fun `statsOfAll - 一次遍历 与单查一致`() {
        val all = data.statsOfAll().associateBy { it.place.id }
        assertEquals(data.statsOf("p1"), all["p1"])
        assertEquals(data.statsOf("p2"), all["p2"])
    }
}
