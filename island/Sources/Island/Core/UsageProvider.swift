import Foundation

protocol UsageProviding {
    func fetchSnapshot() async throws -> UsageSnapshot
}

struct ProviderError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// 官方 monitor 接口客户端（GLM）。auto 模式按「记忆的偏好端点优先」逐个尝试；
/// 端点赢家回写由 UsageStore 负责（settings 持有，ADR-003）。
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

/// 小米 MiMo TOKEN Plan：浏览器 Cookie 认证单端点（it-002 spike 实测；
/// tp- 开头的 plan key 是模型调用 key，控制台接口只认登录态）
struct MiMoUsageProvider: UsageProviding {
    func fetchSnapshot() async throws -> UsageSnapshot {
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

/// 演示数据提供方：注册表注入假数据，走查 UI 与状态机（不请求接口）
struct StaticDemoProvider: UsageProviding {
    let kind: ProviderKind
    let rows: [QuotaRow]

    func fetchSnapshot() async throws -> UsageSnapshot {
        let raw = """
        {
          "demo": true,
          "note": "演示数据（\(ProviderRegistry.descriptor(kind).title)），用于无凭证走查",
          "realResponseShape": "配置凭证后，这里会显示真实接口 JSON"
        }
        """
        return UsageSnapshot(rows: rows, fetchedAt: Date(), endpointHost: "demo", debugRawJSON: raw)
    }
}
