package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit

/** 心愿域仓储（it-021 接口拆分，it-019 双实体） */
interface WishRepository {
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
}
