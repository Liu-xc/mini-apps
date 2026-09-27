import Testing
import Foundation
@testable import Posthouse

/// 邸报成就规则引擎回放（纯函数，构造提交数据回放历史场景）
struct AchievementsReplayTests {
    private func date(_ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int = 0) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        return cal.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
    }

    private func commit(_ subject: String, _ at: Date, repo: String = "mini-apps") -> CommitEntry {
        CommitEntry(hash: UUID().uuidString, subject: subject, committedAt: at, author: "leo", repoName: repo)
    }

    private let cleanStatuses: [RepoStatus] = [
        RepoStatus(path: "/x/mini-apps", name: "mini-apps", branch: "main", ahead: 0, behind: 0,
                   dirtyCount: 0, lastCommitAt: nil, remoteConfigured: true, remoteReachable: true,
                   lastError: nil, probedAt: Date())
    ]

    @Test func firstGazette_unlocks() {
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: [],
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: [:]
        )
        #expect(g.unlocked.contains { $0.id == "first-gazette" })
    }

    @Test func tripleFix_unlocks() {
        let day = date(2026, 9, 27, 10)
        let commits = [
            commit("fix(wardrobe): 坑一", day),
            commit("fix(wardrobe): 坑二", day),
            commit("fix(eats): 坑三", day),
        ]
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: day, commits: commits,
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "2026-09-26"]
        )
        #expect(g.unlocked.contains { $0.id == "triple-fix" })
    }

    @Test func nightOwl_unlocks() {
        let commits = [commit("feat: 深夜爆肝", date(2026, 9, 26, 23, 41))]
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: commits,
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"]
        )
        #expect(g.unlocked.contains { $0.id == "night-owl" })
    }

    @Test func army_unlocks_atTen() {
        let day = date(2026, 9, 27, 10)
        let commits = (0..<10).map { commit("feat: 量产#\($0)", day) }
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: day, commits: commits,
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"]
        )
        #expect(g.unlocked.contains { $0.id == "army" })
    }

    @Test func cleanSweep_unlocks_whenNoBacklog() {
        let commits = [commit("feat: 有活干", date(2026, 9, 27, 10))]
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: commits,
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"]
        )
        #expect(g.unlocked.contains { $0.id == "clean-sweep" })
    }

    @Test func cleanSweep_requiresCommits() {
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: [],
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"]
        )
        #expect(!g.unlocked.contains { $0.id == "clean-sweep" })
    }

    @Test func massPush_unlocks_atTwenty() {
        let big = PushEvent(repoName: "mini-apps", at: date(2026, 9, 27, 12), commitsPushed: 20, branch: "main", automatic: true, success: true)
        let small = PushEvent(repoName: "mini-apps", at: date(2026, 9, 27, 12), commitsPushed: 3, branch: "main", automatic: false, success: true)
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: [],
            pushEvents: [small, big], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"]
        )
        #expect(g.unlocked.contains { $0.id == "mass-push" })
    }

    @Test func alreadyUnlocked_notReissued() {
        let day = date(2026, 9, 27, 10)
        let commits = [
            commit("fix: 坑一", day), commit("fix: 坑二", day), commit("fix: 坑三", day),
        ]
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: day, commits: commits,
            pushEvents: [], statuses: cleanStatuses,
            unlockedArchive: ["first-gazette": "x", "triple-fix": "2026-09-20"]
        )
        #expect(!g.unlocked.contains { $0.id == "triple-fix" })
    }

    /// 提案验收：历史数据回放 ≥3 枚成就可触发
    @Test func replayDay_triggersAtLeastThree() {
        let day = date(2026, 9, 27, 10)
        var commits: [CommitEntry] = []
        for i in 0..<4 { commits.append(commit("fix(wardrobe): 修坑#\(i)", day.addingTimeInterval(Double(i) * 600))) }
        for i in 0..<7 { commits.append(commit("feat(eats): 特性#\(i)", day.addingTimeInterval(3600 + Double(i) * 300))) }
        commits.append(commit("chore: 深夜扫尾", date(2026, 9, 26, 23, 58)))
        let push = PushEvent(repoName: "mini-apps", at: day, commitsPushed: 25, branch: "main", automatic: true, success: true)

        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: commits,
            pushEvents: [push], statuses: cleanStatuses, unlockedArchive: [:]
        )
        let ids = Set(g.unlocked.map(\.id))
        #expect(ids.count >= 3, "回放日应至少触发 3 枚成就，实际: \(ids)")
        #expect(ids.contains("night-owl"))
        #expect(ids.contains("mass-push"))
        #expect(ids.contains("army"))
    }

    @Test func activeStreak_consecutiveDays() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let today = date(2026, 9, 27, 12)
        // 09-25..09-27 连续，09-24 断档
        let days: Set<String> = ["2026-09-25", "2026-09-26", "2026-09-27", "2026-09-22"]
        #expect(GazetteEngine.activeStreak(dayStrings: days, today: today, calendar: cal) == 3)
        // 今天没提交 → 从昨天起数
        #expect(GazetteEngine.activeStreak(dayStrings: ["2026-09-26"], today: today, calendar: cal) == 1)
        // 全断档
        #expect(GazetteEngine.activeStreak(dayStrings: [], today: today, calendar: cal) == 0)
        // 更早的连续段不计入
        #expect(GazetteEngine.activeStreak(dayStrings: ["2026-09-22", "2026-09-23"], today: today, calendar: cal) == 0)
    }

    @Test func build_passesStreakThrough() {
        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30), commits: [],
            pushEvents: [], statuses: cleanStatuses, unlockedArchive: ["first-gazette": "x"],
            streak: 9
        )
        #expect(g.activeStreak == 9)
    }

    @Test func gazetteMarkdown_rendersAndWrites() throws {
        let dir = NSTemporaryDirectory() + "posthouse-gazette-\(UUID().uuidString)"
        defer { try? FileManager.default.removeItem(atPath: dir) }

        let g = GazetteEngine.build(
            dayString: "2026-09-27", now: date(2026, 9, 27, 22, 30),
            commits: [commit("fix(wardrobe): 修坑", date(2026, 9, 27, 11))],
            pushEvents: [PushEvent(repoName: "mini-apps", at: date(2026, 9, 27, 21), commitsPushed: 5, branch: "main", automatic: true, success: true)],
            statuses: cleanStatuses, unlockedArchive: [:]
        )
        let md = GazetteStore.render(g)
        #expect(md.contains("# 📜 驿站邸报 · 2026-09-27"))
        #expect(md.contains("fix"))
        #expect(md.contains("自动补推"))
        #expect(md.contains("首日点亮"))

        let result = GazetteStore.write(gazette: g, outputDir: dir)
        #expect(result != nil)
        let written = try String(contentsOfFile: result!.path, encoding: .utf8)
        #expect(written.contains("驿站邸报"))
    }
}

