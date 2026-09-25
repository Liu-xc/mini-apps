import Foundation

@MainActor
final class UsageStore: ObservableObject {
    enum Status: Equatable {
        case idle
        case loading
        case loaded
        case failed(String)
    }

    /// 每个内容源一份快照；展示与刷新都跟随 activeKind
    @Published private(set) var snapshots: [ProviderKind: UsageSnapshot] = [:]
    @Published var activeKind: ProviderKind {
        didSet {
            UserDefaults.standard.set(activeKind.rawValue, forKey: "activeProvider")
            hasCredential = credentialKinds.contains(activeKind)
        }
    }
    @Published private(set) var status: Status = .idle
    @Published private(set) var hasCredential: Bool = false
    /// 各源是否已配置凭证
    @Published private(set) var credentialKinds: Set<ProviderKind> = []

    let settings: AppSettings
    private let caches: [ProviderKind: SnapshotCache]
    private var refreshTimer: Timer?
    private var consecutiveFailures = 0

    init(settings: AppSettings) {
        self.settings = settings
        self.activeKind = ProviderKind(rawValue: UserDefaults.standard.string(forKey: "activeProvider") ?? "") ?? .glm
        var caches: [ProviderKind: SnapshotCache] = [:]
        for kind in ProviderKind.allCases {
            caches[kind] = SnapshotCache(kind: kind)
        }
        self.caches = caches

        KeychainStore.migrateLegacyAPIKey()
        seedFromEnvironment()
        refreshCredentialKinds()
        for kind in ProviderKind.allCases {
            snapshots[kind] = try? caches[kind]?.load()
        }
        hasCredential = credentialKinds.contains(activeKind)
    }

    var displaySnapshot: UsageSnapshot? {
        snapshots[activeKind]
    }

    var isDemoActive: Bool { settings.demoMode }

    func isConfigured(_ kind: ProviderKind) -> Bool {
        credentialKinds.contains(kind)
    }

    func start() async {
        await refreshNow()
        scheduleNextRefresh()
    }

    /// 切换内容源：立即呈现该源缓存并后台刷新
    func switchProvider(_ kind: ProviderKind) {
        guard kind != activeKind else { return }
        activeKind = kind
        status = snapshots[kind] != nil ? .loaded : .idle
        Task { await refreshNow() }
    }

    /// 手动/定时刷新（当前激活源）。失败退避：连续失败按 2^n 拉长间隔，封顶 8 倍基准
    func refreshNow() async {
        let kind = activeKind
        status = .loading
        do {
            let snap: UsageSnapshot
            if settings.demoMode {
                snap = try await DemoUsageProvider(kind: kind).fetchSnapshot()
            } else {
                let mode = settings.endpointMode
                let preferred = UserDefaults.standard.string(forKey: "preferredEndpoint")
                    .flatMap(EndpointMode.init(rawValue:))
                snap = try await ProviderUsageFetcher(
                    kind: kind,
                    glmEndpointMode: mode,
                    glmPreferredEndpoint: preferred
                ).fetchSnapshot()
                if kind == .glm, mode == .auto {
                    let winner = EndpointMode.mode(forHost: snap.endpointHost)
                    UserDefaults.standard.set(winner.rawValue, forKey: "preferredEndpoint")
                }
            }
            snapshots[kind] = snap
            status = .loaded
            consecutiveFailures = 0
            try? caches[kind]?.save(snap)
        } catch {
            consecutiveFailures += 1
            status = .failed(error.localizedDescription)
        }
    }

    func reschedule() {
        scheduleNextRefresh()
    }

    func saveGLMKey(_ key: String) {
        saveSecret(key, kind: .glm, account: KeychainAccount.glm)
    }

    func saveMimoCookie(_ cookie: String) {
        saveSecret(cookie, kind: .mimo, account: KeychainAccount.mimoCookie)
    }

    func clearGLMKey() {
        KeychainStore.delete(account: KeychainAccount.glm)
        clearCredential(kind: .glm)
    }

    func clearMimoCookie() {
        KeychainStore.delete(account: KeychainAccount.mimoCookie)
        clearCredential(kind: .mimo)
    }

    func setDemoMode(_ enabled: Bool) {
        settings.demoMode = enabled
        Task { await refreshNow() }
    }

    private func saveSecret(_ secret: String, kind: ProviderKind, account: String) {
        let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            try KeychainStore.save(trimmed, account: account)
        } catch {
            status = .failed("钥匙串写入失败：\(error.localizedDescription)")
            return
        }
        credentialKinds.insert(kind)
        hasCredential = credentialKinds.contains(activeKind)
        if settings.demoMode {
            setDemoMode(false)
        }
        Task { await refreshNow() }
    }

    private func clearCredential(kind: ProviderKind) {
        credentialKinds.remove(kind)
        hasCredential = credentialKinds.contains(activeKind)
        status = .idle
    }

    // MARK: - 环境注入（调试/首装）

    private func seedFromEnvironment() {
        let env = ProcessInfo.processInfo.environment
        if let key = env["GLM_ISLAND_SEED_KEY"], !key.isEmpty,
           KeychainStore.load(account: KeychainAccount.glm).isEmpty {
            try? KeychainStore.save(key, account: KeychainAccount.glm)
        }
        if let cookie = env["GLM_ISLAND_SEED_MIMO_COOKIE"], !cookie.isEmpty,
           KeychainStore.load(account: KeychainAccount.mimoCookie).isEmpty {
            try? KeychainStore.save(cookie, account: KeychainAccount.mimoCookie)
        }
    }

    private func refreshCredentialKinds() {
        var kinds: Set<ProviderKind> = []
        if !KeychainStore.load(account: KeychainAccount.glm).isEmpty { kinds.insert(.glm) }
        if !KeychainStore.load(account: KeychainAccount.mimoCookie).isEmpty { kinds.insert(.mimo) }
        credentialKinds = kinds
    }

    // MARK: - 定时器

    private func scheduleNextRefresh() {
        refreshTimer?.invalidate()
        let base = Double(max(1, settings.refreshMinutes)) * 60
        let backoff = consecutiveFailures == 0
            ? 1.0
            : min(8.0, pow(2, Double(min(consecutiveFailures, 4))))
        refreshTimer = Timer.scheduledTimer(withTimeInterval: base * backoff, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                await self.refreshNow()
                self.scheduleNextRefresh()
            }
        }
    }
}
