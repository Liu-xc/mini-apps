package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.placeById
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 统计回顾聚合（it-007，specs W7）：纯函数、无副作用，输入快照输出全部指标，
 * 与 ADR-008「派生不落盘」同思路。时间桶/连续天数依赖时区，测试显式传入。
 */
sealed interface RecapRange {
    data class Year(val year: Int) : RecapRange
    data object All : RecapRange
}

data class RecapTopPlace(val place: Place, val count: Int, val avgRating: Double?)

data class RecapMonth(val label: String, val count: Int)

data class RecapStats(
    /** 档位内总记录数 */
    val totalVisits: Int,
    /** 总花费；档位内没有任何花费记录时为 null */
    val totalCost: Double?,
    /** 吃过的食堂数（档位内 visitCount>=1） */
    val placesVisited: Int,
    /** 最爱 TOP3：按次数，并列取评分高者，再按名称 */
    val topPlaces: List<RecapTopPlace>,
    /** 类型分布（按 Visit 计，含 0 值三键） */
    val kindCounts: Map<PlaceKind, Int>,
    /** 恒 12 桶：Year = 该年 1–12 月；All = 截至(含)当月的近 12 个月 */
    val monthly: List<RecapMonth>,
    /** 连续记录天数：从今天起往回数（今天没有则从昨天起） */
    val currentStreak: Int,
    /** 档位内 Visit 评分均分，无评分则 null */
    val avgRating: Double?,
    /** 档位内 Visit 照片，时间正序，≤9（长图照片墙） */
    val photoFiles: List<String>,
) {
    val hasData: Boolean get() = totalVisits > 0
}

fun EatsData.recap(range: RecapRange, now: Long, zone: ZoneId = ZoneId.systemDefault()): RecapStats {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val (startExcl, endIncl, months) = when (range) {
        is RecapRange.Year -> Triple(
            LocalDate.of(range.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            LocalDate.of(range.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            (1..12).map { YearMonth.of(range.year, it) },
        )
        RecapRange.All -> Triple<Long?, Long, List<YearMonth>>(
            null,
            YearMonth.from(today).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) },
        )
    }

    val inRange = visits.filter { it.at >= (startExcl ?: Long.MIN_VALUE) && it.at < endIncl }
    val byPlaceId = inRange.groupBy { it.placeId }
    val topPlaces = byPlaceId.mapNotNull { (id, vs) ->
        val place = placeById(id) ?: return@mapNotNull null
        val rated = vs.mapNotNull { it.rating }
        RecapTopPlace(place, vs.size, rated.takeIf { it.isNotEmpty() }?.let { l -> l.sum().toDouble() / l.size })
    }.sortedWith(
        compareByDescending<RecapTopPlace> { it.count }
            .thenByDescending { it.avgRating ?: 0.0 }
            .thenBy { it.place.name },
    ).take(3)

    val kindCounts = PlaceKind.entries.associateWith { kind ->
        inRange.count { v -> placeById(v.placeId)?.kind == kind }
    }

    val costs = inRange.mapNotNull { it.cost }
    val rated = inRange.mapNotNull { it.rating }

    val visitDays = inRange.mapTo(HashSet()) { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    var cursor = if (today in visitDays) today else today.minusDays(1)
    var streak = 0
    while (cursor in visitDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    val monthly = months.map { ym ->
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        RecapMonth("${ym.monthValue}月", inRange.count { it.at >= from && it.at < to })
    }

    return RecapStats(
        totalVisits = inRange.size,
        totalCost = costs.takeIf { it.isNotEmpty() }?.sumOf { it },
        placesVisited = byPlaceId.size,
        topPlaces = topPlaces,
        kindCounts = kindCounts,
        monthly = monthly,
        currentStreak = streak,
        avgRating = rated.takeIf { it.isNotEmpty() }?.let { l -> l.sum().toDouble() / l.size },
        photoFiles = inRange.sortedBy { it.at }.flatMap { it.photos }.take(9),
    )
}
