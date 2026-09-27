import Foundation

/// 自动推送决策——纯函数，安全边界全部在此，单测覆盖。
/// 硬规则：白名单关闭绝不自动推；远端确认不通不推；退避冷却期内不推；behind 不推（绝不自动 pull/rebase/merge）。
enum PushDecision {
    struct Input {
        var autoPushWhitelisted: Bool
        var ahead: Int
        var behind: Int
        var remoteConfigured: Bool
        var remoteReachable: Bool?   // nil = 尚未探测
        var cooldownUntil: Date
        var now: Date
    }

    enum Verdict: Equatable {
        case push
        case skip(String)
    }

    static func evaluate(_ i: Input) -> Verdict {
        guard i.autoPushWhitelisted else { return .skip("白名单未开启") }
        guard i.ahead > 0 else { return .skip("无积压") }
        guard i.behind == 0 else { return .skip("本地落后远端，绝不自动动作，仅报警") }
        guard i.remoteConfigured else { return .skip("无远端") }
        if i.remoteReachable == false { return .skip("远端不通") }
        guard i.now >= i.cooldownUntil else { return .skip("退避冷却中") }
        return .push
    }

    /// 手动推送前置校验（手动跳过白名单与冷却，但仍尊重 behind 提示）
    static func evaluateManual(ahead: Int, behind: Int, remoteConfigured: Bool) -> Verdict {
        guard remoteConfigured else { return .skip("无远端") }
        guard behind == 0 else { return .skip("本地落后远端，请先手动处理") }
        guard ahead > 0 else { return .skip("无积压") }
        return .push
    }
}

/// 仓库级退避状态：网络失败按 30s 起步指数退避到 10 分钟封顶。
struct BackoffState: Codable {
    var attempt: Int = 0
    var cooldownUntil: Date? = nil
    var consecutiveNetworkFailures: Int = 0

    mutating func recordFailure(kind: PushFailureKind, now: Date, base: TimeInterval = 30, cap: TimeInterval = 600) {
        if kind == .network { consecutiveNetworkFailures += 1 } else { consecutiveNetworkFailures = 0 }
        attempt += 1
        let delay = min(base * pow(2, Double(attempt - 1)), cap)
        cooldownUntil = now.addingTimeInterval(delay)
    }

    mutating func recordSuccess(now: Date) {
        attempt = 0
        cooldownUntil = nil
        consecutiveNetworkFailures = 0
    }

    /// 连续网络失败 ≥ N 视为疑似代理故障（通知提示用）
    func smellsLikeProxyDeath(threshold: Int = 3) -> Bool {
        consecutiveNetworkFailures >= threshold
    }
}
