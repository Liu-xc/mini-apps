package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Item

/** 单品域仓储（it-021 接口拆分） */
interface ItemRepository {
    /** 新增或更新（id 已存在则更新，updatedAt 刷新） */
    suspend fun upsertItem(item: Item)

    /** 级联：删图片文件、从所有穿搭 itemIds 与心愿穿搭移除 */
    suspend fun deleteItem(id: String)
}
