import Foundation

protocol UsageProviding {
    func fetchSnapshot() async throws -> UsageSnapshot
}

struct ProviderError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// 官方 monitor 接口客户端。auto 模式按「记忆的偏好端点优先」逐个尝试。
struct MonitorUsageProvider: UsageProviding {
    let endpointMode: EndpointMode
    let preferredEndpoint: EndpointMode?

    func fetchSnapshot() async throws -> UsageSnapshot {
        let key = KeychainStore.loadAPIKey()
        guard !key.isEmpty else {
            throw ProviderError(message: "未配置 API Key")
        }
        let candidates: [EndpointMode]
        switch endpointMode {
        case .bigmodel: candidates = [.bigmodel]
        case .zai: candidates = [.zai]
        case .auto:
            candidates = preferredEndpoint == .zai ? [.zai, .bigmodel] : [.bigmodel, .zai]
        }
        var lastError: Error = ProviderError(message: "没有可用端点")
        for mode in candidates {
            do {
                return try await fetchOne(mode, key: key)
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private func fetchOne(_ mode: EndpointMode, key: String) async throws -> UsageSnapshot {
        var request = URLRequest(url: mode.usageLimitURL)
        request.timeoutInterval = 15
        // 官方 monitor 接口鉴权头直接放 Key，不带 Bearer 前缀
        request.setValue(key, forHTTPHeaderField: "Authorization")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ProviderError(message: "无效响应")
        }
        switch http.statusCode {
        case 200..<300:
            break
        case 401, 403:
            throw ProviderError(message: "API Key 无效或已过期（HTTP \(http.statusCode)）")
        default:
            throw ProviderError(message: "HTTP \(http.statusCode)")
        }
        return try QuotaResponseParser.parse(data, host: mode.baseURL)
    }
}

/// 演示模式：不请求接口，用贴近控制台截图的三档假数据走查 UI 与状态机
struct DemoUsageProvider: UsageProviding {
    func fetchSnapshot() async throws -> UsageSnapshot {
        let now = Date()
        func at(_ interval: TimeInterval) -> Date { now.addingTimeInterval(interval) }
        let rows = [
            QuotaRow(
                id: RowKind.fiveHour.rawValue,
                kind: .fiveHour,
                label: "5 小时",
                remainingPercent: 33,
                resetDate: at(2 * 3600 + 7 * 60),
                percentInferred: false
            ),
            QuotaRow(
                id: RowKind.weekly.rawValue,
                kind: .weekly,
                label: "每周",
                remainingPercent: 62,
                resetDate: at(6 * 86400 + 3 * 3600),
                percentInferred: false
            ),
            QuotaRow(
                id: RowKind.zcodeMcp.rawValue,
                kind: .zcodeMcp,
                label: "ZCode MCP",
                remainingPercent: 4,
                resetDate: at(26 * 3600),
                percentInferred: false
            ),
        ]
        let raw = """
        {
          "demo": true,
          "note": "演示数据：33% / 62% / 4%（MCP 处于警戒态），用于无 Key 走查",
          "realResponseShape": "首次保存 Key 后，这里会显示 /api/monitor/usage/quota/limit 的真实 JSON"
        }
        """
        return UsageSnapshot(rows: rows, fetchedAt: now, endpointHost: "demo", debugRawJSON: raw)
    }
}
