import Foundation

/// 内容源：灵岛是通用入口，每个 ProviderKind 是一个可接入的 TOKEN 厂商/套餐（ADR-008）
enum ProviderKind: String, Codable, CaseIterable, Identifiable {
    case glm
    case mimo

    var id: String { rawValue }

    var title: String {
        switch self {
        case .glm: "GLM"
        case .mimo: "MiMo"
        }
    }
}

enum RowKind: String, Codable, CaseIterable {
    case fiveHour
    case weekly
    case zcodeMcp
    case mimo
    case other
}

struct QuotaRow: Codable, Equatable, Identifiable {
    /// 解析时赋的稳定 id（同 kind 多行按来源 name 区分）
    var id: String
    var kind: RowKind
    var label: String
    /// 剩余百分比 0...100；解析不出为 nil（UI 显示 --）
    var remainingPercent: Double?
    var resetDate: Date?
    /// 百分比是由「已用」字段反推的标记
    var percentInferred: Bool
    /// 已用 / 额度的绝对 token 数（MiMo 展示 billion 用；nil=不展示）
    var usedTokens: Double? = nil
    var limitTokens: Double? = nil
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
