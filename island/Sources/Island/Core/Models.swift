import Foundation

enum RowKind: String, Codable, CaseIterable {
    case fiveHour
    case weekly
    case zcodeMcp
    case other
}

struct QuotaRow: Codable, Equatable, Identifiable {
    /// 解析时赋的稳定 id（other 行按标签区分）
    var id: String
    var kind: RowKind
    var label: String
    /// 剩余百分比 0...100；字段语义未 spike 校准前可能为 nil（UI 显示 --）
    var remainingPercent: Double?
    var resetDate: Date?
    /// 百分比是由「已用」字段反推的标记，spike 后可去掉
    var percentInferred: Bool
}

struct UsageSnapshot: Codable, Equatable {
    var rows: [QuotaRow]
    var fetchedAt: Date
    var endpointHost: String
    /// 原始响应（截断后），供设置页诊断与 spike 校准
    var debugRawJSON: String?

    func row(_ kind: RowKind) -> QuotaRow? {
        rows.first { $0.kind == kind }
    }

    /// UI 展示：MCP 档按需求不展示（Leo, it-001）；固定顺序 5 小时 → 每周 → 其余
    var displayRows: [QuotaRow] {
        let visible = rows.filter { $0.kind != .zcodeMcp }
        var result: [QuotaRow] = []
        for kind in [RowKind.fiveHour, .weekly] {
            if let matched = visible.first(where: { $0.kind == kind }) {
                result.append(matched)
            }
        }
        result.append(contentsOf: visible.filter { $0.kind != .fiveHour && $0.kind != .weekly })
        return result
    }
}
