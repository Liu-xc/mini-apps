package com.leo.wardrobe.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 组合去重查询测试（it-004，specs/03 不变量的查询侧） */
class QueriesTest {

    private fun data(vararg outfits: Outfit) = WardrobeData(
        persons = listOf(Person("p1", "我"), Person("p2", "老婆")),
        outfits = outfits.toList(),
    )

    @Test
    fun sameSetDifferentOrderMatches() {
        val d = data(Outfit("o1", "p1", itemIds = listOf("a", "b", "c")))
        assertEquals("o1", d.outfitWithItems("p1", listOf("c", "a", "b"))?.id)
    }

    @Test
    fun extraOrMissingItemDoesNotMatch() {
        val d = data(Outfit("o1", "p1", itemIds = listOf("a", "b")))
        assertNull(d.outfitWithItems("p1", listOf("a", "b", "c")))
        assertNull(d.outfitWithItems("p1", listOf("a")))
    }

    @Test
    fun scopedToPerson() {
        val d = data(Outfit("o1", "p1", itemIds = listOf("a", "b")))
        assertNull(d.outfitWithItems("p2", listOf("a", "b")))
    }

    @Test
    fun multipleMatchesTakesLatest() {
        val d = data(
            Outfit("old", "p1", itemIds = listOf("a"), updatedAt = 1),
            Outfit("new", "p1", itemIds = listOf("a"), updatedAt = 2),
        )
        assertEquals("new", d.outfitWithItems("p1", listOf("a"))?.id)
    }
}
