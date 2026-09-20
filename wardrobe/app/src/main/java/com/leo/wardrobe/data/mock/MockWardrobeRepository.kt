package com.leo.wardrobe.data.mock

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.OutfitImage
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeData
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
        val p = Person(newId(), "我", "🙂", System.currentTimeMillis())
        _data.value = _data.value.copy(persons = _data.value.persons + p)
        return p
    }

    override suspend fun addPerson(name: String, emoji: String): Person {
        val p = Person(newId(), name.trim().ifEmpty { "未命名" }, emoji.ifEmpty { "🙂" }, System.currentTimeMillis())
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
