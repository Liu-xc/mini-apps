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
    // ---- it-081/D-1 回归：心愿穿搭生命周期（购齐待升级是合法态，不再被 cleaned 静默删除） ----

    @Test
    fun `purchaseAllThenReloadKeepsWishOutfitAndPromoteWorks`() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        r.upsertItem(Item(id = "it9", personId = p.id, category = WardrobeCategory.TOP, name = "衬衫", imageFile = "a.webp"))
        val w1 = seedWish(r, p.id, "wi1")
        seedWish(r, p.id, "wi2")
        r.createWishOutfit(p.id, itemIds = listOf("it9"), wishItemIds = listOf("wi1", "wi2"))
        // 逐件购齐（旧语义下：wishItemIds 清空 → 重启 cleaned 即删除该实体，预览图孤儿化）
        r.purchaseWishItem("wi1", Item(id = "i1", personId = p.id, category = w1.category, name = "大衣A", imageFile = "b.webp"))
        r.purchaseWishItem("wi2", Item(id = "i2", personId = p.id, category = w1.category, name = "大衣B", imageFile = "c.webp"))
        assertTrue(r.data.value.wishOutfits.single().wishItemIds.isEmpty())

        // 模拟重启：同 store 新实例（onLoad cleaned()）
        val r2 = WardrobeRepositoryImpl(newStore(), images)
        val kept = r2.data.value.wishOutfits.singleOrNull()
        assertNotNull("购齐待升级的心愿穿搭在重启清洗后必须保留", kept)
        // 一键升级仍可用
        val outfit = r2.promoteWishOutfit(kept!!.id)
        assertEquals(listOf("it9", "i1", "i2"), outfit.itemIds)
        assertTrue(r2.data.value.wishOutfits.isEmpty())
    }

    @Test
    fun `deleteLastWishItemKeepsOutfitContainingRealItems`() = runTest {
        val r = repo()
        val p = r.ensureDefaultPerson()
        r.upsertItem(Item(id = "it8", personId = p.id, category = WardrobeCategory.TOP, name = "衬衫", imageFile = "a.webp"))
        seedWish(r, p.id, "wi1")
        r.createWishOutfit(p.id, itemIds = listOf("it8"), wishItemIds = listOf("wi1"))
        // 删掉最后一件愿望件：组合仍含已有件 → 保留（不再随「变空」一并删除）
        r.deleteWishItem("wi1")
        val kept = r.data.value.wishOutfits.singleOrNull()
        assertNotNull(kept)
        assertTrue(kept!!.wishItemIds.isEmpty())
        assertEquals(listOf("it8"), kept.itemIds)
    }
}

