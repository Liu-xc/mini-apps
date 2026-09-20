package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeData
import kotlinx.coroutines.flow.StateFlow

/**
 * 仓库接口（Repository 模式，specs/04-architecture.md）。
 * 实现负责：内存快照 SSOT + 原子持久化 + 数据不变量（specs/03-data-model.md 末节）。
 */
interface WardrobeRepository {
    /** 全量数据快照；任何写操作后自动广播 */
    val data: StateFlow<WardrobeData>

    /** 空库时创建默认角色「我」并返回（幂等） */
    suspend fun ensureDefaultPerson(): Person

    suspend fun addPerson(name: String, emoji: String): Person
    suspend fun updatePerson(id: String, name: String, emoji: String)

    /** it-017：设置/更换形象参考照（photoFile 为已导入的文件名；换照时旧文件物理删除） */
    suspend fun setPersonRefPhoto(id: String, photoFile: String)

    /** it-017：移除形象参考照（文件物理删除；未设置时为 no-op） */
    suspend fun removePersonRefPhoto(id: String)

    suspend fun deletePerson(id: String)

    /** 新增或更新（id 已存在则更新，updatedAt 刷新） */
    suspend fun upsertItem(item: Item)

    /** 级联：删图片文件、从所有穿搭 itemIds 移除 */
    suspend fun deleteItem(id: String)

    /** 创建穿搭（☆收藏 / 录入成品图时自动创建均走这里） */
    suspend fun createOutfit(personId: String, itemIds: List<String>, tags: List<String> = emptyList()): Outfit
    suspend fun updateOutfit(outfit: Outfit)
    /** 级联：删成品图文件与该穿搭的评论 */
    suspend fun deleteOutfit(id: String)

    suspend fun addEffectImage(outfitId: String, imageFile: String)
    suspend fun removeEffectImage(outfitId: String, imageFile: String)

    suspend fun addNote(parentType: NoteParent, parentId: String, text: String): Note
    suspend fun deleteNote(id: String)
}
