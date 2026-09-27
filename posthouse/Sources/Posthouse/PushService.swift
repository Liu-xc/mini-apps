import Foundation

/// 执行真实推送。只追加 `-c` 参数，永不 force；被拒时原样返回 stderr 供分类。
struct PushService {
    let git: GitRunner
    let extraArgs: [String]   // 来自配置 pushArgs

    struct Outcome {
        var success: Bool
        var failureKind: PushFailureKind?
        var message: String
    }

    /// 推送指定分支到其远端。commitsPushed 由调用方在推前捕获（expectedAhead）传入。
    func push(repoPath: String, branch: String, expectedAhead: Int) -> Outcome {
        // 找远端名：origin 优先，否则第一个
        let remote: String
        if let out = try? git.run(["remote"], in: repoPath, timeout: 5), out.ok {
            let names = out.stdout.split(separator: "\n").map(String.init)
            if names.isEmpty {
                return Outcome(success: false, failureKind: .other, message: "无远端")
            }
            remote = names.contains("origin") ? "origin" : names[0]
        } else {
            return Outcome(success: false, failureKind: .other, message: "无法枚举远端")
        }

        var args = extraArgs
        args += ["push", remote, branch]
        PLog.info("git " + args.joined(separator: " ") + "   (cwd: \(repoPath))")
        guard let out = try? git.run(args, in: repoPath, timeout: 120) else {
            return Outcome(success: false, failureKind: .network, message: "git 进程异常")
        }

        if out.ok {
            return Outcome(success: true, failureKind: nil, message: "推送成功（\(expectedAhead) 个提交）")
        }

        let kind = GitFailureClassifier.classify(out)
        let msg = out.stderr.trimmingCharacters(in: .whitespacesAndNewlines)
        return Outcome(success: false, failureKind: kind, message: msg.isEmpty ? "未知错误" : msg)
    }
}
