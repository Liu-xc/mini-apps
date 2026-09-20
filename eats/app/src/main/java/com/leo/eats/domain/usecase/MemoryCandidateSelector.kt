package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.PlaceWithStats
import kotlin.random.Random

/**
 * 「好久没去」候选选取（it-007 阶段B，specs US-11）：
 * 只推喜欢的老店——去过 ≥3 次、评分 ≥4（无综合评分回退 Visit 均分）、距上次 ≥N 天。
 * 每日最多推 1 条由调度侧保证；上次提醒过的店在其余候选用尽前不重复推。
 */
object MemoryCandidateSelector {
    const val MIN_VISITS = 3
    const val MIN_RATING = 4.0
    const val DAY_MS = 86_400_000L

    /** 有效评分 = 综合评分优先，缺失回退 Visit 均分，两者皆无视为不满足 */
    fun PlaceWithStats.effectiveRating(): Double? =
        place.rating?.toDouble() ?: avgVisitRating

    fun candidates(stats: List<PlaceWithStats>, days: Int, now: Long): List<PlaceWithStats> =
        stats.filter { s ->
            s.visitCount >= MIN_VISITS &&
                (s.effectiveRating() ?: 0.0) >= MIN_RATING &&
                s.lastVisitAt != null &&
                now - s.lastVisitAt >= days * DAY_MS
        }

    /** 候选为空返回 null；上一家仍在候选里但还有别的选择时先让位 */
    fun pick(
        candidates: List<PlaceWithStats>,
        lastNotifiedPlaceId: String?,
        random: Random = Random,
    ): PlaceWithStats? {
        if (candidates.isEmpty()) return null
        val pool = candidates.filterNot { it.place.id == lastNotifiedPlaceId }.ifEmpty { candidates }
        return pool.random(random)
    }
}
