import Foundation

@MainActor
final class UsageStore: ObservableObject {
    enum Status: Equatable {
        case idle
        case loading
        case loaded
        case failed(String)
    }

    @Published private(set) var snapshot: UsageSnapshot?
    @Published private(set) var status: Status = .idle
    @Published private(set) var hasCredential: Bool

    let settings: AppSettings
    private let cache: SnapshotCache
    private var refreshTimer: Timer?
    private var consecutiveFailures = 0

    init(settings: AppSettings, cache: SnapshotCache = SnapshotCache()) {
        self.settings = settings
        self.cache = cache
        hasCredential = !KeychainStore.loadAPIKey().isEmpty
        snapshot = try? cache.load()
    }

    var isDemoActive: Bool { settings.demoMode }

    func start() async {
        await refreshNow()
        scheduleNextRefresh()
    }

    /// 手动/定时刷新。失败退避：连续失败时按 2^n 拉长间隔，封顶 8 倍基准
    func refreshNow() async {
        status = .loading
        do {
            let snap: UsageSnapshot
            if settings.demoMode {
                snap = try await DemoUsageProvider().fetchSnapshot()
            } else {
                let mode = settings.endpointMode
                let preferred = UserDefaults.standard.string(forKey: "preferredEndpoint")
                    .flatMap(EndpointMode.init(rawValue:))
                snap = try await MonitorUsageProvider(endpointMode: mode, preferredEndpoint: preferred).fetchSnapshot()
                if mode == .auto {
                    let winner = EndpointMode.mode(forHost: snap.endpointHost)
                    UserDefaults.standard.set(winner.rawValue, forKey: "preferredEndpoint")
                }
            }
            snapshot = snap
            status = .loaded
            consecutiveFailures = 0
            try? cache.save(snap)
        } catch {
            consecutiveFailures += 1
            status = .failed(error.localizedDescription)
        }
    }

    func reschedule() {
        scheduleNextRefresh()
    }

    func saveAPIKey(_ key: String) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            try KeychainStore.saveAPIKey(trimmed)
        } catch {
            status = .failed("钥匙串写入失败：\(error.localizedDescription)")
            return
        }
        hasCredential = true
        if settings.demoMode {
            setDemoMode(false)
        }
        Task { await refreshNow() }
    }

    func clearAPIKey() {
        KeychainStore.deleteAPIKey()
        hasCredential = false
        status = .idle
    }

    func setDemoMode(_ enabled: Bool) {
        settings.demoMode = enabled
        Task { await refreshNow() }
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
