import Foundation

/// 扫描监控根目录自身 + 一层内的 git 仓库
enum RepoScanner {
    static func scan(root: String, git: GitRunner) -> [String] {
        var result: [String] = []
        if isGitRepo(root, git: git) { result.append(root) }
        guard let entries = try? FileManager.default.contentsOfDirectory(atPath: root) else { return result }
        result += entries
            .map { (root as NSString).appendingPathComponent($0) }
            .filter { isGitRepo($0, git: git) }
            .sorted()
        return result
    }

    static func isGitRepo(_ path: String, git: GitRunner) -> Bool {
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: path, isDirectory: &isDir), isDir.boolValue else { return false }
        // 不能用 rev-parse --is-inside-work-tree：它对仓库内的子目录也返回 true。
        // .git（目录或 worktree 的文件）只存在于仓库根。
        return FileManager.default.fileExists(atPath: path + "/.git")
    }
}

/// 对单个仓库执行一组只读探测，产出 RepoStatus。
/// 全部命令轻量、只读；远端探测带短超时，失败不阻断本地字段。
struct StatusProbe {
    let git: GitRunner
    var remoteProbeTimeout: TimeInterval = 8

    func probe(path: String) -> RepoStatus {
        let name = (path as NSString).lastPathComponent
        let base = RepoStatus(
            path: path, name: name, branch: "?", ahead: 0, behind: 0, dirtyCount: 0,
            lastCommitAt: nil, remoteConfigured: false, remoteReachable: nil,
            lastError: nil, probedAt: Date()
        )

        guard let branchOut = try? git.run(["rev-parse", "--abbrev-ref", "HEAD"], in: path, timeout: 5),
              branchOut.ok else {
            var s = base
            s.lastError = "not a work tree"
            return s
        }
        let branch = branchOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)

        var status = base
        status.branch = branch

        // 本地脏文件数
        if let dirtyOut = try? git.run(["status", "--porcelain"], in: path, timeout: 10), dirtyOut.ok {
            status.dirtyCount = dirtyOut.stdout.split(separator: "\n").count
        }

        // 最近提交时间
        if let logOut = try? git.run(["log", "-1", "--format=%cI"], in: path, timeout: 5), logOut.ok {
            status.lastCommitAt = Self.iso8601.date(from: logOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines))
        }

        // upstream：优先 @{u}，退回 origin/<branch>
        var upstreamRef = "origin/\(branch)"
        if let upOut = try? git.run(["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"], in: path, timeout: 5),
           upOut.ok {
            let name = upOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)
            if !name.isEmpty { upstreamRef = name }
        }

        if let aheadOut = try? git.run(["rev-list", "--count", "\(upstreamRef)..HEAD"], in: path, timeout: 10) {
            status.ahead = aheadOut.ok ? (Int(aheadOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0) : 0
        }
        if let behindOut = try? git.run(["rev-list", "--count", "HEAD..\(upstreamRef)"], in: path, timeout: 10) {
            status.behind = behindOut.ok ? (Int(behindOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0) : 0
        }

        // 远端
        if let remoteOut = try? git.run(["remote"], in: path, timeout: 5), remoteOut.ok {
            let remotes = remoteOut.stdout.split(separator: "\n").map(String.init)
            status.remoteConfigured = !remotes.isEmpty
        }
        if status.remoteConfigured {
            let probe = try? git.run(["ls-remote", "--exit-code", "origin", "HEAD"], in: path, timeout: remoteProbeTimeout)
            if let probe = probe {
                status.remoteReachable = probe.ok || probe.exitCode == 2 // 2 = 连接成功但无 HEAD 引用，连通即算
            } else {
                status.remoteReachable = false
            }
        }

        return status
    }

    /// 当日提交采集（邸报用）：按提交时间过滤本地+远端已知提交
    func commitsToday(repos: [String], dayStart: Date, dayEnd: Date, calendar: Calendar = .current) -> [CommitEntry] {
        var entries: [CommitEntry] = []
        for path in repos {
            let name = (path as NSString).lastPathComponent
            // %x1f = US 单元分隔符，避免提交信息里的 | 干扰
            guard let out = try? git.run(
                ["log", "--all", "--since=\(Self.iso8601.string(from: dayStart))", "--until=\(Self.iso8601.string(from: dayEnd))",
                 "--pretty=format:%H%x1f%s%x1f%cI%x1f%an"],
                in: path, timeout: 15
            ), out.ok else { continue }
            for line in out.stdout.split(separator: "\n") where !line.isEmpty {
                let f = line.split(separator: "\u{1F}", omittingEmptySubsequences: false).map(String.init)
                guard f.count >= 4,
                      let date = Self.iso8601.date(from: f[2].trimmingCharacters(in: .whitespaces)) else { continue }
                entries.append(CommitEntry(hash: f[0], subject: f[1], committedAt: date, author: f[3], repoName: name))
            }
        }
        return entries.sorted { $0.committedAt < $1.committedAt }
    }

    /// 近 N 天有提交的日期集合（"yyyy-MM-dd"），供连续活跃天数计算
    func commitDayStrings(repos: [String], sinceDays: Int, calendar: Calendar = .current) -> Set<String> {
        let since = calendar.date(byAdding: .day, value: -sinceDays, to: Date()) ?? Date()
        var days = Set<String>()
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = .current
        for path in repos {
            guard let out = try? git.run(
                ["log", "--all", "--since=\(Self.iso8601.string(from: since))", "--format=%cI"],
                in: path, timeout: 15
            ), out.ok else { continue }
            for line in out.stdout.split(separator: "\n") {
                let s = line.trimmingCharacters(in: .whitespaces)
                if let d = Self.iso8601.date(from: s) { days.insert(f.string(from: d)) }
            }
        }
        return days
    }

    static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
}
