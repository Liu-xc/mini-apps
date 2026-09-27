import Testing
import Foundation
@testable import Posthouse

/// 真实 git 集成：探测 + 推送全链路（temp 仓库 + bare remote，不碰真远端）
struct StatusProbeIntegrationTests {
    private let shell = GitShell()

    @Test func probe_reportsAheadDirtyBranch() throws {
        let repo = try TempGit.makeRepo(name: "ahead-repo")
        defer { TempGit.cleanup(repo) }
        let bare = try TempGit.makeBare()
        defer { TempGit.cleanup(bare) }

        try TempGit.commit(repo, file: "a.txt", message: "feat(posthouse): 首个提交")
        try TempGit.run(["remote", "add", "origin", bare], in: repo)
        try TempGit.run(["push", "-u", "origin", "main"], in: repo)

        // 制造积压：2 个已提交 + 1 个未提交
        try TempGit.commit(repo, file: "b.txt", message: "fix(posthouse): 造一个坑")
        try TempGit.commit(repo, file: "c.txt", message: "docs: 再造一个")
        try "dirty".data(using: .utf8)!.write(to: URL(fileURLWithPath: repo + "/d.txt"))

        let status = StatusProbe(git: shell).probe(path: repo)

        #expect(status.branch == "main")
        #expect(status.ahead == 2)
        #expect(status.behind == 0)
        #expect(status.dirtyCount == 1)
        #expect(status.remoteConfigured)
        #expect(status.remoteReachable == true)
        #expect(status.beacon == .backlog)
        #expect(status.lastCommitAt != nil)
    }

    @Test func probe_cleanRepoisOk() throws {
        let repo = try TempGit.makeRepo(name: "clean-repo")
        defer { TempGit.cleanup(repo) }
        try TempGit.commit(repo, file: "a.txt", message: "init")

        let status = StatusProbe(git: shell).probe(path: repo)
        // 无远端：不熄火、不狼烟，全绿
        #expect(status.ahead == 0)
        #expect(!status.remoteConfigured)
        #expect(status.beacon == .ok)
    }

    @Test func probe_deadRemoteFlamesOut() throws {
        let repo = try TempGit.makeRepo(name: "dead-repo")
        defer { TempGit.cleanup(repo) }
        try TempGit.commit(repo, file: "a.txt", message: "init")
        // 指向必然不存在的远端
        try TempGit.run(["remote", "add", "origin", "/nonexistent/posthouse-does-not-exist.git"], in: repo)

        let status = StatusProbe(git: shell).probe(path: repo)
        #expect(status.remoteReachable == false)
        #expect(status.beacon == .unreachable)
    }

    @Test func scanner_findsReposInRoot() throws {
        let root = NSTemporaryDirectory() + "posthouse-scan-\(UUID().uuidString)"
        try FileManager.default.createDirectory(atPath: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(atPath: root) }

        let repoA = root + "/alpha"
        try FileManager.default.createDirectory(atPath: repoA, withIntermediateDirectories: true)
        try TempGit.run(["init"], in: repoA)
        // 非 git 目录应被忽略
        try FileManager.default.createDirectory(atPath: root + "/plain", withIntermediateDirectories: true)
        // 深层目录不扫
        let nested = root + "/beta/nested"
        try FileManager.default.createDirectory(atPath: nested, withIntermediateDirectories: true)
        try TempGit.run(["init"], in: nested)

        let found = RepoScanner.scan(root: root, git: shell)
        #expect(found == [repoA])
    }

    @Test func pushService_reallyPushesToBare() throws {
        let repo = try TempGit.makeRepo(name: "push-repo")
        defer { TempGit.cleanup(repo) }
        let bare = try TempGit.makeBare()
        defer { TempGit.cleanup(bare) }
        try TempGit.commit(repo, file: "a.txt", message: "init")
        try TempGit.run(["remote", "add", "origin", bare], in: repo)
        try TempGit.run(["push", "-u", "origin", "main"], in: repo)

        for (i, msg) in ["feat: 1", "fix: 2", "docs: 3"].enumerated() {
            try TempGit.commit(repo, file: "f\(i).txt", message: msg)
        }

        let service = PushService(git: shell, extraArgs: ["-c", "http.version=HTTP/1.1"])
        let outcome = service.push(repoPath: repo, branch: "main", expectedAhead: 3)
        #expect(outcome.success)

        // 推送后积压清零
        let after = StatusProbe(git: shell).probe(path: repo)
        #expect(after.ahead == 0)
        #expect(after.beacon == .ok)
    }

    @Test func pushService_nonFFRejected_neverForced() throws {
        let repo = try TempGit.makeRepo(name: "ff-repo")
        defer { TempGit.cleanup(repo) }
        let bare = try TempGit.makeBare()
        defer { TempGit.cleanup(bare) }
        try TempGit.commit(repo, file: "a.txt", message: "init")
        try TempGit.run(["remote", "add", "origin", bare], in: repo)
        try TempGit.run(["push", "-u", "origin", "main"], in: repo)

        // 模拟「别人先推了」：另一个克隆推入新提交
        let parent = (repo as NSString).deletingLastPathComponent
        let clone = parent + "/clone"
        try TempGit.run(["clone", "--quiet", bare, clone], in: parent)
        try TempGit.run(["config", "user.email", "x@x"], in: clone)
        try TempGit.run(["config", "user.name", "x"], in: clone)
        try TempGit.commit(clone, file: "other.txt", message: "upstream commit")
        try TempGit.run(["push", "origin", "main"], in: clone)

        // 本地再提交 → push 应被拒（non-FF），且绝不带 --force
        try TempGit.commit(repo, file: "mine.txt", message: "local commit")
        let service = PushService(git: shell, extraArgs: [])
        let outcome = service.push(repoPath: repo, branch: "main", expectedAhead: 1)
        #expect(!outcome.success)
        #expect(outcome.failureKind == .nonFastForward)

        // 远端没有我们的提交（没有被强推覆盖）
        let remoteLog = try TempGit.run(["log", "--oneline", "main"], in: bare)
        #expect(!remoteLog.contains("local commit"))
    }
}
