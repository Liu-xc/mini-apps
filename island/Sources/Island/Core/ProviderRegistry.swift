import Foundation

/// it-004：登录态续期能力（nil = 该源只走静态凭证，如 GLM API Key）
struct SessionLoginConfig {
    /// 登录窗与静默续期的起始页（会 302 到小米账号 SSO）
    let startURL: URL
}

/// 内容源注册条目（it-003 ADR-009）。凭证、面板、设置分区、菜单摘要全部由本表驱动。
struct ProviderDescriptor: Identifiable {
    let kind: ProviderKind
    /// 面板头 / 菜单分组标题（"GLM" / "MiMo"）
    let title: String
    /// 设置页分区标题（"GLM Coding Plan" / "小米 MiMo TOKEN Plan"）
    let sectionTitle: String
    /// 凭证存储账户（credentials.json 的 key）
    let credentialAccount: String
    /// 凭证字段名（SecureField 占位："API Key" / "Cookie 字符串"）
    let credentialLabel: String
    /// 状态行里的凭证名词（"Key" / "Cookie"——"Key 已存入本机"）
    let credentialNoun: String
    /// 设置分区底部说明
    let credentialHint: String
    /// 未配置时面板内提示（"未配置 API Key" / "未配置 Cookie"）
    let unconfiguredText: String
    /// it-004：支持账号登录自动续期时的配置（nil = 不支持）
    let sessionLogin: SessionLoginConfig?
    /// 真实数据提供方（凭证与端点由内部自取）
    let makeProvider: @MainActor (AppSettings) -> any UsageProviding
    /// 演示数据（now 注入，重置时刻相对当前）
    let demoRows: (Date) -> [QuotaRow]

    var id: ProviderKind { kind }
}

/// 内容源注册表（ADR-009）：**新增源 = ProviderKind case + 本表一条 +（新格式才需）解析器**。
/// 卡片面板、设置分区、菜单摘要、凭证路径全部遍历本表，无按源硬编码 switch。
enum ProviderRegistry {
    static let all: [ProviderDescriptor] = [glm, mimo]

    static func descriptor(_ kind: ProviderKind) -> ProviderDescriptor {
        guard let found = all.first(where: { $0.kind == kind }) else {
            preconditionFailure("ProviderRegistry 缺少 \(kind.rawValue) 的注册条目")
        }
        return found
    }

    // MARK: - GLM Coding Plan（API Key 双端点）

    private static let glm = ProviderDescriptor(
        kind: .glm,
        title: "GLM",
        sectionTitle: "GLM Coding Plan",
        credentialAccount: KeychainAccount.glm,
        credentialLabel: "API Key",
        credentialNoun: "Key",
        credentialHint: "官方用量接口 /api/monitor/usage/quota/limit 仅查询、不消耗套餐额度；凭证只存本机 0600 权限限制文件，不入仓库/日志。",
        unconfiguredText: "未配置 API Key",
        sessionLogin: nil,
        makeProvider: { settings in
            MonitorUsageProvider(
                endpointMode: settings.endpointMode,
                preferredEndpoint: settings.preferredEndpoint
            )
        },
        demoRows: { now in
            [
                QuotaRow(id: RowKind.fiveHour.rawValue, kind: .fiveHour, label: "5 小时",
                         remainingPercent: 33, resetDate: now.addingTimeInterval(2 * 3600 + 7 * 60),
                         percentInferred: false),
                QuotaRow(id: RowKind.weekly.rawValue, kind: .weekly, label: "每周",
                         remainingPercent: 18, resetDate: now.addingTimeInterval(6 * 86400 + 3 * 3600),
                         percentInferred: false),
            ]
        }
    )

    // MARK: - 小米 MiMo TOKEN Plan（浏览器 Cookie 单端点）

    private static let mimo = ProviderDescriptor(
        kind: .mimo,
        title: "MiMo",
        sectionTitle: "小米 MiMo TOKEN Plan",
        credentialAccount: KeychainAccount.mimoCookie,
        credentialLabel: "Cookie 字符串",
        credentialNoun: "Cookie",
        credentialHint: "推荐点上方「登录小米账号」启用自动续期（登录态只存本机 0600 文件，过期自动静默换新）；也可从浏览器登录控制台后复制整段 Cookie 粘贴，手动模式过期需自行更新。",
        unconfiguredText: "未配置 Cookie",
        sessionLogin: SessionLoginConfig(startURL: MimoSSO.consoleURL),
        makeProvider: { _ in MiMoUsageProvider() },
        demoRows: { now in
            [
                QuotaRow(id: "mimo-plan_total_token", kind: .mimo, label: "套餐",
                         remainingPercent: 96, resetDate: nil, percentInferred: false,
                         usedTokens: 5_580_000_000, limitTokens: 456_000_000_000),
            ]
        }
    )
}
