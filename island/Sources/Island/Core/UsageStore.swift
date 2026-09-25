import Foundation

@MainActor
final class UsageStore: ObservableObject {
    enum Status: Equatable {
        case idle
        case loading
        case loaded
        case failed(String)
    }

    /// 每个内容源一份快照；卡片同屏展示所有已配置源
    @Published private(set) var snapshots: [ProviderKind: UsageSnapshot] = [:]
    @Published private(set) var status: Status = .idle
    /// 各源是否已配置凭证
    @Published private(set) var credentialKinds: Set<ProviderKind> = []

    let settings: AppSettings
    private let caches: [ProviderKind: SnapshotCache]
    private var refreshTimer: Timer?
    private var consecutiveFailures = 0

    init(settings: AppSettings) {
        self.settings = settings
        var caches: [ProviderKind: SnapshotCache] = [:]
        for kind in ProviderKind.allCases {
            caches[kind] = SnapshotCache(kind: kind)
        }
        self.caches = caches

        CredentialStore.migrateFromKeychain()
        seedFromEnvironment()
        refreshCredentialKinds()
        for kind in ProviderKind.allCases {
            snapshots[kind] = try? caches[kind]?.load()
        }
    }

    /// 同屏展示的全部明细行（GLM 两档 + MiMo 一档…按源顺序拼接）
    var allRows: [QuotaRow] {
        ProviderKind.allCases.flatMap { snapshots[$0]?.displayRows ?? [] }
    }

    var lastFetchedAt: Date? {
        snapshots.values.map(\.fetchedAt).max()
    }

    /// 最近一次拉取的快照（诊断展示用）
    var lastFetchedSnapshot: UsageSnapshot? {
        snapshots.values.max { $0.fetchedAt < $1.fetchedAt }
    }

    var isDemoActive: Bool { settings.demoMode }

    func isConfigured(_ kind: ProviderKind) -> Bool {
        credentialKinds.contains(kind)
    }

    func start() async {
        await refreshAll()
        scheduleNextRefresh()
    }

    /// 刷新全部已配置源。失败退避：连续全失败按 2^n 拉长间隔，封顶 8 倍基准
    func refreshAll() async {
        let kinds = settings.demoMode ? Set(ProviderKind.allCases) : credentialKinds
        guard !kinds.isEmpty else {
            status = .failed("尚未配置任何内容源凭证")
            return
        }
        status = .loading
        var anyOK = false
        var lastError: String?
        for kind in kinds {
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
                try? caches[kind]?.save(snap)
                anyOK = true
            } catch {
                lastError = error.localizedDescription
            }
        }
        if anyOK {
            status = .loaded
            consecutiveFailures = 0
        } else {
            consecutiveFailures += 1
            status = .failed(lastError ?? "刷新失败")
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
        CredentialStore.delete(account: KeychainAccount.glm)
        credentialKinds.remove(.glm)
        snapshots[.glm] = nil
    }

    func clearMimoCookie() {
        CredentialStore.delete(account: KeychainAccount.mimoCookie)
        credentialKinds.remove(.mimo)
        snapshots[.mimo] = nil
    }

    func setDemoMode(_ enabled: Bool) {
        settings.demoMode = enabled
        Task { await refreshAll() }
    }

    private func saveSecret(_ secret: String, kind: ProviderKind, account: String) {
        let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        CredentialStore.save(trimmed, account: account)
        credentialKinds.insert(kind)
        if settings.demoMode {
            setDemoMode(false)
        }
        Task { await refreshAll() }
    }

    // MARK: - 环境注入（调试/首装）

    private func seedFromEnvironment() {
        let env = ProcessInfo.processInfo.environment
        if let key = env["GLM_ISLAND_SEED_KEY"], !key.isEmpty,
           CredentialStore.load(account: KeychainAccount.glm).isEmpty {
            try? CredentialStore.save(key, account: KeychainAccount.glm)
        }
        if let cookie = env["GLM_ISLAND_SEED_MIMO_COOKIE"], !cookie.isEmpty,
           CredentialStore.load(account: KeychainAccount.mimoCookie).isEmpty {
            try? CredentialStore.save(cookie, account: KeychainAccount.mimoCookie)
        }
    }

    private func refreshCredentialKinds() {
        var kinds: Set<ProviderKind> = []
        if !CredentialStore.load(account: KeychainAccount.glm).isEmpty { kinds.insert(.glm) }
        if !CredentialStore.load(account: KeychainAccount.mimoCookie).isEmpty { kinds.insert(.mimo) }
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
                await self.refreshAll()
                self.scheduleNextRefresh()
            }
        }
    }
}
