import Foundation

enum QuotaParseError: LocalizedError {
    case envelope(String)
    case noLimits
    case malformed

    var errorDescription: String? {
        switch self {
        case .envelope(let message): message
        case .noLimits: "响应中没有 limits 配额数据"
        case .malformed: "响应不是合法 JSON"
        }
    }
}

/// 官方 monitor 接口无公开文档。字段名按社区工具（openusage / opencode-glm-quota 等）
/// 的用法整理，解析全部宽松：字段找不到就降级，不崩溃，并把原始 JSON 带回设置页。
/// M0 spike 拿到真实响应后收紧候选键。
enum QuotaResponseParser {
    static func parse(_ data: Data, host: String, now: Date = Date()) throws -> UsageSnapshot {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw QuotaParseError.malformed
        }
        if let success = obj["success"] as? Bool, !success {
            throw QuotaParseError.envelope(obj["msg"] as? String ?? "接口返回失败")
        }
        // bigmodel 网关风格：HTTP 200 + body code != 200 表示业务错误
        if let code = obj["code"] as? Int, code != 200, obj["success"] == nil {
            throw QuotaParseError.envelope(obj["msg"] as? String ?? "code \(code)")
        }
        let dataObj = obj["data"] as? [String: Any] ?? obj
        guard let limits = (dataObj["limits"] ?? obj["limits"]) as? [[String: Any]], !limits.isEmpty else {
            throw QuotaParseError.noLimits
        }
        let rows = limits.enumerated().map { index, dict in
            parseRow(dict, index: index)
        }
        return UsageSnapshot(
            rows: rows,
            fetchedAt: now,
            endpointHost: host,
            debugRawJSON: pretty(data)
        )
    }

    static func parseRow(_ dict: [String: Any], index: Int) -> QuotaRow {
        let rawLabel = firstString(dict, keys: ["windowType", "quotaType", "type", "name", "title", "tool", "planType"])
        let classified = classify(rawLabel)
        var kind = classified
        if let unitKind = classifyUnit(dict) {
            // 真实响应的档位类型靠 unit 区分（type 恒为 CREDIT_LIMIT）
            kind = unitKind
        } else if classified == .other, index < 3 {
            // 字段没给类型时按控制台顺序对号：5小时/每周/MCP
            kind = [.fiveHour, .weekly, .zcodeMcp][index]
        }
        let (remaining, inferred) = remainingPercent(in: dict)
        // id 必须行内唯一：unit 解析出的档位用 kind；未识别的按 index 兜底
        let id: String
        if classified == .other, kind != .other {
            id = kind.rawValue
        } else if classified == .other {
            id = "other-\(rawLabel ?? String(index))-\(index)"
        } else {
            id = classified.rawValue
        }
        return QuotaRow(
            id: id,
            kind: kind,
            label: displayLabel(kind, rawLabel),
            remainingPercent: remaining,
            resetDate: resetDate(in: dict),
            percentInferred: inferred || classified == .other
        )
    }

    /// spike 2026-09-23 实测：unit 3（配 number 5）= 5 小时窗口，unit 6（number 1）= 每周
    static func classifyUnit(_ dict: [String: Any]) -> RowKind? {
        guard let unit = double(dict["unit"]) else { return nil }
        switch unit {
        case 3: return .fiveHour
        case 6: return .weekly
        default: return nil
        }
    }

    static func classify(_ rawLabel: String?) -> RowKind {
        guard let raw = rawLabel?.lowercased() else { return .other }
        if raw.contains("mcp") { return .zcodeMcp }
        if raw.contains("week") { return .weekly }
        if raw.contains("5") || raw.contains("hour") || raw.contains("session") { return .fiveHour }
        return .other
    }

    static func displayLabel(_ kind: RowKind, _ raw: String?) -> String {
        switch kind {
        case .fiveHour: "5 小时"
        case .weekly: "每周"
        case .zcodeMcp: "ZCode MCP"
        case .other: raw ?? "配额"
        }
    }

    /// 剩余百分比。真实响应（spike 2026-09-23）字段语义：
    /// `currentValue`/`usage` = 已用/总额度的绝对 token 数（比值最精确，优先）；
    /// `percentage` = **已用**百分比的整数近似（有截断误差，仅兜底）；
    /// `remaining` = 绝对 token 数，绝不能当百分比读
    static func remainingPercent(in dict: [String: Any]) -> (Double?, Bool) {
        if let current = double(dict["currentValue"]), let usage = double(dict["usage"]), usage > 0 {
            let ratio = (usage - current) / usage * 100
            return (min(100, max(0, ratio)), true)
        }
        if let used = normalizedPercent(dict["percentage"]) {
            return (100 - used, true)
        }
        for key in ["remainingRatio", "remainingPercent", "remain", "left"] {
            if let value = normalizedPercent(dict[key]) {
                return (value, false)
            }
        }
        for key in ["used", "usedRatio", "consumed", "ratio"] {
            if let value = normalizedPercent(dict[key]) {
                return (100 - value, true)
            }
        }
        return (nil, false)
    }

    /// 0...1 视作比例，>1 视作已经是百分数
    static func normalizedPercent(_ value: Any?) -> Double? {
        guard let number = double(value), number >= 0, number <= 1000 else { return nil }
        let percent = number <= 1.0000001 ? number * 100 : number
        return min(100, max(0, percent))
    }

    static func resetDate(in dict: [String: Any]) -> Date? {
        for key in ["resetTime", "nextResetTime", "reset_time", "resetAt", "expireTime"] {
            guard let raw = dict[key] else { continue }
            if let date = dateValue(raw) { return date }
        }
        return nil
    }

    static func dateValue(_ raw: Any) -> Date? {
        if let number = double(raw) {
            // 13 位毫秒 vs 10 位秒
            let interval = number > 1_000_000_000_000 ? number / 1000 : number
            return Date(timeIntervalSince1970: interval)
        }
        guard let string = raw as? String else { return nil }
        for formatter in isoFormatters {
            if let date = formatter.date(from: string) { return date }
        }
        for formatter in Self.fallbackFormatters {
            if let date = formatter.date(from: string) { return date }
        }
        return nil
    }

    // MARK: - 私有工具

    private static func firstString(_ dict: [String: Any], keys: [String]) -> String? {
        for key in keys where dict[key] is String {
            return dict[key] as? String
        }
        return nil
    }

    private static func double(_ value: Any?) -> Double? {
        switch value {
        case let number as NSNumber: number.doubleValue
        case let string as String: Double(string)
        default: nil
        }
    }

    private static func pretty(_ data: Data) -> String? {
        guard let obj = try? JSONSerialization.jsonObject(with: data),
              let prettyData = try? JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys]),
              let text = String(data: prettyData, encoding: .utf8) else { return nil }
        return text.count > 8000 ? text.prefix(8000) + "\n…(截断)" : String(text)
    }

    private static var isoFormatters: [ISO8601DateFormatter] {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return [fractional, plain]
    }

    private static var fallbackFormatters: [DateFormatter] {
        ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss"].map { format in
            let formatter = DateFormatter()
            formatter.dateFormat = format
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = .current
            return formatter
        }
    }
}
