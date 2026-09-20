package com.leo.wardrobe.data.repo

import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.isWishSlot
import com.leo.wardrobe.domain.model.wishOutfitWithMembers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 心愿域不变量测试（it-019，specs/03-data-model.md） */
class WishRepositoryTest {

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

    private suspend fun seedWish(r: WardrobeRepositoryImpl, personId: String, id: String = "wi1") =
        WishItem(id = id, personId = personId, category = WardrobeCategory.OUTERWEAR, name = "羊绒大衣").also {
            r.upsertWishItem(it)
        }

    @Test
    fun upsertThenDeleteWishItem() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        seedWish(r, p.id)
        assertEquals(1, r.data.value.wishItems.size)
        r.deleteWishItem("wi1")
        assertTrue(r.data.value.wishItems.isEmpty())
    }

    @Test
    fun deleteWishItemRemovesItFromWishOutfits() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        seedWish(r, p.id, "wi1")
        seedWish(r, p.id, "wi2")
        r.createWishOutfit(p.id, itemIds = emptyList(), wishItemIds = listOf("wi1", "wi2"))
        r.deleteWishItem("wi1")
        // 组合保留（仍含 wi2）
        assertEquals(1, r.data.value.wishOutfits.size)
        assertEquals(listOf("wi2"), r.data.value.wishOutfits.first().wishItemIds)
        // 两个愿望都删光 → 违反「至少一件愿望单品」不变量，组合随之消失
        r.deleteWishItem("wi2")
        assertTrue(r.data.value.wishOutfits.isEmpty())
    }

    @Test
    fun createWishOutfitRequiresAtLeastOneWish() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        val e = runCatching { r.createWishOutfit(p.id, itemIds = emptyList(), wishItemIds = emptyList()) }
        assertTrue(e.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun wishOutfitDedupQueryIgnoresOrder() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        seedWish(r, p.id)
        r.upsertItem(Item("i1", p.id, WardrobeCategory.TOP, "白衬衫", imageFile = "a.webp"))
        r.createWishOutfit(p.id, itemIds = listOf("i1"), wishItemIds = listOf("wi1"))
        assertNotNull(r.data.value.wishOutfitWithMembers(p.id, listOf("i1"), listOf("wi1")))
        assertNull(r.data.value.wishOutfitWithMembers(p.id, listOf("i1"), emptyList()))
    }

    @Test
    fun purchaseWishItemCreatesItemAndUpdatesWishOutfits() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        seedWish(r, p.id, "wi1")
        r.upsertItem(Item("i1", p.id, WardrobeCategory.TOP, "白衬衫", imageFile = "a.webp"))
        r.createWishOutfit(p.id, itemIds = listOf("i1"), wishItemIds = listOf("wi1"))

        val created = r.purchaseWishItem(
            "wi1",
            Item(id = "", personId = p.id, category = WardrobeCategory.OUTERWEAR, name = "羊绒大衣·实物", imageFile = "real.webp"),
        )
        // 正式 Item 创建、愿望回填
        assertEquals("羊绒大衣·实物", created.name)
        val wish = r.data.value.wishItems.first()
        assertNotNull(wish.purchasedAt)
        assertEquals(created.id, wish.purchasedItemId)
        // 心愿穿搭联动：愿望件移入 itemIds
        val outfit = r.data.value.wishOutfits.first()
        assertTrue(created.id in outfit.itemIds)
        assertTrue(outfit.wishItemIds.isEmpty())
        // 买齐 → 可升级
        val promoted = r.promoteWishOutfit(outfit.id)
        assertEquals(setOf("i1", created.id), promoted.itemIds.toSet())
        assertTrue(r.data.value.wishOutfits.isEmpty())
        // 升级为正式 Outfit，非心愿穿搭
        assertTrue(r.data.value.outfits.any { it.id == promoted.id })
    }

    @Test
    fun promoteBlockedWhenWishesRemain() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        seedWish(r, p.id)
        val w = r.createWishOutfit(p.id, itemIds = emptyList(), wishItemIds = listOf("wi1"))
        val e = runCatching { r.promoteWishOutfit(w.id) }
        assertTrue(e.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun deletePersonCascadesWishDomain() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        val w = seedWish(r, p.id).copy(imageFile = "wish.webp")
        r.upsertWishItem(w)
        r.createWishOutfit(p.id, itemIds = emptyList(), wishItemIds = listOf(w.id))
        r.deletePerson(p.id)
        assertTrue(r.data.value.wishItems.isEmpty())
        assertTrue(r.data.value.wishOutfits.isEmpty())
        assertTrue("wish.webp" in images.deleted)
    }

    @Test
    fun slotAdapterPrefixRoundtrip() {
        val wish = WishItem(id = "x", personId = "p", category = WardrobeCategory.SHOES, name = "切尔西靴")
        val slot = wish.asSlotItem()
        assertTrue(slot.isWishSlot)
        assertEquals("x", slot.id.removePrefix("wish:"))
        assertEquals(WardrobeCategory.SHOES, slot.category)
    }
}
