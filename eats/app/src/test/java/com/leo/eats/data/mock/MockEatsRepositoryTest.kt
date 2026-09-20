package com.leo.eats.data.mock

import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.Visit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEatsRepositoryTest {

    @Test
    fun `种子丰富且三类全覆盖`() = runTest {
        val repo = MockEatsRepository()
        val d = repo.data.value
        assertTrue("演示数据应 ≥10 家，实际 ${d.places.size}", d.places.size >= 10)
        assertEquals(setOf(PlaceKind.RESTAURANT, PlaceKind.TAKEOUT, PlaceKind.HOME), d.places.map { it.kind }.toSet())
        assertTrue("应有带链接的食堂", d.places.any { it.links.isNotEmpty() })
        assertTrue("应有带坐标的食堂", d.places.any { it.located })
        assertTrue("应有带评分的食堂", d.places.any { it.rating != null })
        assertTrue("Visit 应分布在近两个月", d.visits.size >= 15)
        assertNull(d.visits[0].photos.firstOrNull())
    }

    @Test
    fun `upsert 归一化与真实仓库一致`() = runTest {
        val repo = MockEatsRepository()
        repo.upsertPlace(
            Place(id = "pl1", name = "  巷子深火锅  ", kind = PlaceKind.RESTAURANT, tags = listOf("辣", "辣")),
        )
        val p = repo.data.value.places.first { it.id == "pl1" }
        assertEquals("巷子深火锅", p.name)
        assertEquals(listOf("辣"), p.tags)
    }

    @Test
    fun `删除食堂级联删除 Visit`() = runTest {
        val repo = MockEatsRepository()
        repo.upsertPlace(Place(id = "px", name = "临时", kind = PlaceKind.HOME))
        repo.addVisit(Visit(id = "vx", placeId = "px", at = 1L))
        repo.deletePlace("px")
        assertTrue(repo.data.value.places.none { it.id == "px" })
        assertTrue(repo.data.value.visits.none { it.placeId == "px" })
    }

    @Test
    fun `addVisit 空id补齐`() = runTest {
        val repo = MockEatsRepository()
        val before = repo.data.value.visits.size
        repo.addVisit(Visit(id = "", placeId = "pl1", at = 5L))
        val added = repo.data.value.visits.last()
        assertEquals(before + 1, repo.data.value.visits.size)
        assertTrue(added.id.isNotBlank())
    }
}
