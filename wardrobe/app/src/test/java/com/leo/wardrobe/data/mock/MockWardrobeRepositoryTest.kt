package com.leo.wardrobe.data.mock

import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.WardrobeCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockWardrobeRepositoryTest {

    @Test
    fun `种子覆盖两角色与八品类`() = runTest {
        val repo = MockWardrobeRepository()
        val d = repo.data.value
        assertEquals(2, d.persons.size)
        assertEquals(17, d.items.size)
        assertEquals(WardrobeCategory.entries.toSet(), d.items.map { it.category }.toSet())
        assertEquals(5, d.outfits.size)
        assertEquals(3, d.notes.size)
        assertTrue("组合 itemIds 应归属本人衣物", d.outfits.all { o ->
            o.itemIds.all { id -> d.items.any { it.id == id && it.personId == o.personId } }
        })
    }

    @Test
    fun `删除单品级联 - 从组合移除 删相关笔记`() = runTest {
        val repo = MockWardrobeRepository()
        repo.deleteItem("it1")
        val d = repo.data.value
        assertTrue(d.items.none { it.id == "it1" })
        assertTrue(d.outfits.none { o -> "it1" in o.itemIds })
        assertTrue(d.notes.none { it.parentType == NoteParent.ITEM && it.parentId == "it1" })
        // 组合未删，其笔记保留
        assertTrue(d.notes.any { it.parentType == NoteParent.OUTFIT && it.parentId == "o1" })
    }

    @Test
    fun `删除角色级联其全部衣物组合与笔记`() = runTest {
        val repo = MockWardrobeRepository()
        repo.deletePerson("p2")
        val d = repo.data.value
        assertEquals(1, d.persons.size)
        assertEquals(12, d.items.size)
        assertTrue(d.outfits.none { it.personId == "p2" })
        // 只剩 p1 的数据；指向已删数据的笔记一并清掉
        assertTrue(d.notes.all { n ->
            when (n.parentType) {
                NoteParent.ITEM -> d.items.any { it.id == n.parentId }
                NoteParent.OUTFIT -> d.outfits.any { it.id == n.parentId }
            }
        })
    }

    @Test
    fun `addNote 与 deleteNote`() = runTest {
        val repo = MockWardrobeRepository()
        val n = repo.addNote(NoteParent.ITEM, "it2", "测试笔记")
        assertTrue(repo.data.value.notes.any { it.id == n.id && it.text == "测试笔记" })
        repo.deleteNote(n.id)
        assertTrue(repo.data.value.notes.none { it.id == n.id })
    }

    @Test
    fun `createOutfit 过滤非本人衣物`() = runTest {
        val repo = MockWardrobeRepository()
        val o = repo.createOutfit("p1", listOf("it1", "it13"), emptyList())
        assertEquals(listOf("it1"), o.itemIds)
    }
}
