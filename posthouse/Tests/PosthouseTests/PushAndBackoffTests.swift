import Testing
import Foundation
@testable import Posthouse

/// M2 安全边界：自动推送决策全部硬规则
struct PushDecisionTests {
    private let now = Date()

    private func input(
        whitelisted: Bool = true, ahead: Int = 3, behind: Int = 0,
        remote: Bool = true, reachable: Bool? = true, cooldownIn: TimeInterval = -1
    ) -> PushDecision.Input {
        .init(
            autoPushWhitelisted: whitelisted, ahead: ahead, behind: behind,
            remoteConfigured: remote, remoteReachable: reachable,
            cooldownUntil: now.addingTimeInterval(cooldownIn), now: now
        )
    }

    @Test func allGreen_pushes() {
        #expect(PushDecision.evaluate(input()) == .push)
    }

    @Test func notWhitelisted_neverPushes() {
        // 硬规则 1：白名单关闭，任何条件都不自动推
        #expect(PushDecision.evaluate(input(whitelisted: false, ahead: 99)) == .skip("白名单未开启"))
    }

    @Test func noBacklog_skips() {
        #expect(PushDecision.evaluate(input(ahead: 0)) != .push)
    }

    @Test func behindRemote_neverAutoAction() {
        // 硬规则 2：本地落后 → 绝不自动 pull/rebase/merge
        guard case .skip(let reason) = PushDecision.evaluate(input(behind: 2)) else {
            Issue.record("应为 skip")
            return
        }
        #expect(reason.contains("绝不自动"))
    }

    @Test func unreachableRemote_skips() {
        #expect(PushDecision.evaluate(input(reachable: false)) != .push)
    }

    @Test func unknownReachability_stillAllows() {
        // 尚未探测（nil）允许尝试——首拍就要探出来
        #expect(PushDecision.evaluate(input(reachable: nil)) == .push)
    }

    @Test func cooldown_skips() {
        #expect(PushDecision.evaluate(input(cooldownIn: 60)) != .push)
    }

    // MARK: 手动推送前置

    @Test func manual_behindBlocked() {
        #expect(PushDecision.evaluateManual(ahead: 1, behind: 1, remoteConfigured: true) != .push)
    }

    @Test func manual_noRemoteBlocked() {
        #expect(PushDecision.evaluateManual(ahead: 1, behind: 0, remoteConfigured: false) != .push)
    }

    @Test func manual_ok() {
        #expect(PushDecision.evaluateManual(ahead: 2, behind: 0, remoteConfigured: true) == .push)
    }
}

/// 退避调度：指数增长、封顶、成功清零、代理僵死识别
struct BackoffTests {
    @Test func exponentialGrowth_withCap() {
        var b = BackoffState()
        let t0 = Date()
        let expected: [TimeInterval] = [30, 60, 120, 240, 480, 600, 600]
        for (i, e) in expected.enumerated() {
            b.recordFailure(kind: .network, now: t0)
            let delay = b.cooldownUntil!.timeIntervalSince(t0)
            #expect(abs(delay - e) < 0.5, "第 \(i + 1) 次失败应退避 \(e)s，实际 \(delay)s")
        }
    }

    @Test func successResets() {
        var b = BackoffState()
        let t0 = Date()
        b.recordFailure(kind: .network, now: t0)
        b.recordFailure(kind: .network, now: t0)
        #expect(b.attempt > 0)
        b.recordSuccess(now: t0)
        #expect(b.attempt == 0)
        #expect(b.cooldownUntil == nil)
    }

    @Test func proxyDeathSmell() {
        var b = BackoffState()
        #expect(!b.smellsLikeProxyDeath())
        b.recordFailure(kind: .network, now: Date())
        b.recordFailure(kind: .network, now: Date())
        b.recordFailure(kind: .network, now: Date())
        #expect(b.smellsLikeProxyDeath(), "连续 3 次网络失败应疑似代理故障")
    }

    @Test func authFailureDoesNotCountAsProxySmell() {
        var b = BackoffState()
        b.recordFailure(kind: .auth, now: Date())
        b.recordFailure(kind: .auth, now: Date())
        b.recordFailure(kind: .auth, now: Date())
        #expect(!b.smellsLikeProxyDeath())
    }
}

/// git 错误归类
struct GitFailureClassifierTests {
    private func out(stderr: String, exit: Int32 = 1, timedOut: Bool = false) -> GitOutput {
        .init(exitCode: exit, stdout: "", stderr: stderr, timedOut: timedOut)
    }

    @Test func timeoutIsNetwork() {
        #expect(GitFailureClassifier.classify(out(stderr: "", timedOut: true)) == .network)
    }

    @Test func nonFF() {
        #expect(GitFailureClassifier.classify(out(stderr: "! [rejected] main -> main (fetch first)")) == .nonFastForward)
    }

    @Test func proxyUnreachableIsNetwork() {
        #expect(GitFailureClassifier.classify(out(stderr: "fatal: unable to access 'https://github.com/': Failed to connect to 127.0.0.1 port 7892")) == .network)
    }

    @Test func auth() {
        #expect(GitFailureClassifier.classify(out(stderr: "fatal: Authentication failed for 'https://github.com/'")) == .auth)
    }

    @Test func other() {
        #expect(GitFailureClassifier.classify(out(stderr: "something weird")) == .other)
    }
}
