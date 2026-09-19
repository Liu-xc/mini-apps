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
        val p = Person(newId(), "我", "🙂", now())
        mutate { it.copy(persons = it.persons + p) }
        return p
    }

    override suspend fun addPerson(name: String, emoji: String): Person {
        val p = Person(newId(), name.trim().ifEmpty { "未命名" }, emoji.ifEmpty { "🙂" }, now())
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

    override suspend fun deletePerson(id: String) {
        // 先收集要删的图片文件，快照更新成功后物理删除
        val snapshot = data.value
        val imageFiles = buildList {
            addAll(snapshot.items.filter { it.personId == id }.map { it.imageFile })
            snapshot.outfits.filter { it.personId == id }.forEach { o ->
                addAll(o.effectImages.map { it.file })
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
    return copy(items = validItems, outfits = validOutfits, notes = validNotes)
}
