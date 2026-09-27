import Foundation

/// 监控引擎：轮询探测 → 自动推送决策 → 邸报到点生成。
/// UI 回调一律切回主队列；git IO 全部在后台队列。
final class WatchEngine {
    struct Snapshot: Codable {
        var probedAt: Date
        var repos: [RepoStatus]
    }

    private let git: GitRunner
    private let queue = DispatchQueue(label: "posthouse.engine", qos: .utility)
    /// 探测走独立并发队列：engine 队列是串行的，若把 probe 派发回自身再 group.wait() 会自死锁
    private let probeQueue = DispatchQueue(label: "posthouse.probe", qos: .utility, attributes: .concurrent)
    /// 懒创建：CLI 一次性模式不 start()，若 inline 创建又永不 resume，释放 suspended source 会触发 libdispatch 断言（SIGTRAP）
    private var timer: DispatchSourceTimer?

    private(set) var config: PosthouseConfig
    private var probe: StatusProbe
    private lazy var pushService = PushService(git: git, extraArgs: config.pushArgs)
    private let notifier: Notifier

    /// path -> 退避状态
    private var backoffs: [String: BackoffState] = [:]
    /// 当日推送事件（邸报「千军一发」用），跨日清空；全量落盘
    private(set) var todayPushEvents: [PushEvent] = []
    private var pushEventsDay = ""
    private var lastGazetteDate = ""
    /// 疑似代理故障已提醒过（一次故障期只提醒一次）
    private var proxyAlertActive = false
    /// 分叉（本地落后远端）已提醒过的仓库；分叉解除后移除
    private var forkAlerted = Set<String>()

    /// UI 绑定：探测结果刷新（主队列回调）
    var onStatuses: (([RepoStatus], BeaconState) -> Void)?
    /// 推送结果回调（主队列）
    var onPushResult: ((String, String) -> Void)?

    init(git: GitRunner = GitShell(), config: PosthouseConfig, notifier: Notifier = Notifier()) {
        self.git = git
        self.config = config
        self.notifier = notifier
        self.probe = StatusProbe(git: git, remoteProbeTimeout: config.remoteProbeTimeout)
        loadPersistentState()
    }

    // MARK: - 持久化

    private func loadPersistentState() {
        if let data = try? Data(contentsOf: DirSupport.pushEventsURL),
           let decoded = try? JSONDecoder().decode([String: [PushEvent]].self, from: data) {
            let today = Self.dayString(Date())
            pushEventsDay = today
            todayPushEvents = decoded[today] ?? []
        }
        if let data = try? Data(contentsOf: DirSupport.gazetteDatesURL),
           let dates = try? JSONDecoder().decode([String].self, from: data), let last = dates.last {
            lastGazetteDate = last
        }
    }

    private func persistPushEvents() {
        var all: [String: [PushEvent]] = [:]
        if let data = try? Data(contentsOf: DirSupport.pushEventsURL),
           let decoded = try? JSONDecoder().decode([String: [PushEvent]].self, from: data) {
            all = decoded
        }
        all[pushEventsDay] = todayPushEvents
        if let data = try? JSONEncoder().encode(all) {
            try? data.write(to: DirSupport.pushEventsURL)
        }
    }

    // MARK: - 仓库集合

    func discoverRepos() -> [String] {
        var set = Set<String>()
        for root in config.scanRoots {
            let found = RepoScanner.scan(root: root, git: git)
            PLog.info("扫描 \(root) → \(found.count) 仓")
            for repo in found { set.insert(repo) }
        }
        for extra in config.extraRepos where RepoScanner.isGitRepo(extra, git: git) {
            set.insert(extra)
        }
        return set.sorted()
    }

