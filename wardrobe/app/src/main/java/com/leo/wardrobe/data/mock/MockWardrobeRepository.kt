package com.leo.wardrobe.data.mock

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.OutfitImage
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import com.leo.wardrobe.domain.model.newId
import com.leo.wardrobe.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 演示仓库（it-015）：内存 SSOT，写操作只改内存、绝不落盘——演示模式下的增删
 * 在退出（重启进程）后自然消失，真实数据零接触。
 * 归一化与级联规则与 WardrobeRepositoryImpl 保持一致；图片文件不物理删除（mock 目录为临时解包）。
 */
class MockWardrobeRepository(seed: WardrobeData = MockWardrobeData.create()) : WardrobeRepository {

    private val _data = MutableStateFlow(seed)
    override val data: StateFlow<WardrobeData> = _data.asStateFlow()

    override suspend fun ensureDefaultPerson(): Person {
        _data.value.persons.firstOrNull()?.let { return it }
        val p = Person(newId(), "我", "🙂", createdAt = System.currentTimeMillis())
        _data.value = _data.value.copy(persons = _data.value.persons + p)
        return p
    }

    override suspend fun addPerson(name: String, emoji: String): Person {
        val p = Person(newId(), name.trim().ifEmpty { "未命名" }, emoji.ifEmpty { "🙂" }, createdAt = System.currentTimeMillis())
        _data.value = _data.value.copy(persons = _data.value.persons + p)
        return p
    }

    override suspend fun updatePerson(id: String, name: String, emoji: String) {
        _data.value = _data.value.copy(
            persons = _data.value.persons.map { p ->
                if (p.id == id) p.copy(
                    name = name.trim().ifEmpty { p.name },
                    emoji = emoji.ifEmpty { p.emoji },
                ) else p
            },
        )
    }

    override suspend fun setPersonRefPhoto(id: String, photoFile: String) {
        _data.value = _data.value.copy(
            persons = _data.value.persons.map { p ->
                if (p.id == id) p.copy(refImageFile = photoFile) else p
            },
        )
    }

    override suspend fun removePersonRefPhoto(id: String) {
        _data.value = _data.value.copy(
            persons = _data.value.persons.map { p ->
                if (p.id == id) p.copy(refImageFile = null) else p
            },
        )
    }

    override suspend fun deletePerson(id: String) {
        val itemIds = _data.value.items.filter { it.personId == id }.map { it.id }.toSet()
        val outfitIds = _data.value.outfits.filter { it.personId == id }.map { it.id }.toSet()
        _data.value = _data.value.copy(
            persons = _data.value.persons.filterNot { p -> p.id == id },
            items = _data.value.items.filterNot { i -> i.personId == id },
            outfits = _data.value.outfits.filterNot { o -> o.personId == id },
            notes = _data.value.notes.filterNot { n ->
                (n.parentType == NoteParent.ITEM && n.parentId in itemIds) ||
                    (n.parentType == NoteParent.OUTFIT && n.parentId in outfitIds)
            },
            wearLogs = _data.value.wearLogs.filterNot { l -> l.personId == id },
            // it-020：心愿域级联，与 WardrobeRepositoryImpl 对齐（此前演示模式漂移）
            wishItems = _data.value.wishItems.filterNot { w -> w.personId == id },
            wishOutfits = _data.value.wishOutfits.filterNot { w -> w.personId == id },
        )
    }

