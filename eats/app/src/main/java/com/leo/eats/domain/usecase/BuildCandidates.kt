package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceWithStats

/**
 * 抽签候选过滤（specs/04-architecture.md 决策引擎第 1 步，US-07/US-11）。
 * 纯函数，JVM 单测覆盖。it-008 增加分类过滤与「只抽愿望」。
 */
data class SpinFilter(
    val categories: Set<PlaceCategory> = PlaceCategory.entries.toSet(),
    val kinds: Set<PlaceKind> = PlaceKind.entries.toSet(),
    /** 忌口标签：含任一标签的食堂不进候选 */
    val excludedTags: Set<String> = emptySet(),
    /** 排除最近 N 天吃过的；null = 关闭 */
    val excludeRecentDays: Int? = 14,
    /** 只抽愿望（it-008）：候选池限定 wishlistedAt != null（均未去过，「排除最近」对其天然无作用） */
    val wishOnly: Boolean = false,
)

class BuildCandidates {
    operator fun invoke(places: List<PlaceWithStats>, filter: SpinFilter, now: Long): List<PlaceWithStats> {
        val recentCutoff = filter.excludeRecentDays?.let { days -> now - days * DAY_MILLIS }
        return places.filter { s ->
            s.place.category in filter.categories &&
                s.place.kind in filter.kinds &&
                (!filter.wishOnly || s.place.isWish) &&
                s.place.tags.none { it in filter.excludedTags } &&
                (recentCutoff == null || (s.lastVisitAt ?: Long.MIN_VALUE) < recentCutoff)
        }
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
