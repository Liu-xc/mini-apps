import Foundation

protocol UsageProviding {
    func fetchSnapshot() async throws -> UsageSnapshot
}

struct ProviderError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// 按内容源拉取用量：GLM=API Key 双端点；MiMo=浏览器 Cookie 单端点
struct ProviderUsageFetcher: UsageProviding {
    let kind: ProviderKind
    var glmEndpointMode: EndpointMode = .auto
    var glmPreferredEndpoint: EndpointMode?

    func fetchSnapshot() async throws -> UsageSnapshot {
        switch kind {
        case .glm:
            return try await MonitorUsageProvider(
                endpointMode: glmEndpointMode,
                preferredEndpoint: glmPreferredEndpoint
            ).fetchSnapshot()
        case .mimo:
            return try await fetchMiMo()
        }
    }

    private func fetchMiMo() async throws -> UsageSnapshot {
        let cookie = CredentialStore.load(account: KeychainAccount.mimoCookie)
        guard !cookie.isEmpty else {
            throw ProviderError(message: "未配置 MiMo Cookie")
        }
        var request = URLRequest(url: URL(string: "https://platform.xiaomimimo.com/api/v1/tokenPlan/usage")!)
        request.timeoutInterval = 15
        request.setValue(cookie, forHTTPHeaderField: "Cookie")
        request.setValue("application/json", forHTTPHeaderField: "accept")
        request.setValue("https://platform.xiaomimimo.com/console/plan-manage", forHTTPHeaderField: "referer")
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36", forHTTPHeaderField: "user-agent")
        request.setValue("Asia/Shanghai", forHTTPHeaderField: "x-timezone")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ProviderError(message: "无效响应")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw ProviderError(message: "HTTP \(http.statusCode)")
        }
        if let text = String(data: data, encoding: .utf8), text.contains("\"code\":401") {
            throw ProviderError(message: "MiMo Cookie 已过期，请在设置更新")
        }
        return try MiMoQuotaParser.parse(data)
    }
}

/// 官方 monitor 接口客户端（GLM）。auto 模式按「记忆的偏好端点优先」逐个尝试。
struct MonitorUsageProvider: UsageProviding {
    let endpointMode: EndpointMode
    let preferredEndpoint: EndpointMode?

    func fetchSnapshot() async throws -> UsageSnapshot {
        let key = CredentialStore.load(account: KeychainAccount.glm)
        guard !key.isEmpty else {
            throw ProviderError(message: "未配置 GLM API Key")
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

/// 演示模式：不请求接口，用贴近控制台的假数据走查 UI 与状态机
struct DemoUsageProvider: UsageProviding {
    let kind: ProviderKind

    func fetchSnapshot() async throws -> UsageSnapshot {
        let now = Date()
        func at(_ interval: TimeInterval) -> Date { now.addingTimeInterval(interval) }
        let rows: [QuotaRow]
        switch kind {
        case .glm:
            rows = [
                QuotaRow(id: RowKind.fiveHour.rawValue, kind: .fiveHour, label: "5 小时",
                         remainingPercent: 33, resetDate: at(2 * 3600 + 7 * 60), percentInferred: false),
                QuotaRow(id: RowKind.weekly.rawValue, kind: .weekly, label: "每周",
                         remainingPercent: 18, resetDate: at(6 * 86400 + 3 * 3600), percentInferred: false),
            ]
        case .mimo:
            rows = [
                QuotaRow(id: "mimo-plan_total_token", kind: .mimo, label: "MiMo TOKEN",
                         remainingPercent: 96, resetDate: nil, percentInferred: false),
            ]
        }
        let raw = """
        {
          "demo": true,
          "note": "演示数据（\(kind.title)），用于无凭证走查",
          "realResponseShape": "配置凭证后，这里会显示真实接口 JSON"
        }
        """
        return UsageSnapshot(rows: rows, fetchedAt: now, endpointHost: "demo", debugRawJSON: raw)
    }
}
