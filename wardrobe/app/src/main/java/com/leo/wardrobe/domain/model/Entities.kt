package com.leo.wardrobe.domain.model

import kotlinx.serialization.Serializable

/** 品类标签预设（specs/03-data-model.md）：仅影响输入体验，数据里标签是普通字符串。 */
object TagPresets {
    val style = listOf("通勤", "休闲", "运动", "约会", "度假", "正式", "简约", "复古")
    val season = listOf("春", "夏", "秋", "冬", "早春", "早秋")
    val occasion = listOf("上班", "出游", "居家", "聚会")

    /** 表单里快速点选的顺序 */
    val quickPicks: List<String> = style + season + occasion
}

fun newId(): String = java.util.UUID.randomUUID().toString()

@Serializable
data class Person(
    val id: String,
    val name: String,
    val emoji: String = "🙂",
    /** it-017 形象参考照（可选，全身照/头像均可）：导出长图顶部附给生图 Agent */
    val refImageFile: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class Item(
    val id: String,
    val personId: String,
    val category: WardrobeCategory,
    val name: String,
    val color: String = "",
    val desc: String = "",
    /** 相对 images/ 目录的文件名 */
    val imageFile: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class OutfitImage(
    val file: String,
    val addedAt: Long = 0L,
)

@Serializable
data class Outfit(
    val id: String,
    val personId: String,
    val itemIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val effectImages: List<OutfitImage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
enum class NoteParent { ITEM, OUTFIT }

@Serializable
data class Note(
    val id: String,
    val parentType: NoteParent,
    val parentId: String,
    val text: String,
    val createdAt: Long = 0L,
)

/** 数据快照根（SSOT），即 wardrobe.json 的结构 */
@Serializable
data class WardrobeData(
    val schemaVersion: Int = 1,
    val persons: List<Person> = emptyList(),
    val items: List<Item> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    val isEmpty: Boolean get() = persons.isEmpty() && items.isEmpty() && outfits.isEmpty()
}
