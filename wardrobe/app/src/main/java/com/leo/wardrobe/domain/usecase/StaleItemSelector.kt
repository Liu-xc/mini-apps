package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.WearLog

/** 单品穿着聚合（it-018 口径）：同一单品的穿着次数/最后穿着 = 其所在全部穿搭打卡的合并 */
data class ItemWearStat(
    val itemId: String,
    val personId: String,
    val wearCount: Int,
    val lastWornAt: Long,
)

/**
 * 「好久没穿」候选规则（it-021 自 ReminderScheduler 抽出为 domain 纯函数，JVM 可测）：
 * 穿过 ≥2 次且距最后穿着 ≥N 天、且单品仍存在。
 */
object StaleItemSelector {
    const val MIN_WEAR_COUNT = 2
    const val DAY_MS = 86_400_000L

    fun aggregateWearStats(items: List<Item>, outfits: List<Outfit>, wearLogs: List<WearLog>): List<ItemWearStat> {
        val outfitById = outfits.associateBy { it.id }
        return items
            .flatMap { item ->
                wearLogs.mapNotNull { log ->
                    val outfit = outfitById[log.outfitId] ?: return@mapNotNull null
                    if (item.id in outfit.itemIds) Triple(item.id, item.personId, log.at) else null
                }
            }
            .groupBy({ (itemId, personId, _) -> itemId to personId }, { it.third })
            .map { (key, times) ->
                ItemWearStat(
                    itemId = key.first,
                    personId = key.second,
                    wearCount = times.size,
                    lastWornAt = times.max(),
                )
            }
    }

    fun candidates(
        stats: List<ItemWearStat>,
        names: Map<String, String>,
        days: Int,
        now: Long,
    ): List<ItemWearStat> =
        stats.filter { s ->
            s.wearCount >= MIN_WEAR_COUNT &&
                s.lastWornAt > 0 &&
                now - s.lastWornAt >= days * DAY_MS &&
                names.containsKey(s.itemId)
        }
}
