import Foundation

/// 一次性 CLI 模式（与菜单按钮走同一代码路径）：
///   Posthouse --probe              探测全部仓库并打印 JSON
///   Posthouse --push <repoPath>    手动推送单仓（跳过白名单，仍尊重 behind 红线）
///   Posthouse --gazette            立即生成今日邸报
/// 返回 nil = 非 CLI 模式，正常启动菜单栏 app。
enum CLI {
    static func runOnce(args: [String]) -> Int32? {
        guard args.count >= 2 else { return nil }
        switch args[1] {
        case "--probe":
            return probe()
        case "--push":
            guard args.count >= 3 else { print("用法: --push <repoPath>"); return 2 }
            return push(path: args[2])
        case "--gazette":
            return gazette()
        default:
            return nil
        }
    }

    private static func probe() -> Int32 {
        let cfg = PosthouseConfig.loadOrSeed()
        let engine = WatchEngine(config: cfg)
        // 直接同步探测（不启定时器）
        engine.probeNowSyncForCLI()
        guard let data = try? Data(contentsOf: DirSupport.statusFileURL) else {
            print("探测失败：status.json 未生成"); return 1
        }
        print(String(data: data, encoding: .utf8) ?? "")
        return 0
    }

    private static func push(path: String) -> Int32 {
        let cfg = PosthouseConfig.loadOrSeed()
        let engine = WatchEngine(config: cfg)
        engine.probeNowSyncForCLI()
        guard let data = try? Data(contentsOf: DirSupport.statusFileURL),
              let snap = try? JSONDecoder().decode(WatchEngine.Snapshot.self, from: data),
              let status = snap.repos.first(where: { $0.path == path }) else {
            print("未找到仓库: \(path)"); return 1
        }
        let verdict = PushDecision.evaluateManual(
            ahead: status.ahead, behind: status.behind, remoteConfigured: status.remoteConfigured
        )
        guard case .push = verdict else {
            print("暂不推送: \(Self.skipReason(verdict))"); return 3
        }
        let service = PushService(git: GitShell(), extraArgs: cfg.pushArgs)
        let outcome = service.push(repoPath: path, branch: status.branch, expectedAhead: status.ahead)
        print(outcome.message)
        if outcome.success {
            Notifier(enabled: cfg.notificationsEnabled)
                .notify(title: "✅ \(status.name) 已推送", body: "\(status.ahead) 个提交上去了")
        } else {
            PLog.warn("手动推送失败 \(status.name): \(outcome.message)")
        }
        return outcome.success ? 0 : 1
    }

    private static func gazette() -> Int32 {
        let cfg = PosthouseConfig.loadOrSeed()
        let engine = WatchEngine(config: cfg)
        engine.generateGazette()
        return 0
    }

    private static func skipReason(_ v: PushDecision.Verdict) -> String {
        if case .skip(let r) = v { return r }
        return ""
    }
}
