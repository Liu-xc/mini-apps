import Testing
import Foundation
@testable import Posthouse

/// 测试工具：临时 git 仓库（真实 git CLI）
enum TempGit {
    static func makeRepo(name: String = "repo") throws -> String {
        let root = NSTemporaryDirectory().appending("posthouse-test-\(UUID().uuidString)")
        let path = root + "/" + name
        try FileManager.default.createDirectory(atPath: path, withIntermediateDirectories: true)
        try run(["init", "-b", "main"], in: path)
        try run(["config", "user.email", "test@posthouse.local"], in: path)
        try run(["config", "user.name", "posthouse-test"], in: path)
        return path
    }

    static func makeBare(name: String = "origin.git") throws -> String {
        let root = NSTemporaryDirectory().appending("posthouse-test-\(UUID().uuidString)")
        let path = root + "/" + name
        try FileManager.default.createDirectory(atPath: path, withIntermediateDirectories: true)
        try run(["init", "--bare"], in: path)
        return path
    }

    @discardableResult
    static func run(_ args: [String], in dir: String, env: [String: String] = [:]) throws -> String {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/git")
        p.arguments = args
        p.currentDirectoryURL = URL(fileURLWithPath: dir)
        var environment = ProcessInfo.processInfo.environment
        for (k, v) in env { environment[k] = v }
        p.environment = environment
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        try p.run()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        guard p.terminationStatus == 0 else {
            throw NSError(domain: "tempgit", code: Int(p.terminationStatus),
                          userInfo: [NSLocalizedDescriptionKey: String(data: data, encoding: .utf8) ?? ""])
        }
        return String(data: data, encoding: .utf8) ?? ""
    }

    /// 提交一个文件；可指定提交时间（同时写 author 与 committer 日期）
    static func commit(_ path: String, file: String, message: String, at date: Date? = nil) throws {
        let filePath = (path as NSString).appendingPathComponent(file)
        try "content-\(UUID())".data(using: .utf8)!.write(to: URL(fileURLWithPath: filePath))
        try run(["add", "."], in: path)
        var env: [String: String] = [:]
        if let date {
            let iso = ISO8601DateFormatter().string(from: date)
            env["GIT_AUTHOR_DATE"] = iso
            env["GIT_COMMITTER_DATE"] = iso
        }
        try run(["commit", "-m", message], in: path, env: env)
    }

    static func cleanup(_ path: String) {
        let root = (path as NSString).deletingLastPathComponent
        try? FileManager.default.removeItem(atPath: root)
    }
}
