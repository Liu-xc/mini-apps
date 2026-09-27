import Foundation

/// 通知：osascript 系统通知（无需授权弹窗、无需 bundle 注册），失败静默降级为日志。
struct Notifier {
    var enabled: Bool = true

    func notify(title: String, body: String) {
        guard enabled else { return }
        let escTitle = title.replacingOccurrences(of: "\"", with: "'")
        let escBody = body.replacingOccurrences(of: "\"", with: "'").prefix(200)
        let script = "display notification \"\(escBody)\" with title \"\(escTitle)\" subtitle \"驿站 posthouse\""
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        p.arguments = ["-e", script]
        try? p.run()
        PLog.info("🔔 通知: [\(title)] \(body)")
    }
}
