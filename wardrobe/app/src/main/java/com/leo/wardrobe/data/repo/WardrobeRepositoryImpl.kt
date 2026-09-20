package com.leo.wardrobe.data.repo

import com.leo.libs.store.SnapshotStore
import com.leo.libs.store.SsotRepository
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.OutfitImage
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit
import com.leo.wardrobe.domain.model.newId
import com.leo.wardrobe.domain.repository.ImageStore
import com.leo.wardrobe.domain.repository.WardrobeRepository

/**
 * SSOT 实现（ADR-008）：内存快照 + 写操作「改快照→原子落盘→广播」，
 * 持久化与广播机制由 store SDK 的 [SsotRepository] 承担；不变量见 specs/03-data-model.md。
 */
class WardrobeRepositoryImpl(
    store: SnapshotStore<WardrobeData>,
    private val images: ImageStore,
) : SsotRepository<WardrobeData>(store, onLoad = { it.cleaned() }), WardrobeRepository {

    /** 测试可注入的时钟 */
    var now: () -> Long = { System.currentTimeMillis() }

    private fun <T> List<T>.replaceBy(id: String, selector: (T) -> String, map: (T) -> T): List<T> =
        mapNotNull { if (selector(it) == id) map(it) else it }

    // ---- Person ----

    override suspend fun ensureDefaultPerson(): Person {
        data.value.persons.firstOrNull()?.let { return it }
        val p = Person(newId(), "我", "🙂", createdAt = now())
        mutate { it.copy(persons = it.persons + p) }
        return p
    }

    override suspend fun addPerson(name: String, emoji: String): Person {
        val p = Person(newId(), name.trim().ifEmpty { "未命名" }, emoji.ifEmpty { "🙂" }, createdAt = now())
        mutate { it.copy(persons = it.persons + p) }
        return p
    }

    override suspend fun updatePerson(id: String, name: String, emoji: String) {
        mutate {
            it.copy(persons = it.persons.replaceBy(id, { p -> p.id }) { p ->
                p.copy(name = name.trim().ifEmpty { p.name }, emoji = emoji.ifEmpty { p.emoji })
            })
        }
    }

    override suspend fun setPersonRefPhoto(id: String, photoFile: String) {
        val old = data.value.persons.firstOrNull { it.id == id }?.refImageFile
        mutate {
            it.copy(persons = it.persons.replaceBy(id, { p -> p.id }) { p ->
                p.copy(refImageFile = photoFile)
            })
        }
        // it-017：换照删旧文件，不残留（快照更新成功后再删）
        if (old != null && old != photoFile) images.delete(old)
    }

    override suspend fun removePersonRefPhoto(id: String) {
        val old = data.value.persons.firstOrNull { it.id == id }?.refImageFile ?: return
        mutate {
            it.copy(persons = it.persons.replaceBy(id, { p -> p.id }) { p ->
                p.copy(refImageFile = null)
            })
        }
        images.delete(old)
    }

    override suspend fun deletePerson(id: String) {
        // 先收集要删的图片文件，快照更新成功后物理删除
        val snapshot = data.value
        val imageFiles = buildList {
            snapshot.persons.firstOrNull { it.id == id }?.refImageFile?.let { add(it) }
            addAll(snapshot.items.filter { it.personId == id }.map { it.imageFile })
            snapshot.outfits.filter { it.personId == id }.forEach { o ->
                addAll(o.effectImages.map { it.file })
            }
            // it-019：愿望单品商品图与心愿穿搭预览图一并收集
            snapshot.wishItems.filter { it.personId == id }.forEach { w -> w.imageFile?.let { add(it) } }
            snapshot.wishOutfits.filter { it.personId == id }.forEach { w ->
                addAll(w.previewImages.map { it.file })
            }
        }
        val itemIds = snapshot.items.filter { it.personId == id }.map { it.id }.toSet()
        val outfitIds = snapshot.outfits.filter { it.personId == id }.map { it.id }.toSet()
        mutate {
            it.copy(
                persons = it.persons.filterNot { p -> p.id == id },
                items = it.items.filterNot { i -> i.personId == id },
                outfits = it.outfits.filterNot { o -> o.personId == id },
                notes = it.notes.filterNot { n ->
                    (n.parentType == NoteParent.ITEM && n.parentId in itemIds) ||
                        (n.parentType == NoteParent.OUTFIT && n.parentId in outfitIds)
                },
                // it-018：角色删除，其打卡记录级联删除
                wearLogs = it.wearLogs.filterNot { l -> l.personId == id },
                // it-019：心愿域级联删除
                wishItems = it.wishItems.filterNot { w -> w.personId == id },
                wishOutfits = it.wishOutfits.filterNot { w -> w.personId == id },
            )
        }
        imageFiles.forEach { images.delete(it) }
    }

    // ---- Item ----

    override suspend fun upsertItem(item: Item) {
        mutate { d ->
            val exists = d.items.any { it.id == item.id }
            val fixed = if (exists) item.copy(updatedAt = now()) else item.copy(createdAt = now(), updatedAt = now())
            d.copy(items = d.items.replaceBy(item.id, { i -> i.id }) { fixed }
                .let { if (!exists) it + fixed else it })
        }
    }

    override suspend fun deleteItem(id: String) {
        val image = data.value.items.firstOrNull { it.id == id }?.imageFile
        mutate { d ->
            d.copy(
                items = d.items.filterNot { it.id == id },
                outfits = d.outfits.map { o -> o.copy(itemIds = o.itemIds - id) },
                notes = d.notes.filterNot { it.parentType == NoteParent.ITEM && it.parentId == id },
                // it-019：从心愿穿搭的已有件部分移除
                wishOutfits = d.wishOutfits.map { w -> w.copy(itemIds = w.itemIds - id) },
            )
        }
        image?.let { images.delete(it) }
    }

    // ---- Outfit ----

    override suspend fun createOutfit(personId: String, itemIds: List<String>, tags: List<String>): Outfit {
        val o = Outfit(
            id = newId(),
            personId = personId,
            itemIds = itemIds.filter { id -> data.value.items.any { it.id == id && it.personId == personId } },
            tags = tags.distinct(),
            createdAt = now(),
            updatedAt = now(),
        )
        mutate { it.copy(outfits = it.outfits + o) }
        return o
    }

    override suspend fun updateOutfit(outfit: Outfit) {
        mutate {
            it.copy(outfits = it.outfits.replaceBy(outfit.id, { o -> o.id }) { outfit.copy(updatedAt = now()) }
                .let { list -> if (list.none { o -> o.id == outfit.id }) list + outfit else list })
        }
    }
    override suspend fun deleteOutfit(id: String) {
        val files = data.value.outfits.firstOrNull { it.id == id }?.effectImages?.map { it.file } ?: emptyList()
        mutate { d ->
            d.copy(
                outfits = d.outfits.filterNot { it.id == id },
                notes = d.notes.filterNot { it.parentType == NoteParent.OUTFIT && it.parentId == id },
                // it-018：穿搭删除，其打卡记录级联删除
                wearLogs = d.wearLogs.filterNot { it.outfitId == id },
            )
        }
        files.forEach { images.delete(it) }
    }

    override suspend fun addEffectImage(outfitId: String, imageFile: String) {
        mutate {
            it.copy(outfits = it.outfits.replaceBy(outfitId, { o -> o.id }) { o ->
                o.copy(effectImages = o.effectImages + OutfitImage(imageFile, now()), updatedAt = now())
            })
        }
    }

    override suspend fun removeEffectImage(outfitId: String, imageFile: String) {
        mutate { d ->
            d.copy(outfits = d.outfits.replaceBy(outfitId, { o -> o.id }) { o ->
                o.copy(effectImages = o.effectImages.filterNot { e -> e.file == imageFile }, updatedAt = now())
            })
        }
        images.delete(imageFile)
    }

    // ---- WearLog（it-018 阶段A） ----

    override suspend fun addWearLog(outfitId: String, at: Long): WearLog {
        val outfit = data.value.outfits.firstOrNull { it.id == outfitId }
            ?: throw IllegalArgumentException("outfit 不存在: $outfitId")
        val log = WearLog(newId(), outfit.personId, outfitId, at, now())
        mutate { it.copy(wearLogs = it.wearLogs + log) }
        return log
    }

    override suspend fun deleteWearLogsOf(outfitId: String, from: Long, to: Long) {
        mutate {
            it.copy(wearLogs = it.wearLogs.filterNot { l -> l.outfitId == outfitId && l.at >= from && l.at < to })
        }
    }

    // ---- 心愿域（it-019） ----

    override suspend fun upsertWishItem(item: WishItem) {
        mutate { d ->
            val exists = d.wishItems.any { it.id == item.id }
            val fixed = if (exists) item.copy(updatedAt = now()) else item.copy(createdAt = now(), updatedAt = now())
            d.copy(wishItems = d.wishItems.replaceBy(item.id, { w -> w.id }) { fixed }
                .let { if (!exists) it + fixed else it })
        }
    }

    override suspend fun deleteWishItem(id: String) {
        val image = data.value.wishItems.firstOrNull { it.id == id }?.imageFile
        mutate { d ->
            d.copy(
                wishItems = d.wishItems.filterNot { it.id == id },
                // 从心愿穿搭移除该愿望件；不变量（至少一件愿望单品）被破坏的组合一并删除
                wishOutfits = d.wishOutfits
                    .map { w -> w.copy(wishItemIds = w.wishItemIds - id) }
                    .filter { it.wishItemIds.isNotEmpty() },
            )
        }
        image?.let { images.delete(it) }
    }

    override suspend fun purchaseWishItem(wishItemId: String, item: Item): Item {
        val wish = data.value.wishItems.firstOrNull { it.id == wishItemId }
            ?: throw IllegalArgumentException("wishItem 不存在: $wishItemId")
        val created = item.copy(
            id = item.id.ifBlank { newId() },
            personId = wish.personId,
            createdAt = now(),
            updatedAt = now(),
        )
        mutate { d ->
            d.copy(
                items = d.items + created,
                wishItems = d.wishItems.replaceBy(wishItemId, { w -> w.id }) { w ->
                    w.copy(purchasedAt = now(), purchasedItemId = created.id, updatedAt = now())
                },
                // 转正联动：含该愿望件的心愿穿搭把它移入已有件部分
                wishOutfits = d.wishOutfits.map { w ->
                    if (wishItemId in w.wishItemIds) {
                        w.copy(wishItemIds = w.wishItemIds - wishItemId, itemIds = w.itemIds + created.id)
                    } else {
                        w
                    }
                },
            )
        }
        return created
    }

    override suspend fun createWishOutfit(
        personId: String,
        itemIds: List<String>,
        wishItemIds: List<String>,
        tags: List<String>,
    ): WishOutfit {
        require(wishItemIds.isNotEmpty()) { "心愿穿搭至少含一件愿望单品" }
        val w = WishOutfit(
            id = newId(),
            personId = personId,
            itemIds = itemIds.filter { id -> data.value.items.any { it.id == id && it.personId == personId } },
            wishItemIds = wishItemIds.filter { id -> data.value.wishItems.any { it.id == id && it.personId == personId } },
            tags = tags.distinct(),
            createdAt = now(),
            updatedAt = now(),
        )
        mutate { it.copy(wishOutfits = it.wishOutfits + w) }
        return w
    }

    override suspend fun updateWishOutfit(wishOutfit: WishOutfit) {
        mutate {
            it.copy(
                wishOutfits = it.wishOutfits.replaceBy(wishOutfit.id, { w -> w.id }) {
                    wishOutfit.copy(updatedAt = now())
                }.let { list -> if (list.none { w -> w.id == wishOutfit.id }) list + wishOutfit else list },
            )
        }
    }

    override suspend fun deleteWishOutfit(id: String) {
        val files = data.value.wishOutfits.firstOrNull { it.id == id }?.previewImages?.map { it.file } ?: emptyList()
        mutate { it.copy(wishOutfits = it.wishOutfits.filterNot { w -> w.id == id }) }
        files.forEach { images.delete(it) }
    }

    override suspend fun addPreviewImage(wishOutfitId: String, imageFile: String) {
        mutate {
            it.copy(wishOutfits = it.wishOutfits.replaceBy(wishOutfitId, { w -> w.id }) { w ->
                w.copy(previewImages = w.previewImages + OutfitImage(imageFile, now()), updatedAt = now())
            })
        }
    }

    override suspend fun removePreviewImage(wishOutfitId: String, imageFile: String) {
        mutate { d ->
            d.copy(wishOutfits = d.wishOutfits.replaceBy(wishOutfitId, { w -> w.id }) { w ->
                w.copy(previewImages = w.previewImages.filterNot { p -> p.file == imageFile }, updatedAt = now())
            })
        }
        images.delete(imageFile)
    }

    override suspend fun promoteWishOutfit(id: String): Outfit {
        val wish = data.value.wishOutfits.firstOrNull { it.id == id }
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
        mutate { d ->
            d.copy(outfits = d.outfits + outfit, wishOutfits = d.wishOutfits.filterNot { w -> w.id == id })
        }
        return outfit
    }

    // ---- Note ----

    override suspend fun addNote(parentType: NoteParent, parentId: String, text: String): Note {
        val n = Note(newId(), parentType, parentId, text.trim(), now())
        mutate { it.copy(notes = it.notes + n) }
        return n
    }

    override suspend fun deleteNote(id: String) {
        mutate { it.copy(notes = it.notes.filterNot { n -> n.id == id }) }
    }
}

