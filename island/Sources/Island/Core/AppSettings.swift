import Foundation

enum EndpointMode: String, CaseIterable, Codable, Identifiable {
    case auto
    case bigmodel
    case zai

    var id: String { rawValue }

    var title: String {
        switch self {
        case .auto: "自动探测（国内/国际都试）"
        case .bigmodel: "bigmodel.cn（国内）"
        case .zai: "z.ai（国际）"
        }
    }

    var baseURL: String {
        switch self {
        case .auto, .bigmodel: "https://open.bigmodel.cn"
        case .zai: "https://api.z.ai"
        }
    }

    /// 官方用量接口：路径与鉴权方式已验证（Authorization 直放 Key、不带 Bearer），
    /// limits[] 字段以 spike 抓到的真实响应为准
    var usageLimitURL: URL {
        URL(string: baseURL + "/api/monitor/usage/quota/limit")!
    }

    var defaultConsoleURL: String {
        switch self {
        case .auto, .bigmodel: "https://bigmodel.cn/claude-code"
        case .zai: "https://z.ai/devpack"
        }
    }

    static func mode(forHost host: String) -> EndpointMode {
        host.contains("z.ai") ? .zai : .bigmodel
    }
}

@MainActor
final class AppSettings: ObservableObject {
    @Published var endpointMode: EndpointMode {
        didSet { UserDefaults.standard.set(endpointMode.rawValue, forKey: "endpointMode") }
    }

    @Published var refreshMinutes: Int {
        didSet { UserDefaults.standard.set(refreshMinutes, forKey: "refreshMinutes") }
    }

    @Published var demoMode: Bool {
        didSet { UserDefaults.standard.set(demoMode, forKey: "demoMode") }
    }

    @Published var consoleURLString: String {
        didSet { UserDefaults.standard.set(consoleURLString, forKey: "consoleURLString") }
    }

    /// auto 探测记忆的偏好端点（ADR-003；it-003 收编进 settings，不再散布 UserDefaults 直读写）
    var preferredEndpoint: EndpointMode? {
        get { UserDefaults.standard.string(forKey: "preferredEndpoint").flatMap(EndpointMode.init(rawValue:)) }
        set { UserDefaults.standard.set(newValue?.rawValue, forKey: "preferredEndpoint") }
    }

    /// it-004：按源登录态（UserDefaults `sessionState.<kind>`），驱动设置页状态行与静默续期闸门
    @Published private(set) var sessionStates: [ProviderKind: SessionState] = [:]

    func sessionState(_ kind: ProviderKind) -> SessionState {
        sessionStates[kind] ?? .none
    }

    func setSessionState(_ kind: ProviderKind, _ state: SessionState) {
        sessionStates[kind] = state
        UserDefaults.standard.set(state.rawValue, forKey: "sessionState.\(kind.rawValue)")
    }

    init() {
        let forceDemo = ProcessInfo.processInfo.environment["GLM_ISLAND_DEMO"] == "1"
        endpointMode = EndpointMode(rawValue: UserDefaults.standard.string(forKey: "endpointMode") ?? "") ?? .auto
        refreshMinutes = UserDefaults.standard.object(forKey: "refreshMinutes") as? Int ?? 5
        demoMode = forceDemo || UserDefaults.standard.bool(forKey: "demoMode")
        consoleURLString = UserDefaults.standard.string(forKey: "consoleURLString") ?? ""
        for kind in ProviderKind.allCases {
            let raw = UserDefaults.standard.string(forKey: "sessionState.\(kind.rawValue)") ?? ""
            if let state = SessionState(rawValue: raw) {
                sessionStates[kind] = state
            }
        }
    }
}
