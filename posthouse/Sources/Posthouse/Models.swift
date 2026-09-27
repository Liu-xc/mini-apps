import Foundation

// MARK: - 烽火台状态

/// 单仓库整体三态：全绿 / 狼烟（有积压）/ 熄火（远端不通）
enum BeaconState: String, Codable {
    case ok
    case backlog
    case unreachable

    var systemImage: String {
        switch self {
        case .ok: return "circlebadge.fill"
        case .backlog: return "flame.fill"
        case .unreachable: return "bolt.slash.fill"
        }
    }
}

/// 单仓库一次探测的完整快照
struct RepoStatus: Codable, Identifiable {
    var path: String
    var name: String
    var branch: String
    var ahead: Int
    var behind: Int
    var dirtyCount: Int
    var lastCommitAt: Date?
    var remoteConfigured: Bool
    var remoteReachable: Bool?
    var lastError: String?
    var probedAt: Date

    var id: String { path }

    /// 狼烟判定：有未推送提交，或远端配置了但探测不通
    var beacon: BeaconState {
        if remoteConfigured && remoteReachable == false && ahead >= 0 { return .unreachable }
        if ahead > 0 || behind > 0 { return .backlog }
        return .ok
    }

    var summaryLine: String {
        var parts = [branch]
        if ahead > 0 { parts.append("↑\(ahead)") }
        if behind > 0 { parts.append("↓\(behind)") }
        if dirtyCount > 0 { parts.append("✎\(dirtyCount)") }
        return parts.joined(separator: "  ")
    }
}

/// 全仓库聚合态（菜单栏图标依据）：任一熄火 > 任一狼烟 > 全绿
extension Array where Element == RepoStatus {
    var overallBeacon: BeaconState {
        if contains(where: { $0.beacon == .unreachable }) { return .unreachable }
        if contains(where: { $0.beacon == .backlog }) { return .backlog }
        return .ok
    }
}

// MARK: - 提交与推送事件

struct CommitEntry: Codable {
    var hash: String
    var subject: String
    var committedAt: Date
    var author: String
    var repoName: String

    /// 提交规范 `feat|fix|docs|spec|chore(scope): …` 的类型前缀；无前缀归 other
    var kind: String {
        guard let m = subject.range(of: "^[a-z]+", options: .regularExpression) else { return "other" }
        return String(subject[m])
    }
}

struct PushEvent: Codable {
    var repoName: String
    var at: Date
    var commitsPushed: Int
    var branch: String
    var automatic: Bool
    var success: Bool
}

// MARK: - 邸报

struct RepoDaySummary: Codable {
    var repoName: String
    var commits: Int
    var fixes: Int
    var feats: Int
    var docsSpec: Int
    var backlogCleared: Bool   // 当日结束时积压清零
}

struct Achievement: Codable, Identifiable {
    var id: String
    var title: String
    var emoji: String
    var detail: String
}

struct DailyGazette: Codable {
    var date: String                 // yyyy-MM-dd
    var generatedAt: Date
    var repoSummaries: [RepoDaySummary]
    var todayCommits: [CommitEntry]  // 当日全部提交（渲染逐条首行）
    var totalCommits: Int
    var pushEvents: [PushEvent]
    var backlogAtClose: Int          // 收官时全仓积压仓库数
    var unlocked: [Achievement]
    var activeStreak: Int            // 连续有提交天数
}
