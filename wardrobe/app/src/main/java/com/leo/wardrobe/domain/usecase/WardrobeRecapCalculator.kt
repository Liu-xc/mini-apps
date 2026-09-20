package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 统计档位（it-018）：今年 / 累计 */
sealed interface WardrobeRecapRange {
    data class Year(val year: Int) : WardrobeRecapRange
    data object All : WardrobeRecapRange
}

data class VersatileItem(
    val item: Item,
    /** 进入的穿搭数（结构属性，全量） */
    val outfitCount: Int,
    /** 这些穿搭的累计打卡次数 */
    val wearCount: Int,
)

data class TopWornOutfit(val outfit: Outfit, val wearCount: Int, val lastWornAt: Long)

data class WardrobeRecapStats(
    val personName: String,
    val personEmoji: String,
    /** 现有单品数（存量，不随档位变） */
    val itemCount: Int,
    /** 现有穿搭套数（存量） */
    val outfitCount: Int,
    /** 档位内打卡次数 */
    val wearCount: Int,
    /** 最百搭 TOP3：按进入穿搭数，并列取打卡多者（结构属性，全量） */
    val topVersatile: List<VersatileItem>,
    /** 品类分布（现有单品） */
    val categoryCounts: Map<WardrobeCategory, Int>,
    /** 利用率 = 出现过任一「被穿过」穿搭的单品占比（结构属性，全量） */
    val utilization: Double,
    /** 闲置清单：从没上过身的单品，按拥有时间最早优先 */
    val idleItems: List<Item>,
    /** 档内出勤最高穿搭 */
    val topOutfit: TopWornOutfit?,
    /** 连续打卡天数（截至今天/昨天） */
    val currentStreak: Int,
    /** 档内打卡关联穿搭的成品图，addedAt 正序去重 ≤9（长图照片墙） */
    val photoFiles: List<String>,
) {
    val hasWearData: Boolean get() = wearCount > 0
}

/**
 * 衣橱统计回顾聚合（it-018 阶段B，specs W9）：纯函数无副作用。
 * 口径约定：单品/穿搭数与品类分布、利用率、闲置、最百搭为「衣橱结构」指标（全量）；
 * 打卡次数、出勤最高穿搭、照片墙为「穿着行为」指标（随档位过滤）。
 */
fun WardrobeData.wardrobeRecap(
    personId: String,
    range: WardrobeRecapRange,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): WardrobeRecapStats {
    val person = persons.firstOrNull { it.id == personId }
    val items = items.filter { it.personId == personId }
    val outfits = outfits.filter { it.personId == personId }
    val personLogs = wearLogs.filter { it.personId == personId }

    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val (startExcl, endIncl) = when (range) {
        is WardrobeRecapRange.Year -> Pair(
            LocalDate.of(range.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            LocalDate.of(range.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        WardrobeRecapRange.All -> Pair(
            Long.MIN_VALUE,
            LocalDate.from(Instant.ofEpochMilli(now).atZone(zone)).plusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
    val logsInRange = personLogs.filter { it.at >= startExcl && it.at < endIncl }

    // ---- 衣橱结构（全量）：百搭 / 利用率 / 闲置 ----
    val outfitWearCounts = personLogs.groupBy { it.outfitId }.mapValues { (_, l) -> l.size }
    val wornOutfitIds = outfitWearCounts.filterValues { it > 0 }.keys
    val wornItemIds = outfits.filter { it.id in wornOutfitIds }.flatMap { it.itemIds }.toSet()

    val topVersatile = items.mapNotNull { item ->
        val containing = outfits.filter { item.id in it.itemIds }
        if (containing.isEmpty()) return@mapNotNull null
        val wearSum = containing.sumOf { outfitWearCounts[it.id] ?: 0 }
        VersatileItem(item, containing.size, wearSum)
    }.sortedWith(
        compareByDescending<VersatileItem> { it.outfitCount }
            .thenByDescending { it.wearCount }
            .thenBy { it.item.name },
    ).take(3)

    val idleItems = items.filterNot { it.id in wornItemIds }.sortedBy { it.createdAt }
    val utilization = if (items.isEmpty()) 0.0 else (items.size - idleItems.size).toDouble() / items.size

    // ---- 穿着行为（档内）：出勤最高 / 照片墙 ----
    val rangeCounts = logsInRange.groupBy { it.outfitId }
    val topOutfit = rangeCounts.maxByOrNull { (_, l) -> l.size }?.let { (outfitId, l) ->
        outfits.firstOrNull { it.id == outfitId }?.let {
            TopWornOutfit(it, l.size, l.maxOf { log -> log.at })
        }
    }
    val outfitById = outfits.associateBy { it.id }
    val photoFiles = logsInRange.sortedBy { it.at }
        .flatMap { outfitById[it.outfitId]?.effectImages.orEmpty() }
        .distinctBy { it.file }
        .map { it.file }
        .take(9)

    // ---- 连续打卡天数（全量行为） ----
    val visitDays = personLogs.mapTo(HashSet()) { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    var cursor = if (today in visitDays) today else today.minusDays(1)
    var streak = 0
    while (cursor in visitDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    return WardrobeRecapStats(
        personName = person?.name ?: "",
        personEmoji = person?.emoji ?: "🙂",
        itemCount = items.size,
        outfitCount = outfits.size,
        wearCount = logsInRange.size,
        topVersatile = topVersatile,
        categoryCounts = WardrobeCategory.entries.associateWith { c -> items.count { it.category == c } },
        utilization = utilization,
        idleItems = idleItems,
        topOutfit = topOutfit,
        currentStreak = streak,
        photoFiles = photoFiles,
    )
}
