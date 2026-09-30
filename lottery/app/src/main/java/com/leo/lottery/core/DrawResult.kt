package com.leo.lottery.core

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** 开奖结果。demo 期恒 demo=true，UI 必须露出「演示数据 · 非官方」标注（UC-2.5）。 */
@Serializable
data class DrawResult(
    val game: Game,
    val issue: String,
    val date: String,
    val zone1: List<Int>,
    val zone2: List<Int>,
    val demo: Boolean = true,
)

interface DrawRepository {
    /** 指定期号结果；期号未到开奖日（未来期）返回 null。 */
    fun result(game: Game, issue: String, today: LocalDate): DrawResult?
}

/**
 * 演示数据：SHA-256(game|issue) 种子确定性出号（ADR-003）。
 * 同 (game, issue) 恒同结果；真实 API 上线时换实现。
 */
class DemoDrawRepository : DrawRepository {

    override fun result(game: Game, issue: String, today: LocalDate): DrawResult? {
        val date = IssueCalendar.dateOfIssue(game, issue) ?: return null
        if (date.isAfter(today)) return null
        val rng = SplitMix64(SeedHash.seedLong("demo|$game|$issue".toByteArray(Charsets.UTF_8)))
        return DrawResult(
            game = game,
            issue = issue,
            date = date.toString(),
            zone1 = rng.sample(1, game.poolZone1 + 1, game.baseZone1),
            zone2 = rng.sample(1, game.poolZone2 + 1, game.baseZone2),
        )
    }
}