/// 配置序列化与白名单语义
struct ConfigStoreTests {
    @Test func roundTrip() throws {
        var cfg = PosthouseConfig.defaultConfig()
        cfg.autoPushEnabled = true
        cfg.autoPushWhitelist["/Users/leo/Documents/mini-apps"] = true
        cfg.gazetteHour = 21

        let data = try JSONEncoder().encode(cfg)
        let decoded = try JSONDecoder().decode(PosthouseConfig.self, from: data)
        #expect(decoded.autoPushWhitelist["/Users/leo/Documents/mini-apps"] == true)
        #expect(decoded.gazetteHour == 21)
        #expect(decoded.pushArgs == ["-c", "http.version=HTTP/1.1", "-c", "http.postBuffer=524288000"])
    }

    @Test func whitelistSemantics_defaultClosed() {
        var cfg = PosthouseConfig.defaultConfig()
        // 默认全关
        #expect(!cfg.isWhitelisted("/any/repo"))
        // 白名单开了但总开关没开 → 仍不推
        cfg.autoPushWhitelist["/any/repo"] = true
        #expect(!cfg.isWhitelisted("/any/repo"))
        // 总开 + 白名单 → 推
        cfg.autoPushEnabled = true
        #expect(cfg.isWhitelisted("/any/repo"))
        // 未列入白名单的仓库不推
        #expect(!cfg.isWhitelisted("/other/repo"))
    }
}
