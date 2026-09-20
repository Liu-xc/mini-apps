package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Outfit

/** 穿搭域仓储（it-021 接口拆分） */
interface OutfitRepository {
    /** 创建穿搭（☆收藏 / 录入成品图时自动创建均走这里） */
    suspend fun createOutfit(personId: String, itemIds: List<String>, tags: List<String> = emptyList()): Outfit
    suspend fun updateOutfit(outfit: Outfit)

    /** 级联：删成品图文件与该穿搭的评论 */
    suspend fun deleteOutfit(id: String)

    suspend fun addEffectImage(outfitId: String, imageFile: String)
    suspend fun removeEffectImage(outfitId: String, imageFile: String)
}
