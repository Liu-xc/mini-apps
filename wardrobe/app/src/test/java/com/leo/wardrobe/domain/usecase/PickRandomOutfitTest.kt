package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-081/B-6：随机一套的核心分配语义（Random 注入现成，五个 usecase 中唯一没测的） */
class PickRandomOutfitTest {

    private fun item(id: String, cat: WardrobeCategory, personId: String = "p1") =
        Item(id = id, personId = personId, category = cat, name = id, imageFile = "$id.webp")

    @Test
    fun `each non-empty category gets exactly one pick`() {
        val items = listOf(
            item("a1", WardrobeCategory.TOP), item("a2", WardrobeCategory.TOP),
            item("b1", WardrobeCategory.BOTTOM),
            item("s1", WardrobeCategory.SHOES), item("s2", WardrobeCategory.SHOES),
        )
        val pick = PickRandomOutfit(Random(42))(items)

        assertEquals(setOf(WardrobeCategory.TOP, WardrobeCategory.BOTTOM, WardrobeCategory.SHOES), pick.keys)
        pick.forEach { (cat, chosen) -> assertEquals(cat, chosen.category) }
    }

    @Test
    fun `empty input yields empty pick`() {
        assertTrue(PickRandomOutfit(Random(1))(emptyList()).isEmpty())
    }

    @Test
    fun `deterministic with seeded random`() {
        val items = (1..8).map { item("i$it", WardrobeCategory.ACCESSORY) }
        val r1 = PickRandomOutfit(Random(7))(items)
        val r2 = PickRandomOutfit(Random(7))(items)
        assertEquals(r1, r2)
    }
}
