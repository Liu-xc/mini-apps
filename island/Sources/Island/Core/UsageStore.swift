import Foundation

@MainActor
final class UsageStore: ObservableObject {
    /// 每内容源一份状态（快照 / 最近错误 / 在途）；卡片同屏展示所有已配置源
    @Published private(set) var states: [ProviderKind: SourceState] = [:]
    /// 各源是否已配置凭证
    @Published private(set) var credentialKinds: Set<ProviderKind> = []

    let settings: AppSettings
    private let caches: [ProviderKind: SnapshotCache]
    private let sessionRenewer = MimoSessionRenewer()   // it-004：MiMo 静默续期（单飞）
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
        // it-004 M1 spike 钩子：剥离 serviceToken 模拟过期，验证静默续期（AC1）
        if ProcessInfo.processInfo.environment["GLM_ISLAND_SPIKE_EXPIRE_MIMO"] == "1" {
            await MimoSessionRenewer.spikeExpireServiceToken()
        }
        await refreshAll()
        scheduleNextRefresh()
    }

    /// 刷新全部已配置源，**逐源记录成败**：失败落在对应源状态（他源照常展示，不吞错误），
    /// 成功即清除该源错误。连续全失败按 2^n 拉长间隔，封顶 8 倍基准。
    func refreshAll() async {
        let kinds = ProviderRegistry.all
            .map(\.kind)
            .filter { settings.demoMode || credentialKinds.contains($0) }
        guard !kinds.isEmpty else {
            NSLog("[island][refresh] 跳过：无已配置源（demo=\(settings.demoMode) credentialKinds=\(credentialKinds.map(\.rawValue).sorted())）")
            return
        }

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
                    snap = try await fetchWithRenewal(kind, descriptor)
                    if kind == .glm, settings.endpointMode == .auto {
                        // auto 探测赢家记忆（ADR-003），偏好收编进 settings
                        settings.preferredEndpoint = EndpointMode.mode(forHost: snap.endpointHost)
                    }
                }
                states[kind] = SourceState(snapshot: snap, lastError: nil, isFetching: false)
                try? caches[kind]?.save(snap)
                NSLog("[island][refresh] \(kind) 成功 rows=\(snap.displayRows.count) at=\(snap.fetchedAt)")
                anyOK = true
            } catch {
                var state = self.state(kind)
                state.lastError = error.localizedDescription
                state.isFetching = false
                states[kind] = state   // 保留旧快照：陈旧数据继续展示 + 错误明示
                NSLog("[island][refresh] \(kind) 失败: \(error.localizedDescription)")
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

    // MARK: - 登录态续期（it-004）

    /// 拉取单源。MiMo 登录态过期且该源处于 active 会话时：先静默续期（SSO 换新
    /// serviceToken），成功则落盘并重试一次；账号会话已死标记 expired 停自动重试（AC3）。
    private func fetchWithRenewal(_ kind: ProviderKind, _ descriptor: ProviderDescriptor) async throws -> UsageSnapshot {
        do {
            return try await descriptor.makeProvider(settings).fetchSnapshot()
        } catch let err as ProviderError where err.sessionExpired && !settings.demoMode {
            guard let login = descriptor.sessionLogin else { throw err }
            guard settings.sessionState(kind) == .active else {
                // 手动粘贴模式维持原文案（US-8 回归）；已判死改引导重新登录
                if settings.sessionState(kind) == .expired {
                    throw ProviderError(message: "\(descriptor.title) 登录已失效，请在设置重新登录", sessionExpired: true)
                }
                throw err
            }
            let startURL = err.loginURL ?? login.startURL
            NSLog("[island][refresh] \(kind) 登录态过期，尝试静默续期")
            let header: String
            do {
                header = try await sessionRenewer.renew(startURL: startURL)
            } catch {
                if (error as? SessionRenewError)?.sessionDead == true {
                    settings.setSessionState(kind, .expired)
                }
                NSLog("[island][refresh] \(kind) 静默续期失败: \(error.localizedDescription)")
                throw error
            }
            CredentialStore.save(header, account: descriptor.credentialAccount)
            credentialKinds.insert(kind)
            settings.setSessionState(kind, .active)
            NSLog("[island][refresh] \(kind) 静默续期成功，重试拉取")
            return try await descriptor.makeProvider(settings).fetchSnapshot()
        }
    }

    /// 登录窗/续期取到的整段 Cookie 落盘并标记 active 会话（设置页登录回调入口）
    func adoptSessionCookie(_ header: String, kind: ProviderKind) {
        guard !header.isEmpty else { return }
        settings.setSessionState(kind, .active)
        saveSecret(kind, header)
    }

    // MARK: - 凭证（registry 驱动，不按源硬编码）

    func saveSecret(_ kind: ProviderKind, _ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        CredentialStore.save(trimmed, account: ProviderRegistry.descriptor(kind).credentialAccount)
        credentialKinds.insert(kind)
        // it-004：手动粘贴接管 → 会话状态回手动（active 不降级——粘贴不作废 App 内账号会话）
        if ProviderRegistry.descriptor(kind).sessionLogin != nil, settings.sessionState(kind) != .active {
            settings.setSessionState(kind, .manual)
        }
        if settings.demoMode { settings.demoMode = false }
        Task { await refreshAll() }
    }

    func clearCredential(_ kind: ProviderKind) {
        let descriptor = ProviderRegistry.descriptor(kind)
        CredentialStore.delete(account: descriptor.credentialAccount)
        credentialKinds.remove(kind)
        states[kind] = SourceState()   // 清快照与错误
        try? caches[kind]?.remove()    // 磁盘缓存一并删除（防重启后幽灵数据）
        if descriptor.sessionLogin != nil {
            MimoSessionRenewer.clearWebsiteData()   // AC4：登录会话数据一并清除
            settings.setSessionState(kind, .none)
        }
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