    override suspend fun upsertItem(item: Item) {
        val exists = _data.value.items.any { it.id == item.id }
        val fixed = if (exists) item.copy(updatedAt = now()) else item.copy(createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(
            items = _data.value.items
                .map { if (it.id == fixed.id) fixed else it }
                .let { if (!exists) it + fixed else it },
        )
    }

    override suspend fun deleteItem(id: String) {
        _data.value = _data.value.copy(
            items = _data.value.items.filterNot { it.id == id },
            outfits = _data.value.outfits.map { o -> o.copy(itemIds = o.itemIds - id) },
            notes = _data.value.notes.filterNot { it.parentType == NoteParent.ITEM && it.parentId == id },
            // it-020：从心愿穿搭的已有件部分移除，与 WardrobeRepositoryImpl 对齐
            wishOutfits = _data.value.wishOutfits.map { w -> w.copy(itemIds = w.itemIds - id) },
        )
    }

    override suspend fun createOutfit(personId: String, itemIds: List<String>, tags: List<String>): Outfit {
        val o = Outfit(
            id = newId(),
            personId = personId,
            itemIds = itemIds.filter { id -> _data.value.items.any { it.id == id && it.personId == personId } },
            tags = tags.distinct(),
            createdAt = now(),
            updatedAt = now(),
        )
        _data.value = _data.value.copy(outfits = _data.value.outfits + o)
        return o
    }

    override suspend fun updateOutfit(outfit: Outfit) {
        _data.value = _data.value.copy(
            outfits = _data.value.outfits
                .map { if (it.id == outfit.id) outfit.copy(updatedAt = now()) else it }
                .let { list -> if (list.none { o -> o.id == outfit.id }) list + outfit else list },
        )
    }

    override suspend fun deleteOutfit(id: String) {
        _data.value = _data.value.copy(
            outfits = _data.value.outfits.filterNot { it.id == id },
            notes = _data.value.notes.filterNot { it.parentType == NoteParent.OUTFIT && it.parentId == id },
            wearLogs = _data.value.wearLogs.filterNot { it.outfitId == id },
        )
    }

    override suspend fun addEffectImage(outfitId: String, imageFile: String) {
        _data.value = _data.value.copy(
            outfits = _data.value.outfits.map { o ->
                if (o.id == outfitId) {
                    o.copy(effectImages = o.effectImages + OutfitImage(imageFile, now()), updatedAt = now())
                } else o
            },
        )
    }

    override suspend fun removeEffectImage(outfitId: String, imageFile: String) {
        _data.value = _data.value.copy(
            outfits = _data.value.outfits.map { o ->
                if (o.id == outfitId) {
                    o.copy(effectImages = o.effectImages.filterNot { e -> e.file == imageFile }, updatedAt = now())
                } else o
            },
        )
    }

    // ---- WearLog（it-018 阶段A；mock 与 Impl 级联规则一致，不物理删图） ----

    override suspend fun addWearLog(outfitId: String, at: Long): WearLog {
        val outfit = _data.value.outfits.firstOrNull { it.id == outfitId }
            ?: throw IllegalArgumentException("outfit 不存在: $outfitId")
        val log = WearLog(newId(), outfit.personId, outfitId, at, System.currentTimeMillis())
        _data.value = _data.value.copy(wearLogs = _data.value.wearLogs + log)
        return log
    }

    override suspend fun deleteWearLogsOf(outfitId: String, from: Long, to: Long) {
        _data.value = _data.value.copy(
            wearLogs = _data.value.wearLogs.filterNot { l -> l.outfitId == outfitId && l.at >= from && l.at < to },
        )
    }

    // ---- 心愿域（it-019；mock 与 Impl 级联规则一致，不物理删图） ----

    override suspend fun upsertWishItem(item: com.leo.wardrobe.domain.model.WishItem) {
        val exists = _data.value.wishItems.any { it.id == item.id }
        val fixed = if (exists) item.copy(updatedAt = now()) else item.copy(createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(
            wishItems = _data.value.wishItems
                .map { if (it.id == fixed.id) fixed else it }
                .let { if (!exists) it + fixed else it },
        )
    }

    override suspend fun deleteWishItem(id: String) {
        _data.value = _data.value.copy(
            wishItems = _data.value.wishItems.filterNot { it.id == id },
            wishOutfits = _data.value.wishOutfits
                .map { w -> w.copy(wishItemIds = w.wishItemIds - id) }
                .filter { it.wishItemIds.isNotEmpty() },
        )
    }

    override suspend fun purchaseWishItem(wishItemId: String, item: Item): Item {
        val wish = _data.value.wishItems.firstOrNull { it.id == wishItemId }
            ?: throw IllegalArgumentException("wishItem 不存在: $wishItemId")
        val created = item.copy(
            id = item.id.ifBlank { newId() },
            personId = wish.personId,
            createdAt = now(),
            updatedAt = now(),
        )
        _data.value = _data.value.copy(
            items = _data.value.items + created,
            wishItems = _data.value.wishItems.map { w ->
                if (w.id == wishItemId) w.copy(purchasedAt = now(), purchasedItemId = created.id, updatedAt = now()) else w
            },
            wishOutfits = _data.value.wishOutfits.map { w ->
                if (wishItemId in w.wishItemIds) {
                    w.copy(wishItemIds = w.wishItemIds - wishItemId, itemIds = w.itemIds + created.id)
                } else w
            },
        )
        return created
    }

    override suspend fun createWishOutfit(
        personId: String,
        itemIds: List<String>,
        wishItemIds: List<String>,
        tags: List<String>,
    ): com.leo.wardrobe.domain.model.WishOutfit {
        require(wishItemIds.isNotEmpty()) { "心愿穿搭至少含一件愿望单品" }
        val w = com.leo.wardrobe.domain.model.WishOutfit(
            id = newId(),
            personId = personId,
            itemIds = itemIds.filter { id -> _data.value.items.any { it.id == id && it.personId == personId } },
            wishItemIds = wishItemIds.filter { id -> _data.value.wishItems.any { it.id == id && it.personId == personId } },
            tags = tags.distinct(),
            createdAt = now(),
            updatedAt = now(),
        )
        _data.value = _data.value.copy(wishOutfits = _data.value.wishOutfits + w)
        return w
    }

    override suspend fun updateWishOutfit(wishOutfit: com.leo.wardrobe.domain.model.WishOutfit) {
        _data.value = _data.value.copy(
            wishOutfits = _data.value.wishOutfits
                .map { if (it.id == wishOutfit.id) wishOutfit.copy(updatedAt = now()) else it }
                .let { list -> if (list.none { w -> w.id == wishOutfit.id }) list + wishOutfit else list },
        )
    }

    override suspend fun deleteWishOutfit(id: String) {
        _data.value = _data.value.copy(wishOutfits = _data.value.wishOutfits.filterNot { it.id == id })
    }

    override suspend fun addPreviewImage(wishOutfitId: String, imageFile: String) {
        _data.value = _data.value.copy(
            wishOutfits = _data.value.wishOutfits.map { w ->
                if (w.id == wishOutfitId) {
                    w.copy(previewImages = w.previewImages + OutfitImage(imageFile, now()), updatedAt = now())
                } else w
            },
        )
    }

    override suspend fun removePreviewImage(wishOutfitId: String, imageFile: String) {
        _data.value = _data.value.copy(
            wishOutfits = _data.value.wishOutfits.map { w ->
                if (w.id == wishOutfitId) {
                    w.copy(previewImages = w.previewImages.filterNot { p -> p.file == imageFile }, updatedAt = now())
                } else w
            },
        )
    }

    override suspend fun promoteWishOutfit(id: String): Outfit {
        val wish = _data.value.wishOutfits.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("wishOutfit 不存在: $id")
        if (wish.wishItemIds.isNotEmpty()) throw IllegalStateException("还有 ${wish.wishItemIds.size} 件愿望单品未购入")
        val outfit = Outfit(
            id = newId(),
            personId = wish.personId,
            itemIds = wish.itemIds,
            tags = wish.tags,
            effectImages = wish.previewImages,
            createdAt = now(),
            updatedAt = now(),
        )
        _data.value = _data.value.copy(
            outfits = _data.value.outfits + outfit,
            wishOutfits = _data.value.wishOutfits.filterNot { w -> w.id == id },
        )
        return outfit
    }

    override suspend fun addNote(parentType: NoteParent, parentId: String, text: String): Note {
        val n = Note(newId(), parentType, parentId, text.trim(), now())
        _data.value = _data.value.copy(notes = _data.value.notes + n)
        return n
    }

    override suspend fun deleteNote(id: String) {
        _data.value = _data.value.copy(notes = _data.value.notes.filterNot { n -> n.id == id })
    }

    private fun now(): Long = System.currentTimeMillis()
}
