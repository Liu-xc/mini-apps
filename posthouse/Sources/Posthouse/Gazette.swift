import Foundation

/// 邸报构建与成就评估——全部纯函数，可离线回放单测。
enum GazetteEngine {

    /// 连续活跃天数：从 today（或 today 无提交时从昨天）往回数，dayStrings（"yyyy-MM-dd"）里连续存在提交的天数
    static func activeStreak(dayStrings: Set<String>, today: Date, calendar: Calendar = .current) -> Int {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = .current
        var cursor = today
        // 当天还没有提交不打断连胜（邸报生成时当天可能零提交）
        if !dayStrings.contains(f.string(from: cursor)),
           let yesterday = calendar.date(byAdding: .day, value: -1, to: cursor),
           dayStrings.contains(f.string(from: yesterday)) {
            cursor = yesterday
        }
        var streak = 0
        while dayStrings.contains(f.string(from: cursor)) {
            streak += 1
            guard let prev = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = prev
        }
        return streak
    }

    static func build(
        dayString: String, now: Date, commits: [CommitEntry],
        pushEvents: [PushEvent], statuses: [RepoStatus],
        unlockedArchive: [String: String],
        streak: Int? = nil
    ) -> DailyGazette {
        // 分仓汇总
        var byRepo: [String: [CommitEntry]] = [:]
        for c in commits { byRepo[c.repoName, default: []].append(c) }

        let backlogRepos = Set(statuses.filter { $0.ahead > 0 }.map(\.name))
        let summaries = byRepo.keys.sorted().map { name -> RepoDaySummary in
            let list = byRepo[name] ?? []
            return RepoDaySummary(
                repoName: name,
                commits: list.count,
                fixes: list.filter { $0.kind == "fix" }.count,
                feats: list.filter { $0.kind == "feat" }.count,
                docsSpec: list.filter { $0.kind == "docs" || $0.kind == "spec" }.count,
                backlogCleared: !backlogRepos.contains(name)
            )
        }

        let cal = Calendar.current
        let activeStreak = streak ?? 0

        var gazette = DailyGazette(
            date: dayString, generatedAt: now, repoSummaries: summaries,
            totalCommits: commits.count, pushEvents: pushEvents.filter(\.success),
            backlogAtClose: backlogRepos.count,
            unlocked: [], activeStreak: activeStreak
        )

        gazette.unlocked = evaluateAchievements(
            gazette: gazette, commits: commits, statuses: statuses,
            archive: unlockedArchive, isFirstEver: unlockedArchive.isEmpty
        )
        return gazette
    }

    /// 成就规则引擎（纯函数）。首发 6 枚。
    static func evaluateAchievements(
        gazette: DailyGazette, commits: [CommitEntry], statuses: [RepoStatus],
        archive: [String: String], isFirstEver: Bool
    ) -> [Achievement] {
        var out: [Achievement] = []
        let cal = Calendar.current

        func unlock(_ id: String, _ title: String, _ emoji: String, _ detail: String) {
            out.append(Achievement(id: id, title: title, emoji: emoji, detail: detail))
        }

        // 1 首日点亮：档案为空（第一次生成邸报）
        if isFirstEver && archive["first-gazette"] == nil {
            unlock("first-gazette", "首日点亮", "🏮", "第一封邸报送达")
        }
        // 2 连修三坑：当日全仓 fix ≥ 3
        let fixes = commits.filter { $0.kind == "fix" }.count
        if fixes >= 3 {
            unlock("triple-fix", "连修三坑", "🛠️", "单日修复 \(fixes) 处")
        }
        // 3 深夜修罗：23 点后或 5 点前的提交
        if let night = commits.first(where: { let h = cal.component(.hour, from: $0.committedAt); return h >= 23 || h < 5 }) {
            unlock("night-owl", "深夜修罗", "🦉", String(format: "%02d 点仍在提交", cal.component(.hour, from: night.committedAt)))
        }
        // 4 大部队：单日全仓 ≥ 10 提交
        if gazette.totalCommits >= 10 {
            unlock("army", "大部队", "⚔️", "单日 \(gazette.totalCommits) 个提交")
        }
        // 5 清仓大吉：当日有提交且收官时全部积压清零
        if gazette.totalCommits > 0 && gazette.backlogAtClose == 0
            && statuses.contains(where: { $0.remoteConfigured }) {
            unlock("clean-sweep", "清仓大吉", "🧹", "收官时所有仓库积压清零")
        }
        // 6 千军一发：单次成功推送 ≥ 20 提交
        if let big = gazette.pushEvents.max(by: { $0.commitsPushed < $1.commitsPushed }), big.commitsPushed >= 20 {
            unlock("mass-push", "千军一发", "🚀", "一次推送 \(big.commitsPushed) 个提交")
        }

        // 过滤掉历史已解锁的（首日点亮等一次性成就）
        return out.filter { archive[$0.id] == nil }
    }
}

