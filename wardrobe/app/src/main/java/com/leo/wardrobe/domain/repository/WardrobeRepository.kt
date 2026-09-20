package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit
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

    // ---- 穿搭打卡（it-018） ----

    /** 打卡：为穿搭新增一条穿着记录（同日多套允许）；outfit 不存在时抛 IllegalArgumentException */
    suspend fun addWearLog(outfitId: String, at: Long): WearLog

    /** 撤销：删除该穿搭在 [from, to) 时间区间内的打卡记录 */
    suspend fun deleteWearLogsOf(outfitId: String, from: Long, to: Long)

    // ---- 心愿域（it-019） ----

    /** 新增或更新愿望单品（id 已存在则更新，updatedAt 刷新） */
    suspend fun upsertWishItem(item: WishItem)

    /** 删除愿望单品：级联删商品图、从所有心愿穿搭 wishItemIds 移除（因此变空的心愿穿搭一并删除） */
    suspend fun deleteWishItem(id: String)

    /** 购入转正：创建正式 Item + 回填 purchasedAt/purchasedItemId；含该愿望件的心愿穿搭自动把它移入 itemIds */
    suspend fun purchaseWishItem(wishItemId: String, item: Item): Item

    /** 保存心愿穿搭（组合去重不做在此，ViewModel 层判重后调用） */
    suspend fun createWishOutfit(
        personId: String,
        itemIds: List<String>,
        wishItemIds: List<String>,
        tags: List<String> = emptyList(),
    ): WishOutfit

    suspend fun updateWishOutfit(wishOutfit: WishOutfit)

    /** 删除心愿穿搭：previewImages 文件物理删除 */
    suspend fun deleteWishOutfit(id: String)

    suspend fun addPreviewImage(wishOutfitId: String, imageFile: String)
    suspend fun removePreviewImage(wishOutfitId: String, imageFile: String)

    /** 一键升级：wishItemIds 全部转正后转正式 Outfit（tags 继承、previewImages 移交 effectImages），心愿穿搭删除；未买齐抛 IllegalStateException */
    suspend fun promoteWishOutfit(id: String): Outfit

    suspend fun addNote(parentType: NoteParent, parentId: String, text: String): Note
    suspend fun deleteNote(id: String)
}
