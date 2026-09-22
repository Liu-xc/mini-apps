import Foundation

/// 纯函数时间格式化（now 注入，便于固定时钟单测）
enum ResetFormatter {
    /// 今天 → "19:00"；否则 → "9月28日"
    static func shortReset(_ date: Date, now: Date = Date()) -> String {
        if Calendar.current.isDate(date, inSameDayAs: now) {
            return clockString(date)
        }
        let components = Calendar.current.dateComponents([.month, .day], from: date)
        return "\(components.month ?? 0)月\(components.day ?? 0)日"
    }

    /// 48 小时内的倒计时（"2小时7分" / "42分"），否则 nil
    static func countdown(_ date: Date, now: Date = Date()) -> String? {
        let minutes = Int(date.timeIntervalSince(now) / 60)
        guard minutes > 0, minutes < 48 * 60 else { return nil }
        let hours = minutes / 60
        let rest = minutes % 60
        return hours > 0 ? "\(hours)小时\(rest)分" : "\(rest)分"
    }

    /// "刚刚" / "15分钟前" / "2小时前" / "3天前"
    static func relativeAge(_ date: Date, now: Date = Date()) -> String {
        let seconds = Int(now.timeIntervalSince(date))
        guard seconds >= 0 else { return "刚刚" }
        if seconds < 45 { return "刚刚" }
        if seconds < 3600 { return "\(seconds / 60)分钟前" }
        if seconds < 86400 { return "\(seconds / 3600)小时前" }
        return "\(seconds / 86400)天前"
    }

    private static func clockString(_ date: Date) -> String {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }
}
