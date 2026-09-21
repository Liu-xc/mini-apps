package com.leo.eats.domain.repository

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.Visit
import kotlinx.coroutines.flow.StateFlow

/**
 * 仓库接口（Repository 模式，specs/04-architecture.md）。
 * 实现负责：内存快照 SSOT + 原子持久化 + 数据不变量（specs/03-data-model.md 末节）。
 */
interface EatsRepository {
    /** 全量数据快照；任何写操作后自动广播 */
    val data: StateFlow<EatsData>

    /** it-012 数据包导入终步：整体替换快照（已清洗）并单事务落盘——合并/替换模式共用 */
    suspend fun replaceAll(data: EatsData)

    /** 新增或更新（id 已存在则更新，updatedAt 刷新）；links 归一化：trim、去空、按 url 去重 */
    suspend fun upsertPlace(place: Place)

    /** 级联：删除其全部 Visit；双方 photos 文件物理删除 */
    suspend fun deletePlace(id: String)

    suspend fun addVisit(visit: Visit)

    /** 级联：其 photos 物理删除，Place 保留 */
    suspend fun deleteVisit(id: String)
}
