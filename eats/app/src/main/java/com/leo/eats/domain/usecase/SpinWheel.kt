package com.leo.eats.domain.usecase

import com.leo.eats.domain.model.PlaceWithStats
import kotlin.random.Random

/**
 * 加权抽取（specs/04-architecture.md 决策引擎第 2/3 步，ADR-006）：
 * w = 1 + 距上次吃的天数（从未吃过按 30 计）——越久没吃越容易中，今天已吃过最低。
 * 纯函数 + 可注入 Random，分布可 JVM 单测。
 */
class SpinWheel {

    data class SpinPlan(
        val winner: PlaceWithStats,
        /** 供 WheelCanvas 的动画参数 */
        val turns: Int,
        val durationMillis: Int,
        /** 中奖扇区中心落点在指针处的额外随机偏移（度），避免每次都停正中 */
        val offsetDegrees: Float,
    )

    fun weightOf(lastVisitAt: Long?, now: Long): Double {
        if (lastVisitAt == null) return 1.0 + NEVER_EATEN_DAYS
        val days = ((now - lastVisitAt).coerceAtLeast(0L)).toDouble() / DAY_MILLIS
        return 1.0 + days
    }

    /** 按权重随机抽取；candidates 为空抛 IllegalArgumentException（UI 层保证 ≥1） */
    fun <T> pickWeighted(items: List<T>, weight: (T) -> Double, random: Random = Random.Default): T {
        require(items.isNotEmpty()) { "候选不能为空" }
        val weights = items.map(weight)
        val total = weights.sum()
        var point = random.nextDouble() * total
        items.forEachIndexed { i, item ->
            point -= weights[i]
            if (point <= 0) return item
        }
        return items.last()
    }

    fun plan(candidates: List<PlaceWithStats>, now: Long, random: Random = Random.Default): SpinPlan {
        val winner = pickWeighted(candidates, { weightOf(it.lastVisitAt, now) }, random)
        return SpinPlan(
            winner = winner,
            turns = random.nextInt(4, 7),
            durationMillis = random.nextInt(2600, 3600),
            offsetDegrees = random.nextFloat() * 6f - 3f,
        )
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        /** 从未吃过的等效天数（ADR-006） */
        const val NEVER_EATEN_DAYS = 30
    }
}
