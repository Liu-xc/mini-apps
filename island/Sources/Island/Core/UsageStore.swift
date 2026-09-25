import Foundation

@MainActor
final class UsageStore: ObservableObject {
    /// 每内容源一份状态（快照 / 最近错误 / 在途）；卡片同屏展示所有已配置源
    @Published private(set) var states: [ProviderKind: SourceState] = [:]
    /// 各源是否已配置凭证
    @Published private(set) var credentialKinds: Set<ProviderKind> = []

    let settings: AppSettings
    private let caches: [ProviderKind: SnapshotCache]
    private var refreshTimer: Timer?
    private var consecutiveFailures = 0

    init(settings: AppSettings) {
        self.settings = settings
        var caches: [ProviderKind: SnapshotCache] = [:]
        for desc in ProviderRegistry.all {
            caches[desc.kind] = SnapshotCache(kind: desc.kind)
        }
        self.caches = caches

        CredentialStore.migrateFromKeychain()
        // 走查钩子：GLM_ISLAND_BLANK=1 视作全未配置（空态截图用，与 GLM_ISLAND_SHOT 搭配）
        if ProcessInfo.processInfo.environment["GLM_ISLAND_BLANK"] != "1" {
            seedFromEnvironment()
            refreshCredentialKinds()
            // 只为已配置源恢复快照：清凭证即清展示（缓存文件由 clearCredential 删除）
            for desc in ProviderRegistry.all where credentialKinds.contains(desc.kind) {
                states[desc.kind] = SourceState(snapshot: try? caches[desc.kind]?.load())
            }
        }
    }

    // MARK: - 读取

    func state(_ kind: ProviderKind) -> SourceState {
        states[kind] ?? SourceState()
    }

    var snapshots: [ProviderKind: UsageSnapshot] {
        states.compactMapValues(\.snapshot)
    }

    /// registry 顺序的全部明细行（菜单摘要 / 首启判定）
    var allRows: [QuotaRow] {
        ProviderRegistry.all.flatMap { states[$0.kind]?.snapshot?.displayRows ?? [] }
    }

    var lastFetchedAt: Date? {
        snapshots.values.map(\.fetchedAt).max()
    }

    /// 最近一次拉取的快照（诊断展示用）
    var lastFetchedSnapshot: UsageSnapshot? {
        snapshots.values.max { $0.fetchedAt < $1.fetchedAt }
    }

    var isDemoActive: Bool { settings.demoMode }

    /// 任一源在途（页脚刷新指示）
    var isFetchingAny: Bool { states.values.contains(where: \.isFetching) }

    func isConfigured(_ kind: ProviderKind) -> Bool {
        credentialKinds.contains(kind)
    }

    // MARK: - 刷新

    func start() async {
        await refreshAll()
        scheduleNextRefresh()
    }

    /// 刷新全部已配置源，**逐源记录成败**：失败落在对应源状态（他源照常展示，不吞错误），
    /// 成功即清除该源错误。连续全失败按 2^n 拉长间隔，封顶 8 倍基准。
    func refreshAll() async {
        let kinds = ProviderRegistry.all
            .map(\.kind)
            .filter { settings.demoMode || credentialKinds.contains($0) }
        guard !kinds.isEmpty else { return }

        for kind in kinds {
            var state = self.state(kind)
            state.isFetching = true
            states[kind] = state
        }

        var anyOK = false
        for kind in kinds {
            let descriptor = ProviderRegistry.descriptor(kind)
            do {
                let snap: UsageSnapshot
                if settings.demoMode {
                    snap = try await StaticDemoProvider(kind: kind, rows: descriptor.demoRows(Date()))
                        .fetchSnapshot()
                } else {
                    snap = try await descriptor.makeProvider(settings).fetchSnapshot()
                    if kind == .glm, settings.endpointMode == .auto {
                        // auto 探测赢家记忆（ADR-003），偏好收编进 settings
                        settings.preferredEndpoint = EndpointMode.mode(forHost: snap.endpointHost)
                    }
                }
                states[kind] = SourceState(snapshot: snap, lastError: nil, isFetching: false)
                try? caches[kind]?.save(snap)
                anyOK = true
            } catch {
                var state = self.state(kind)
                state.lastError = error.localizedDescription
                state.isFetching = false
                states[kind] = state   // 保留旧快照：陈旧数据继续展示 + 错误明示
            }
        }
        if anyOK {
            consecutiveFailures = 0
        } else {
            consecutiveFailures += 1
        }
    }

    func reschedule() {
        scheduleNextRefresh()
    }

    // MARK: - 凭证（registry 驱动，不按源硬编码）

    func saveSecret(_ kind: ProviderKind, _ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        CredentialStore.save(trimmed, account: ProviderRegistry.descriptor(kind).credentialAccount)
        credentialKinds.insert(kind)
        if settings.demoMode { settings.demoMode = false }
        Task { await refreshAll() }
    }

    func clearCredential(_ kind: ProviderKind) {
        CredentialStore.delete(account: ProviderRegistry.descriptor(kind).credentialAccount)
        credentialKinds.remove(kind)
        states[kind] = SourceState()   // 清快照与错误
        try? caches[kind]?.remove()    // 磁盘缓存一并删除（防重启后幽灵数据）
    }

    func setDemoMode(_ enabled: Bool) {
        settings.demoMode = enabled
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
        credentialKinds = Set(ProviderRegistry.all.compactMap { descriptor in
            CredentialStore.load(account: descriptor.credentialAccount).isEmpty ? nil : descriptor.kind
        })
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
