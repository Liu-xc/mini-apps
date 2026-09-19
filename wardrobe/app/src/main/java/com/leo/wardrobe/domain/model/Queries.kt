package com.leo.wardrobe.domain.model

/** WardrobeData 上的常用查询（纯函数，UI/VM 共用）。 */

fun WardrobeData.personById(id: String): Person? = persons.firstOrNull { it.id == id }

fun WardrobeData.itemsOf(personId: String): List<Item> = items.filter { it.personId == personId }

fun WardrobeData.itemsOf(personId: String, category: WardrobeCategory): List<Item> =
    items.filter { it.personId == personId && it.category == category }

fun WardrobeData.outfitsOf(personId: String): List<Outfit> =
    outfits.filter { it.personId == personId }.sortedByDescending { it.updatedAt }

fun WardrobeData.itemById(id: String): Item? = items.firstOrNull { it.id == id }

fun WardrobeData.outfitById(id: String): Outfit? = outfits.firstOrNull { it.id == id }

fun WardrobeData.notesOf(parentType: NoteParent, parentId: String): List<Note> =
    notes.filter { it.parentType == parentType && it.parentId == parentId }
        .sortedByDescending { it.createdAt }

/** 反查：这件单品出现在哪些穿搭中（US-11） */
fun WardrobeData.outfitsContaining(itemId: String): List<Outfit> =
    outfits.filter { itemId in it.itemIds }.sortedByDescending { it.updatedAt }

/** 某角色下实际使用过的所有标签（筛选条数据源，按热度排序） */
fun WardrobeData.tagsUsedIn(personId: String): List<String> {
    val counts = LinkedHashMap<String, Int>()
    itemsOf(personId).forEach { it.tags.forEach { t -> counts[t] = (counts[t] ?: 0) + 1 } }
    outfitsOf(personId).forEach { it.tags.forEach { t -> counts[t] = (counts[t] ?: 0) + 1 } }
    return counts.entries.sortedByDescending { it.value }.map { it.key }
}
