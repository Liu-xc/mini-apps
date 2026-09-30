package com.leo.lottery.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 开奖日历：SSQ 二/四/日，DLT 一/三/六；期号 = yyyy + 年内第 N 个开奖日（%03d）。
 */
object IssueCalendar {

    fun drawWeekdays(game: Game): Set<DayOfWeek> = when (game) {
        Game.SSQ -> setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        Game.DLT -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
    }

    /** 该日是否开奖日。 */
    fun isDrawDay(game: Game, date: LocalDate): Boolean =
        date.dayOfWeek in drawWeekdays(game)

    /** 该日期的期号；非开奖日返回 null。 */
    fun issueOn(game: Game, date: LocalDate): String? {
        if (!isDrawDay(game, date)) return null
        var seq = 0
        var d = LocalDate.of(date.year, 1, 1)
        while (!d.isAfter(date)) {
            if (isDrawDay(game, d)) seq++
            d = d.plusDays(1)
        }
        return "%d%03d".format(date.year, seq)
    }

    /** 最新一期（≤ today 的最近开奖日；today 是开奖日即 today）。 */
    fun latestIssue(game: Game, today: LocalDate): String {
        var d = today
        while (true) {
            issueOn(game, d)?.let { return it }
            d = d.minusDays(1)
        }
    }

    /** 期号回推出开奖日；期号在本年超出总开奖日数返回 null。 */
    fun dateOfIssue(game: Game, issue: String): LocalDate? {
        val year = issue.take(4).toIntOrNull() ?: return null
        val seq = issue.drop(4).toIntOrNull() ?: return null
        if (seq < 1) return null
        var count = 0
        var d = LocalDate.of(year, 1, 1)
        while (d.year == year) {
            if (isDrawDay(game, d)) {
                count++
                if (count == seq) return d
            }
            d = d.plusDays(1)
        }
        return null
    }

    /** 该期是否已到开奖日（≤ today）。 */
    fun isIssueOpen(game: Game, issue: String, today: LocalDate): Boolean {
        val date = dateOfIssue(game, issue) ?: return false
        return !date.isAfter(today)
    }
}
