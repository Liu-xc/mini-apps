package com.leo.wardrobe.data.repo

import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 数据不变量测试（specs/03-data-model.md 末节） */
class WardrobeRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val images = FakeImageStore()

    private fun newStore() = SnapshotStore(
        dir = tmp.root,
        fileName = "wardrobe.json",
        serializer = WardrobeData.serializer(),
        default = { WardrobeData() },
    )

    private fun repo() = WardrobeRepositoryImpl(newStore(), images)

    @Test
    fun defaultPersonCreatedOnce() = runTest {
        val r = repo()
        val first = r.ensureDefaultPerson()
        val second = r.ensureDefaultPerson()
        assertEquals(first.id, second.id)
        assertEquals(1, r.data.value.persons.size)
    }

    @Test
    fun deleteItemCleansOutfitRefsAndImage() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        val shirt = Item("i1", p.id, WardrobeCategory.TOP, "白衬衫", imageFile = "shirt.webp")
        val jeans = Item("i2", p.id, WardrobeCategory.BOTTOM, "牛仔裤", imageFile = "jeans.webp")
        r.upsertItem(shirt)
        r.upsertItem(jeans)
        val outfit = r.createOutfit(p.id, listOf("i1", "i2"))
        r.addNote(NoteParent.ITEM, "i1", "领口易皱")

        r.deleteItem("i1")

        val d = r.data.value
        assertTrue(d.items.none { it.id == "i1" })
        assertEquals(listOf("i2"), d.outfits.first { it.id == outfit.id }.itemIds)
        assertTrue(d.notes.isEmpty())
        assertEquals(listOf("shirt.webp"), images.deleted)
    }

    @Test
    fun deletePersonCascadesEverythingButKeepsOthers() = runTest {
        val r = repo()
        val me = r.ensureDefaultPerson()
        val wife = r.addPerson("老婆", "👩")
        val myItem = Item("i1", me.id, WardrobeCategory.TOP, "白衬衫", imageFile = "a.webp")
        val herItem = Item("i2", wife.id, WardrobeCategory.DRESS, "连衣裙", imageFile = "b.webp")
        r.upsertItem(myItem)
        r.upsertItem(herItem)
        val myOutfit = r.createOutfit(me.id, listOf("i1"))
        r.addEffectImage(myOutfit.id, "effect.webp")
        r.addNote(NoteParent.OUTFIT, myOutfit.id, "被夸了")
        val herOutfit = r.createOutfit(wife.id, listOf("i2"))

        r.deletePerson(me.id)

        val d = r.data.value
        assertEquals(listOf(wife.id), d.persons.map { it.id })
        assertEquals(listOf("i2"), d.items.map { it.id })
        assertEquals(listOf(herOutfit.id), d.outfits.map { it.id })
        assertTrue(d.notes.isEmpty())
        // 单品图 + 成品图都物理删除
        assertEquals(setOf("a.webp", "effect.webp"), images.deleted.toSet())
    }

    @Test
    fun createOutfitFiltersForeignItems() = runTest {
        val r = repo()
        val me = r.ensureDefaultPerson()
        val wife = r.addPerson("老婆", "👩")
        r.upsertItem(Item("mine", me.id, WardrobeCategory.TOP, "T恤", imageFile = "a.webp"))
        r.upsertItem(Item("hers", wife.id, WardrobeCategory.TOP, "上衣", imageFile = "b.webp"))

        val outfit = r.createOutfit(me.id, listOf("mine", "hers", "ghost"))

        assertEquals(listOf("mine"), outfit.itemIds)
    }

    @Test
    fun removeEffectImageDeletesFile() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        r.upsertItem(Item("i1", p.id, WardrobeCategory.TOP, "T恤", imageFile = "a.webp"))
        val o = r.createOutfit(p.id, listOf("i1"))
        r.addEffectImage(o.id, "e1.webp")
        r.addEffectImage(o.id, "e2.webp")

        r.removeEffectImage(o.id, "e1.webp")

        val files = r.data.value.outfits.first { it.id == o.id }.effectImages.map { it.file }
        assertEquals(listOf("e2.webp"), files)
        assertEquals(listOf("e1.webp"), images.deleted)
    }

    @Test
    fun deleteOutfitRemovesItsNotes() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        r.upsertItem(Item("i1", p.id, WardrobeCategory.TOP, "T恤", imageFile = "a.webp"))
        val o = r.createOutfit(p.id, listOf("i1"))
        r.addNote(NoteParent.OUTFIT, o.id, "好看")
        r.addEffectImage(o.id, "e.webp")

        r.deleteOutfit(o.id)

        assertTrue(r.data.value.outfits.isEmpty())
        assertTrue(r.data.value.notes.isEmpty())
        assertEquals(listOf("e.webp"), images.deleted)
    }

    @Test
    fun danglingRefsCleanedOnLoad() = runTest {
        // 直接写入含悬空引用的数据
        newStore().commit(
            WardrobeData(
                persons = listOf(Person("p1", "我")),
                items = listOf(Item("i1", "ghost_person", WardrobeCategory.TOP, "T恤", imageFile = "a.webp")),
                outfits = listOf(Outfit("o1", "p1", itemIds = listOf("i1", "ghost_item"))),
                notes = listOf(Note("n1", NoteParent.OUTFIT, "ghost_outfit", "x")),
            ),
        )

        val d = repo().data.value

        assertTrue(d.items.isEmpty()) // personId 悬空 → 移除
        assertTrue(d.outfits.first().itemIds.isEmpty())
        assertTrue(d.notes.isEmpty())
    }

    @Test
    fun persistsAcrossInstances() = runTest {
        val r1 = repo()
        val p = r1.ensureDefaultPerson()
        r1.upsertItem(Item("i1", p.id, WardrobeCategory.SHOES, "小白鞋", imageFile = "a.webp"))

        val r2 = repo()
        assertEquals(1, r2.data.value.items.size)
    }

    // ---- it-017 形象参考照 ----

    @Test
    fun setRefPhotoUpdatesPersonAndDeletesOldFile() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()

        r.setPersonRefPhoto(p.id, "ref1.webp")
        assertEquals("ref1.webp", r.data.value.persons.first().refImageFile)
        assertTrue(images.deleted.isEmpty()) // 首次设置无旧文件可删

        r.setPersonRefPhoto(p.id, "ref2.webp")
        assertEquals("ref2.webp", r.data.value.persons.first().refImageFile)
        assertEquals(listOf("ref1.webp"), images.deleted) // 换照删旧，新文件不删
    }

    @Test
    fun removeRefPhotoClearsAndDeletesFile() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()

        r.removePersonRefPhoto(p.id) // 未设置时 no-op
        assertTrue(images.deleted.isEmpty())

        r.setPersonRefPhoto(p.id, "ref.webp")
        r.removePersonRefPhoto(p.id)
        assertEquals(null, r.data.value.persons.first().refImageFile)
        assertEquals(listOf("ref.webp"), images.deleted)
    }

    @Test
    fun deletePersonRemovesRefPhotoFile() = runTest {
        val r = repo()
        val me = r.ensureDefaultPerson()
        val wife = r.addPerson("老婆", "👩")
        r.setPersonRefPhoto(me.id, "me_ref.webp")
        r.setPersonRefPhoto(wife.id, "wife_ref.webp")
        images.deleted.clear()

        r.deletePerson(me.id)

        assertTrue("me_ref.webp" in images.deleted)
        assertTrue("wife_ref.webp" !in images.deleted)
        assertEquals("wife_ref.webp", r.data.value.persons.first().refImageFile)
    }

    @Test
    fun oldJsonWithoutRefPhotoFieldLoadsAsNull() = runTest {
        // it-017 向后兼容：旧备份 JSON（Person 无 refImageFile 字段）正常加载为未设置
        val legacyJson =
            """{"schemaVersion":1,"persons":[{"id":"p1","name":"Leo","emoji":"👨","createdAt":0}]}"""
        java.io.File(tmp.root, "wardrobe.json").writeText(legacyJson)

        val d = repo().data.value

        assertEquals(1, d.persons.size)
        assertEquals(null, d.persons.first().refImageFile)
    }
}
