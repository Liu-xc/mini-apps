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
    func thresholdStates() {
        #expect(ThresholdState(remainingPercent: 100) == .healthy)
        #expect(ThresholdState(remainingPercent: 33) == .healthy)
        #expect(ThresholdState(remainingPercent: 20) == .warn)
        #expect(ThresholdState(remainingPercent: 1) == .warn)
        #expect(ThresholdState(remainingPercent: 0) == .exhausted)
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
}
