import Foundation

/// 小米 MiMo TOKEN Plan 解析器（spike 2026-09-23 实测结构）：
/// data.usage / data.monthUsage 内 items[]，percent 为**小数比例**（0.0118 = 已用 1.18%），
/// limit=0 的条目（如未购买的补偿包）跳过；无重置时间字段。
enum MiMoQuotaParser {
    static func parse(_ data: Data, now: Date = Date()) throws -> UsageSnapshot {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw QuotaParseError.malformed
        }
        if let code = obj["code"] as? Int, code != 0 {
            throw QuotaParseError.envelope(obj["message"] as? String ?? "code \(code)")
        }
        guard let dataObj = obj["data"] as? [String: Any] else {
            throw QuotaParseError.noLimits
        }

        var rows: [QuotaRow] = []
        // usage = 套餐当期窗口（主档）；monthUsage 与其通常一致，仅在 usage 缺失时兜底
        for blockKey in ["usage", "monthUsage"] {
            guard let block = dataObj[blockKey] as? [String: Any],
                  let items = block["items"] as? [[String: Any]] else { continue }
            for item in items {
                guard let limit = double(item["limit"]), limit > 0,
                      let percent = double(item["percent"]) else { continue }
                let name = item["name"] as? String ?? ""
                let remaining = min(100, max(0, (1 - percent) * 100))
                rows.append(QuotaRow(
                    id: "mimo-\(name)",
                    kind: .mimo,
                    label: displayLabel(for: name),
                    remainingPercent: remaining,
                    resetDate: nil,
                    percentInferred: false,
                    usedTokens: double(item["used"]),
                    limitTokens: limit
                ))
                break   // 每个块取主档一条
            }
            if !rows.isEmpty { break }
        }
        guard !rows.isEmpty else { throw QuotaParseError.noLimits }

        return UsageSnapshot(
            rows: rows,
            fetchedAt: now,
            endpointHost: "platform.xiaomimimo.com",
            debugRawJSON: pretty(data)
        )
    }

    /// it-003：主档改叫「套餐」——面板头已标源名（MiMo），环心/菜单不再重复厂商名
    static func displayLabel(for name: String) -> String {
        switch name {
        case "plan_total_token": "套餐"
        case "month_total_token": "MiMo 当月"
        case "compensation_total_token": "MiMo 补偿包"
        default: "套餐"
        }
    }

    private static func double(_ value: Any?) -> Double? {
        JSONLoose.double(value)
    }

    private static func pretty(_ data: Data) -> String? {
        JSONLoose.pretty(data)
    }
}