    /// 目录枚举可能被 macOS TCC（「文稿」访问授权）等挂起——超时放弃本轮，不让引擎挂死。
    /// 超时后 result 弃用不读，无并发访问。
    private func discoverReposWithTimeout(_ timeout: TimeInterval) -> [String] {
        var result: [String] = []
        let done = DispatchSemaphore(value: 0)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { done.signal(); return }
            result = self.discoverRepos()
            done.signal()
        }
        if done.wait(timeout: .now() + timeout) == .timedOut {
            PLog.warn("仓库扫描超时（\(Int(timeout))s）——若首次启动可能在等「文稿」文件夹访问授权，本轮跳过")
            return []
        }
        return result
    }

    // MARK: - 轮询

    func start() {
        guard timer == nil else { return }
        let interval = max(config.pollIntervalSeconds, 10)
        let t = DispatchSource.makeTimerSource(queue: DispatchQueue(label: "posthouse.timer"))
        t.schedule(deadline: .now(), repeating: interval)
        t.setEventHandler { [weak self] in self?.tick() }
        t.resume()
        timer = t
        PLog.info("引擎启动：轮询 \(Int(interval))s")
    }

    func stop() {
        timer?.cancel()
        timer = nil
    }

    deinit {
        timer?.cancel()
    }

    func updateConfig(_ newConfig: PosthouseConfig) {
        config = newConfig
        // 重建探测超时与推送参数
        probe.remoteProbeTimeout = newConfig.remoteProbeTimeout
    }

    func tick() {
        queue.async { [weak self] in
            guard let self else { return }
            self.pollOnce()
            self.checkGazetteDue()
        }
    }

    func probeNow() { tick() }

    /// CLI 同步探测：不启定时器、不触发自动推送，只探测并落 status.json
    func probeNowSyncForCLI() {
        let repos = discoverReposWithTimeout(15)
        guard !repos.isEmpty else { PLog.warn("未发现任何 git 仓库（或扫描超时）"); return }
        persistSnapshot(probeAll(repos))
    }

    /// 并发探测全部仓库（engine 队列外安全：走 probeQueue + group.wait）
    private func probeAll(_ repos: [String]) -> [RepoStatus] {
        var statuses: [RepoStatus] = []
        statuses.reserveCapacity(repos.count)
        let group = DispatchGroup()
        let lock = NSLock()
        for path in repos {
            group.enter()
            probeQueue.async { [weak self] in
                guard let self else { group.leave(); return }
                let s = self.probe.probe(path: path)
                lock.lock(); statuses.append(s); lock.unlock()
                group.leave()
            }
        }
        group.wait()
        statuses.sort { $0.name < $1.name }
        return statuses
    }

    private func pollOnce() {
        let repos = discoverReposWithTimeout(15)
        guard !repos.isEmpty else {
            PLog.warn("未发现任何 git 仓库（或扫描超时）")
            return
        }
        PLog.info("轮询开始：\(repos.count) 仓 [\(repos.map { ($0 as NSString).lastPathComponent }.joined(separator: ","))]")

        let statuses = probeAll(repos)

        let overall = statuses.overallBeacon
        persistSnapshot(statuses)
        DispatchQueue.main.async { [weak self] in
            self?.onStatuses?(statuses, overall)
        }

        evaluateAutoPushes(statuses: statuses, repos: repos)
    }

    private func persistSnapshot(_ statuses: [RepoStatus]) {
        let snap = Snapshot(probedAt: Date(), repos: statuses)
        if let data = try? JSONEncoder().encode(snap) {
            try? data.write(to: DirSupport.statusFileURL)
        }
        PLog.info("探测完成：\(statuses.count) 仓，整体态 \(statuses.overallBeacon.rawValue)，"
            + statuses.map { "\($0.name)=\($0.beacon.rawValue)↑\($0.ahead)" }.joined(separator: " "))
    }

    // MARK: - 自动推送（M2）

    private func evaluateAutoPushes(statuses: [RepoStatus], repos: [String]) {
        let now = Date()
        var proxySuspect = false

        for status in statuses {
            guard config.isWhitelisted(status.path) else { continue }
            var backoff = backoffs[status.path] ?? BackoffState()

            let verdict = PushDecision.evaluate(.init(
                autoPushWhitelisted: true,
                ahead: status.ahead,
                behind: status.behind,
                remoteConfigured: status.remoteConfigured,
                remoteReachable: status.remoteReachable,
                cooldownUntil: backoff.cooldownUntil ?? .distantPast,
                now: now
            ))

            switch verdict {
            case .skip(let reason):
                if reason.contains("本地落后") {
                    // 分叉：只报警不动手，每仓分叉期提醒一次
                    if forkAlerted.insert(status.path).inserted {
                        PLog.warn("分叉 \(status.name)：\(reason)")
                        notifier.notify(title: "🔀 \(status.name) 分叉", body: "远端有你没有的提交——我不会自动 pull/rebase，请手动处理")
                    }
                } else {
                    forkAlerted.remove(status.path)
                }
                if backoff.smellsLikeProxyDeath(), status.remoteReachable == false {
                    proxySuspect = true
                }
                _ = reason // 冷却/无积压属常态，不打日志
            case .push:
                let before = status.ahead
                PLog.info("自动推送 \(status.name)（\(before) 个提交）…")
                let outcome = pushService.push(repoPath: status.path, branch: status.branch, expectedAhead: before)
                handlePushOutcome(outcome, repo: status, automatic: true, expectedCommits: before, backoff: &backoff)
            }
            backoffs[status.path] = backoff
        }

        if proxySuspect && !proxyAlertActive {
            proxyAlertActive = true
            notifier.notify(title: "疑似代理故障", body: "多个仓库连续网络失败。菜单「烽火台 → 网络诊断…」一键跑 network-rescue，恢复后我会自动补推。")
        } else if !proxySuspect {
            proxyAlertActive = false
        }
    }

    private static func skipReason(of verdict: PushDecision.Verdict) -> String {
        if case .skip(let reason) = verdict { return reason }
        return ""
    }

    private func handlePushOutcome(_ outcome: PushService.Outcome, repo: RepoStatus, automatic: Bool, expectedCommits: Int, backoff: inout BackoffState) {
        let now = Date()
        if outcome.success {
            backoff.recordSuccess(now: now)
            recordPush(PushEvent(repoName: repo.name, at: now, commitsPushed: expectedCommits, branch: repo.branch, automatic: automatic, success: true))
            notifier.notify(title: "✅ \(repo.name) 已推送", body: "\(expectedCommits) 个提交上去了")
        } else {
            let kind = outcome.failureKind ?? .other
            backoff.recordFailure(kind: kind, now: now)
            PLog.warn("推送失败 \(repo.name) [\(kind.rawValue)]: \(outcome.message)")
            if kind == .nonFastForward {
                // non-FF：只报警，绝不自动 pull/rebase/merge
                notifier.notify(title: "⚠️ \(repo.name) 推送被拒（non-fast-forward）", body: "远端有新提交，请手动处理；我不会自动动作。")
            } else if kind == .auth {
                notifier.notify(title: "🔑 \(repo.name) 推送凭证失败", body: "请检查 git 凭证（gh auth / ssh key）。")
            }
            // network/other：静默退避，连续失败触发「疑似代理故障」提醒
        }
        DispatchQueue.main.async { [weak self] in
            self?.onPushResult?(repo.name, outcome.success ? "成功" : "失败: \(outcome.message)")
        }
    }

    private func recordPush(_ event: PushEvent) {
        let today = Self.dayString(event.at)
        if today != pushEventsDay {
            pushEventsDay = today
            todayPushEvents = []
        }
        todayPushEvents.append(event)
        persistPushEvents()
    }

    // MARK: - 手动推送（M1）

    func pushNow(path: String) {
        queue.async { [weak self] in
            guard let self else { return }
            guard let status = self.latestStatuses.first(where: { $0.path == path }) else { return }
            let verdict = PushDecision.evaluateManual(
                ahead: status.ahead, behind: status.behind, remoteConfigured: status.remoteConfigured
            )
            guard case .push = verdict else {
                self.notifier.notify(title: "ℹ️ \(status.name) 暂不推送", body: Self.skipReason(of: verdict))
                return
            }
            var backoff = self.backoffs[path] ?? BackoffState()
            let outcome = self.pushService.push(repoPath: path, branch: status.branch, expectedAhead: status.ahead)
            self.handlePushOutcome(outcome, repo: status, automatic: false, expectedCommits: status.ahead, backoff: &backoff)
            self.backoffs[path] = backoff
            // 推完立刻复测刷新 UI
            self.pollOnce()
        }
    }

    /// 最近一次探测结果（供手动推送读取；引擎线程写，读走快照）
    private var latestStatuses: [RepoStatus] {
        if let data = try? Data(contentsOf: DirSupport.statusFileURL),
           let snap = try? JSONDecoder().decode(Snapshot.self, from: data) {
            return snap.repos
        }
        return []
    }

    // MARK: - 邸报到点检查（M3）

    private func checkGazetteDue() {
        let now = Date()
        let today = Self.dayString(now)
        guard lastGazetteDate != today else { return }
        var cal = Calendar.current
        cal.timeZone = .current
        let due = cal.date(bySettingHour: config.gazetteHour, minute: config.gazetteMinute, second: 0, of: now)!
        guard now >= due else { return }
        generateGazette()
    }

    func generateGazette() {
        let repos = discoverRepos()
        let statuses = latestStatuses
        let now = Date()
        let today = Self.dayString(now)

        var cal = Calendar.current
        cal.timeZone = .current
        guard let dayStart = cal.date(bySettingHour: 0, minute: 0, second: 0, of: now),
              let dayEnd = cal.date(byAdding: DateComponents(day: 1, second: -1), to: dayStart) else { return }

        let commits = probe.commitsToday(repos: repos, dayStart: dayStart, dayEnd: dayEnd)
        let daySet = probe.commitDayStrings(repos: repos, sinceDays: 90)
        let streak = GazetteEngine.activeStreak(dayStrings: daySet, today: now)
        let gazette = GazetteEngine.build(
            dayString: today, now: now, commits: commits,
            pushEvents: todayPushEvents, statuses: statuses,
            unlockedArchive: GazetteStore.loadArchive(),
            streak: streak
        )

        let result: GazetteStore.WriteResult?
        let markdown = GazetteStore.render(gazette)
        if let polished = GazettePolisher.polish(markdown: markdown, cfg: config) {
            result = GazetteStore.writeRaw(markdown: polished, date: gazette.date, outputDir: config.gazetteOutputDir)
        } else {
            result = GazetteStore.writeRaw(markdown: markdown, date: gazette.date, outputDir: config.gazetteOutputDir)
        }
        lastGazetteDate = today
        GazetteStore.appendGazetteDate(today)
        GazetteStore.saveUnlocks(gazette.unlocked, on: today)

        PLog.info("邸报已生成: \(result?.path ?? "?")，成就 \(gazette.unlocked.map(\.title).joined(separator: "/"))")
        notifier.notify(
            title: "📜 今日邸报已送达",
            body: result == nil ? "写入失败，见日志" : "提交 \(gazette.totalCommits) · 成就 \(gazette.unlocked.count) 枚 → \(result!.path)"
        )
    }

    static func dayString(_ date: Date, calendar: Calendar = .current) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = .current
        return f.string(from: date)
    }
}