/// 邸报与成就存档的落盘
enum GazetteStore {
    static func loadArchive() -> [String: String] {
        guard let data = try? Data(contentsOf: DirSupport.achievementsURL),
              let decoded = try? JSONDecoder().decode([String: String].self, from: data) else { return [:] }
        return decoded
    }

    static func saveUnlocks(_ unlocked: [Achievement], on day: String) {
        guard !unlocked.isEmpty else { return }
        var archive = loadArchive()
        for a in unlocked where archive[a.id] == nil { archive[a.id] = day }
        if let data = try? JSONEncoder().encode(archive) {
            try? data.write(to: DirSupport.achievementsURL)
        }
    }

    static func appendGazetteDate(_ day: String) {
        var dates: [String] = []
        if let data = try? Data(contentsOf: DirSupport.gazetteDatesURL),
           let decoded = try? JSONDecoder().decode([String].self, from: data) {
            dates = decoded
        }
        guard !dates.contains(day) else { return }
        dates.append(day)
        if let data = try? JSONEncoder().encode(dates) {
            try? data.write(to: DirSupport.gazetteDatesURL)
        }
    }

    struct WriteResult {
        var path: String
    }

    @discardableResult
    static func write(gazette: DailyGazette, outputDir: String) -> WriteResult? {
        writeRaw(markdown: render(gazette), date: gazette.date, outputDir: outputDir)
    }

    /// 直接写入已渲染/润色过的 Markdown
    @discardableResult
    static func writeRaw(markdown: String, date: String, outputDir: String) -> WriteResult? {
        let fm = FileManager.default
        let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
        try? fm.createDirectory(at: dirURL, withIntermediateDirectories: true)
        let fileURL = dirURL.appendingPathComponent("gazette-\(date).md")
        do {
            try markdown.data(using: .utf8)?.write(to: fileURL)
            return WriteResult(path: fileURL.path)
        } catch {
            PLog.error("邸报写入失败: \(error)")
            return nil
        }
    }

    static func render(_ g: DailyGazette) -> String {
        var lines: [String] = []
        lines.append("# 📜 驿站邸报 · \(g.date)")
        lines.append("")
        lines.append("> 生成于 \(Self.timeFormatter.string(from: g.generatedAt)) · 连续活跃 \(g.activeStreak) 天")
        lines.append("")

        lines.append("## 今日概览")
        lines.append("")
        lines.append("- 提交合计 **\(g.totalCommits)**")
        lines.append("- 成功推送 **\(g.pushEvents.count)** 次，共 \(g.pushEvents.reduce(0) { $0 + $1.commitsPushed }) 个提交")
        lines.append("- 收官积压 **\(g.backlogAtClose)** 仓\(g.backlogAtClose == 0 ? " ✅ 清仓" : " ⚠️ 仍有狼烟")")
        lines.append("")

        if !g.repoSummaries.isEmpty {
            lines.append("## 分仓战况")
            lines.append("")
            lines.append("| 仓库 | 提交 | fix | feat | docs/spec | 积压清零 |")
            lines.append("|---|---:|---:|---:|---:|---|")
            for s in g.repoSummaries {
                lines.append("| \(s.repoName) | \(s.commits) | \(s.fixes) | \(s.feats) | \(s.docsSpec) | \(s.backlogCleared ? "✅" : "—") |")
            }
            lines.append("")
        }

        if !g.pushEvents.isEmpty {
            lines.append("## 烽火传递")
            lines.append("")
            for e in g.pushEvents {
                let tag = e.automatic ? "自动补推" : "手动"
                lines.append("- \(Self.timeFormatter.string(from: e.at)) · **\(e.repoName)** \(tag) \(e.commitsPushed) 个提交（\(e.branch)）")
            }
            lines.append("")
        }

        lines.append("## 今日成就")
        lines.append("")
        if g.unlocked.isEmpty {
            lines.append("*今日无新成就，养精蓄锐。*")
        } else {
            for a in g.unlocked {
                lines.append("- \(a.emoji) **\(a.title)** —— \(a.detail)")
            }
        }
        lines.append("")
        lines.append("---")
        lines.append("*驿站 · 烽火台自动生成*")
        return lines.joined(separator: "\n")
    }

    static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = .current
        return f
    }()
}