/** 载入时清洗悬空引用（不变量 5） */
private fun WardrobeData.cleaned(): WardrobeData {
    val personIds = persons.map { it.id }.toSet()
    val validItems = items.filter { it.personId in personIds }
    val itemIds = validItems.map { it.id }.toSet()
    val validOutfits = outfits
        .filter { it.personId in personIds }
        .map { it.copy(itemIds = it.itemIds.filter { id -> id in itemIds }) }
    val outfitIds = validOutfits.map { it.id }.toSet()
    val validNotes = notes.filter {
        when (it.parentType) {
            NoteParent.ITEM -> it.parentId in itemIds
            NoteParent.OUTFIT -> it.parentId in outfitIds
        }
    }
    // it-018：打卡记录的 outfit/person 悬空引用一并清洗
    val validWearLogs = wearLogs.filter { it.personId in personIds && it.outfitId in outfitIds }
    // it-019：心愿域清洗——悬空引用移除，且心愿穿搭必须保留至少一件愿望单品（不变量）
    val validWishItems = wishItems.filter { it.personId in personIds }
        .map { w -> if (w.purchasedItemId != null && w.purchasedItemId !in itemIds) w.copy(purchasedItemId = null) else w }
    val validWishItemIds = validWishItems.map { it.id }.toSet()
    val validWishOutfits = wishOutfits
        .filter { it.personId in personIds }
        .map { w -> w.copy(itemIds = w.itemIds.filter { id -> id in itemIds }, wishItemIds = w.wishItemIds.filter { id -> id in validWishItemIds }) }
        .filter { it.wishItemIds.isNotEmpty() }
    return copy(
        items = validItems,
        outfits = validOutfits,
        notes = validNotes,
        wearLogs = validWearLogs,
        wishItems = validWishItems,
        wishOutfits = validWishOutfits,
    )
}
