import Foundation

/// it-004：MiMo 登录态（会话）生命周期状态，按源持久化在 UserDefaults（`sessionState.<kind>`）。
/// - none：从未配置过该源的登录态
/// - manual：用户手动粘贴 Cookie（无 App 内账号会话，过期需再粘贴）
/// - active：App 内完成过小米账号登录——serviceToken 过期可走静默续期
/// - expired：静默续期发现账号会话也失效，停止自动重试，等用户重新登录
enum SessionState: String, Codable, CaseIterable {
    case none, manual, active, expired
}

/// MiMo 平台 SSO 纯工具（it-004）：命名空间收敛 host/URL/解析规则，便于单测。
enum MimoSSO {
    static let platformHost = "platform.xiaomimimo.com"
    static let accountHost = "account.xiaomi.com"

    /// 静默续期/登录窗的兜底起始页：控制台页本身会 302 到 SSO（带新鲜 sign），
    /// 比手工拼 serviceLogin 回调更稳（401 body 缺 loginUrl 时用它）
    static var consoleURL: URL {
        URL(string: "https://\(platformHost)/console/plan-manage")!
    }

    /// 是否已落在小米开放平台域（sts 回调与控制台页都算成功着陆）
    static func isPlatformURL(_ url: URL?) -> Bool {
        url?.host == platformHost
    }

    /// 是否停在账号登录页（账号会话失效的判定信号）
    static func isAccountLoginURL(_ url: URL?) -> Bool {
        url?.host == accountHost
    }

    /// 从 401 响应体取 SSO 登录 URL（`{"code":401,"loginUrl":"…"}`；缺省返回 nil）
    static func loginURL(from401Body data: Data) -> URL? {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let raw = obj["loginUrl"] as? String else { return nil }
        return URL(string: raw)
    }

    /// WKWebsiteDataStore 里的 Cookie → 请求头字符串（只取小米开放平台域，串起浏览器同款整段）
    static func cookieHeader(from cookies: [HTTPCookie]) -> String {
        cookies
            .filter { $0.domain.contains("xiaomimimo.com") }
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "; ")
    }

    /// 续期成功的硬标准：必须拿到非空 serviceToken（光着陆平台页不算）
    static func hasServiceToken(_ header: String) -> Bool {
        guard let range = header.range(of: "api-platform_serviceToken=") else { return false }
        let rest = header[range.upperBound...]
        let value = rest.prefix(while: { $0 != ";" && $0 != " " })
        return !value.isEmpty
    }
}
