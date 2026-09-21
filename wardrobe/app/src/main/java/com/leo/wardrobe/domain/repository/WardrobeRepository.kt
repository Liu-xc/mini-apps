package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.WardrobeData
import kotlinx.coroutines.flow.StateFlow

/**
 * 仓库门面（Repository 模式，specs/04-architecture.md）。
 * it-021 按聚合域拆分为 Person/Item/Outfit/WearLog/Wish/Note 六个子接口；
 * 本接口聚合全部域并暴露数据快照——现有调用方（AppViewModel/Mock/测试）不受影响，
 * 新代码应按需依赖最窄的子接口。
 */
interface WardrobeRepository :
    PersonRepository,
    ItemRepository,
    OutfitRepository,
    WearLogRepository,
    WishRepository,
    NoteRepository {
    /** 全量数据快照；任何写操作后自动广播 */
    val data: StateFlow<WardrobeData>

    /** it-024 数据包导入终步：整体替换快照（已清洗）并单事务落盘——合并/替换模式共用 */
    suspend fun replaceAll(data: WardrobeData)
}
