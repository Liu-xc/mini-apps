package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats

/**
 * 转盘候选过滤（specs/04-architecture.md 决策引擎第 1 步，US-07）。
 * 纯函数，JVM 单测覆盖。
 */
data class SpinFilter(
    val kinds: Set<PlaceKind> = PlaceKind.entries.toSet(),
    /** 忌口标签：含任一标签的食堂不进候选 */
    val excludedTags: Set<String> = emptySet(),
    /** 排除最近 N 天吃过的；null = 关闭 */
    val excludeRecentDays: Int? = 14,
)

class BuildCandidates {
    operator fun invoke(places: List<PlaceWithStats>, filter: SpinFilter, now: Long): List<PlaceWithStats> {
        val recentCutoff = filter.excludeRecentDays?.let { days -> now - days * DAY_MILLIS }
        return places.filter { s ->
            s.place.kind in filter.kinds &&
                s.place.tags.none { it in filter.excludedTags } &&
                (recentCutoff == null || (s.lastVisitAt ?: Long.MIN_VALUE) < recentCutoff)
        }
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
