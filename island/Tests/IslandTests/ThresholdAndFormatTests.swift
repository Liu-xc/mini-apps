import Testing
import Foundation
@testable import Island

@Suite
struct ThresholdAndFormatTests {
    private func date(_ text: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = TimeZone.current
        return formatter.date(from: text)!
    }

    @Test
    func shortResetTodayShowsClockOtherwiseDate() {
        let now = date("2026-09-22 17:00:00")
        #expect(ResetFormatter.shortReset(date("2026-09-22 19:00:00"), now: now) == "19:00")
        #expect(ResetFormatter.shortReset(date("2026-09-28 09:30:00"), now: now) == "9月28日")
    }

    @Test
    func countdownWithin48HoursOnly() {
        let now = date("2026-09-22 17:00:00")
        #expect(ResetFormatter.countdown(date("2026-09-22 19:07:00"), now: now) == "2小时7分")
        #expect(ResetFormatter.countdown(date("2026-09-22 17:42:00"), now: now) == "42分")
        #expect(ResetFormatter.countdown(date("2026-09-25 17:00:00"), now: now) == nil)
        #expect(ResetFormatter.countdown(date("2026-09-22 16:00:00"), now: now) == nil)
    }

    @Test
    func relativeAge() {
        let now = date("2026-09-22 17:00:00")
        #expect(ResetFormatter.relativeAge(date("2026-09-22 17:00:10"), now: now) == "刚刚")
        #expect(ResetFormatter.relativeAge(date("2026-09-22 16:45:00"), now: now) == "15分钟前")
        #expect(ResetFormatter.relativeAge(date("2026-09-22 15:00:00"), now: now) == "2小时前")
        #expect(ResetFormatter.relativeAge(date("2026-09-19 17:00:00"), now: now) == "3天前")
    }

    /// US-4 健康度阈值（it-003 收编：≥50 绿 / 20–50 橙 / <20 红 / 无数据 灰白）
    @Test
    func healthLevelThresholds() {
        #expect(IslandTheme.level(of: 100) == .good)
        #expect(IslandTheme.level(of: 50) == .good)
        #expect(IslandTheme.level(of: 49.9) == .warn)
        #expect(IslandTheme.level(of: 20) == .warn)
        #expect(IslandTheme.level(of: 19.9) == .bad)
        #expect(IslandTheme.level(of: 0) == .bad)
        #expect(IslandTheme.level(of: nil) == .unknown)
    }

    /// 面板头重置文案：5 小时档精确到时钟，其余走日期；无重置时间返回 nil
    @Test
    func rowResetPicksFormatByKind() {
        let now = date("2026-09-22 17:00:00")
        let reset = date("2026-09-22 19:07:00")
        func row(_ kind: RowKind, _ resetDate: Date?) -> QuotaRow {
            QuotaRow(id: kind.rawValue, kind: kind, label: "", remainingPercent: 50,
                     resetDate: resetDate, percentInferred: false)
        }
        #expect(ResetFormatter.rowReset(row(.fiveHour, reset), now: now) == "19:07")
        #expect(ResetFormatter.rowReset(row(.weekly, reset), now: now) == ResetFormatter.shortReset(reset, now: now))
        #expect(ResetFormatter.rowReset(row(.mimo, nil), now: now) == nil)
    }
}
