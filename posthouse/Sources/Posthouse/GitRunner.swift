import Foundation

struct GitOutput {
    var exitCode: Int32
    var stdout: String
    var stderr: String
    var timedOut: Bool

    var ok: Bool { exitCode == 0 && !timedOut }
}

enum GitError: Error, CustomStringConvertible {
    case spawnFailed(String)
    case timeout(seconds: TimeInterval)

    var description: String {
        switch self {
        case .spawnFailed(let m): return "git 进程启动失败: \(m)"
        case .timeout(let s): return "git 超时(\(Int(s))s)"
        }
    }
}

/// git 交互抽象：生产走 GitShell（Process），测试可注入 fake。
protocol GitRunner {
    func run(_ args: [String], in directory: String, timeout: TimeInterval) throws -> GitOutput
}

struct GitShell: GitRunner {
    /// 外部参数（HTTP/1.1 + postBuffer 等）以 `-c key=value` 前缀注入，不改写仓库自身 git 配置。
    func run(_ args: [String], in directory: String, timeout: TimeInterval) throws -> GitOutput {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/git")
        process.arguments = args
        process.currentDirectoryURL = URL(fileURLWithPath: directory)
        process.environment = ProcessInfo.processInfo.environment

        let outPipe = Pipe()
        let errPipe = Pipe()
        process.standardOutput = outPipe
        process.standardError = errPipe

        do {
            try process.run()
        } catch {
            throw GitError.spawnFailed(error.localizedDescription)
        }

        // 读管道放后台，避免输出满缓冲死锁
        var outData = Data()
        var errData = Data()
        let group = DispatchGroup()
        let ioQueue = DispatchQueue(label: "posthouse.gitio", attributes: .concurrent)
        group.enter()
        DispatchQueue.global(qos: .userInitiated).async {
            let d = outPipe.fileHandleForReading.readDataToEndOfFile()
            ioQueue.sync { outData = d }
            group.leave()
        }
        group.enter()
        DispatchQueue.global(qos: .userInitiated).async {
            let d = errPipe.fileHandleForReading.readDataToEndOfFile()
            ioQueue.sync { errData = d }
            group.leave()
        }

        let deadline = Date().addingTimeInterval(timeout)
        var timedOut = false
        while process.isRunning {
            if Date() > deadline {
                timedOut = true
                process.terminate()
                // 给 0.5s 体面退出机会，仍活着就强杀
                _ = process.waitForExit(timeout: 0.5)
                if process.isRunning { process.terminate() }
                break
            }
            Thread.sleep(forTimeInterval: 0.02)
        }
        if !timedOut { process.waitUntilExit() }
        group.wait()

        let out = String(data: outData, encoding: .utf8) ?? ""
        let err = String(data: errData, encoding: .utf8) ?? ""
        return GitOutput(
            exitCode: process.terminationStatus,
            stdout: out,
            stderr: err,
            timedOut: timedOut
        )
    }
}

extension Process {
    /// 等待进程退出，最多 timeout 秒；返回是否已退出。
    @discardableResult
    func waitForExit(timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while isRunning && Date() < deadline {
            Thread.sleep(forTimeInterval: 0.01)
        }
        return !isRunning
    }
}

enum GitFailureClassifier {
    /// 把一次失败 git 调用归类：网络类 / 凭证类 / non-FF 拒绝 / 其他。
    static func classify(_ output: GitOutput) -> PushFailureKind {
        let err = output.stderr.lowercased()
        if output.timedOut { return .network }
        if err.contains("fetch first") || err.contains("rejected") && err.contains("behind") { return .nonFastForward }
        if err.contains("could not resolve host") || err.contains("connection")
            || err.contains("timed out") || err.contains("unable to access")
            || err.contains("proxy") || err.contains("ssl") { return .network }
        if err.contains("authentication") || err.contains("permission to") || err.contains("403") { return .auth }
        if err.contains("non-fast-forward") { return .nonFastForward }
        return .other
    }
}

enum PushFailureKind: String, Codable {
    case network, auth, nonFastForward, other
}
