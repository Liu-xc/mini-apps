package com.leo.wardrobe.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-024：数据包合并/差异/图片引用重映射纯函数 */
class DataPackageMergeTest {

    private fun item(id: String, name: String = id, tags: List<String> = emptyList(), updatedAt: Long = 0L) =
        Item(id = id, personId = "p1", category = WardrobeCategory.TOP, name = name, imageFile = "$id.webp", tags = tags, updatedAt = updatedAt)

    private fun outfit(id: String, items: List<String> = emptyList(), images: List<String> = emptyList()) =
        Outfit(id = id, personId = "p1", itemIds = items, effectImages = images.map { OutfitImage(it) })

    private val local = WardrobeData(
        persons = listOf(Person("p1", "Leo")),
        items = listOf(item("i1", tags = listOf("通勤")), item("i2")),
        outfits = listOf(outfit("o1", items = listOf("i1"))),
        notes = listOf(Note("n1", NoteParent.ITEM, "i1", "洗后微缩水")),
    )

    @Test
    fun `合并：包内同 id 整体覆盖、新实体追加、本地独有保留`() {
        val incoming = WardrobeData(
            persons = listOf(Person("p1", "Leo")),
            items = listOf(
                item("i1", tags = listOf("通勤", "简约")), // 覆盖：tags 更新
                item("i9"),                                // 新增
            ),
            outfits = listOf(outfit("o1", items = listOf("i1"))), // 内容相同
        )
        val merged = mergeWardrobe(local, incoming)

        assertEquals(listOf("i1", "i2", "i9"), merged.items.map { it.id })
        assertEquals(listOf("通勤", "简约"), merged.items.first { it.id == "i1" }.tags)
        assertTrue(merged.items.any { it.id == "i2" }) // 本地独有保留
        assertEquals(1, merged.notes.size)
    }

    @Test
    fun `diff 计数：新增、更新、不变、本地独有与覆盖警示`() {
        val incoming = WardrobeData(
            persons = listOf(Person("p1", "Leo")),
            items = listOf(
                item("i1", tags = listOf("通勤", "简约"), updatedAt = 50), // 更新（内容不同）
                item("i2"),                                              // 不变（内容相同）
                item("i9"),                                              // 新增
            ),
        )
        val d = diffWardrobe(local, incoming, exportedAt = 100L)
        assertEquals(1, d.items.added)
        assertEquals(1, d.items.updated)
        assertEquals(1, d.items.unchanged)
        assertEquals(0, d.items.localOnly) // i1、i2 都在包内
        assertEquals(1, d.outfits.localOnly) // o1 本地独有
        assertEquals(3, d.items.totalAfter)
    }

    @Test
    fun `本地晚于导出时间且将被覆盖的实体计入 locallyNewer`() {
        // i1 本地 updatedAt=120 > exportedAt=100，且包内版本内容不同 → 警示
        val localNewer = local.copy(items = local.items.map { if (it.id == "i1") it.copy(updatedAt = 120) else it })
        val incoming = WardrobeData(
            persons = listOf(Person("p1", "Leo")),
            items = listOf(item("i1", tags = listOf("简约"), updatedAt = 50), item("i2")),
        )
        assertEquals(1, diffWardrobe(localNewer, incoming, exportedAt = 100L).locallyNewer)
        // 内容相同（不会被实际改写）不计入
        val sameIncoming = WardrobeData(persons = listOf(Person("p1", "Leo")), items = localNewer.items)
        assertEquals(0, diffWardrobe(localNewer, sameIncoming, exportedAt = 100L).locallyNewer)
    }

    @Test
    fun `重映射图片引用：五类图片字段全部改写`() {
        val data = WardrobeData(
            persons = listOf(Person("p1", "Leo", refImageFile = "ref.jpg")),
            items = listOf(item("i1").copy(imageFile = "top.jpg")),
            outfits = listOf(outfit("o1", images = listOf("a.jpg", "b.jpg"))),
            wishItems = listOf(WishItem("w1", "p1", WardrobeCategory.BAG, "包", imageFile = "bag.jpg")),
            wishOutfits = listOf(
                WishOutfit("wo1", "p1", wishItemIds = listOf("w1"), previewImages = listOf(OutfitImage("pv.jpg"))),
            ),
        )
        val remapped = data.remapImageRefs { old -> "uuid-$old" }
        assertEquals("uuid-ref.jpg", remapped.persons[0].refImageFile)
        assertEquals("uuid-top.jpg", remapped.items[0].imageFile)
        assertEquals(listOf("uuid-a.jpg", "uuid-b.jpg"), remapped.outfits[0].effectImages.map { it.file })
        assertEquals("uuid-bag.jpg", remapped.wishItems[0].imageFile)
        assertEquals(listOf("uuid-pv.jpg"), remapped.wishOutfits[0].previewImages.map { it.file })
    }

    @Test
    fun `referencedImages 收集五类引用且去重`() {
        val data = WardrobeData(
            persons = listOf(Person("p1", "Leo", refImageFile = "a.webp")),
            items = listOf(item("i1"), item("i2").copy(imageFile = "a.webp")),
            outfits = listOf(outfit("o1", images = listOf("b.webp"))),
        )
        assertEquals(setOf("i1.webp", "a.webp", "b.webp"), data.referencedImages())
    }

    @Test
    fun `合并保持本地顺序，包内新实体按包内顺序追加`() {
        val incoming = WardrobeData(
            persons = listOf(Person("p1", "Leo")),
            items = listOf(item("i9"), item("i1", tags = listOf("新")), item("i8")),
        )
        val merged = mergeWardrobe(local, incoming)
        assertEquals(listOf("i1", "i2", "i9", "i8"), merged.items.map { it.id })
        assertTrue(merged.items.first { it.id == "i1" }.tags == listOf("新"))
    }
}
