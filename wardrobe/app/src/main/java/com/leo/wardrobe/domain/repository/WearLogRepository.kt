package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.WearLog

/** 打卡域仓储（it-021 接口拆分，it-018） */
interface WearLogRepository {
    /** 打卡：为穿搭新增一条穿着记录（同日多套允许）；outfit 不存在时抛 IllegalArgumentException */
    suspend fun addWearLog(outfitId: String, at: Long): WearLog

    /** 撤销：删除该穿搭在 [from, to) 时间区间内的打卡记录 */
    suspend fun deleteWearLogsOf(outfitId: String, from: Long, to: Long)
}
