package com.leo.lottery

import com.leo.lottery.core.DemoDrawRepository
import com.leo.lottery.core.Game
import com.leo.lottery.core.IssueCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueCalendarTest {

    private val wed = LocalDate.of(2026, 9, 30)   // 周三
    private val tue = LocalDate.of(2026, 9, 29)   // 周二
    private val sat = LocalDate.of(2026, 9, 26)   // 周六

    @Test
    fun `ssq draws tue thu sun`() {
        assertTrue(IssueCalendar.isDrawDay(Game.SSQ, tue))
        assertTrue(IssueCalendar.isDrawDay(Game.SSQ, LocalDate.of(2026, 10, 1))) // 周四
        assertFalse(IssueCalendar.isDrawDay(Game.SSQ, wed))
        assertFalse(IssueCalendar.isDrawDay(Game.SSQ, sat))
    }

    @Test
    fun `dlt draws mon wed sat`() {
        assertTrue(IssueCalendar.isDrawDay(Game.DLT, wed))
        assertTrue(IssueCalendar.isDrawDay(Game.DLT, sat))
        assertFalse(IssueCalendar.isDrawDay(Game.DLT, tue))
    }

    @Test
    fun `issue format and roundtrip`() {
        val issue = IssueCalendar.issueOn(Game.SSQ, tue)
        assertNotNull(issue)
        assertTrue(issue!!.matches(Regex("20\\d{5}")))
        assertEquals(tue, IssueCalendar.dateOfIssue(Game.SSQ, issue))
    }

    @Test
    fun `non draw day has no issue`() {
        assertNull(IssueCalendar.issueOn(Game.SSQ, wed))
        assertNull(IssueCalendar.issueOn(Game.DLT, tue))
    }

    @Test
    fun `latest issue falls back to last draw day`() {
        val ssqLatest = IssueCalendar.latestIssue(Game.SSQ, wed)
        assertEquals(tue, IssueCalendar.dateOfIssue(Game.SSQ, ssqLatest))
        val dltLatest = IssueCalendar.latestIssue(Game.DLT, wed)
        assertEquals(wed, IssueCalendar.dateOfIssue(Game.DLT, dltLatest))
    }

    @Test
    fun `future issue beyond year returns null date`() {
        assertNull(IssueCalendar.dateOfIssue(Game.SSQ, "2026999"))
        assertNull(IssueCalendar.dateOfIssue(Game.SSQ, "abcd123"))
    }

    @Test
    fun `demo result deterministic and bounded`() {
        val repo = DemoDrawRepository()
        val issue = IssueCalendar.latestIssue(Game.SSQ, wed)
        val a = repo.result(Game.SSQ, issue, wed)
        val b = repo.result(Game.SSQ, issue, wed)
        assertNotNull(a)
        assertEquals(a, b)
        assertEquals(6, a!!.zone1.size)
        assertEquals(1, a.zone2.size)
        assertEquals(a.zone1, a.zone1.sorted().distinct())
        assertTrue(a.zone1.all { it in 1..33 })
        assertTrue(a.zone2.all { it in 1..16 })
        assertTrue(a.demo)
    }

    @Test
    fun `future issue has no result`() {
        val repo = DemoDrawRepository()
        val issue = IssueCalendar.latestIssue(Game.DLT, wed)
        val date = IssueCalendar.dateOfIssue(Game.DLT, issue)!!
        val futureIssue = "%d%03d".format(date.year, issue.drop(4).toInt() + 1)
        assertNull(repo.result(Game.DLT, futureIssue, wed))
    }

    @Test
    fun `different issues differ`() {
        val repo = DemoDrawRepository()
        val issue = IssueCalendar.latestIssue(Game.SSQ, wed)
        val prevIssue = "%d%03d".format(issue.take(4).toInt(), issue.drop(4).toInt() - 1)
        val a = repo.result(Game.SSQ, issue, wed)!!
        val b = repo.result(Game.SSQ, prevIssue, wed)!!
        assertNotEquals(a.zone1, b.zone1)
    }
}
