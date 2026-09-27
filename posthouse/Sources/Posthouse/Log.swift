import Foundation
import os

/// 文件日志：验收与排障的证据链（~/Library/Application Support/Posthouse/posthouse.log）
enum PLog {
    private static let queue = DispatchQueue(label: "posthouse.log")
    private static let df: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        f.timeZone = .current
        return f
    }()

    static var logFileURL: URL {
        let dir = DirSupport.appSupport
        return dir.appendingPathComponent("posthouse.log")
    }

    static func info(_ msg: String) { write("INFO", msg) }
    static func warn(_ msg: String) { write("WARN", msg) }
    static func error(_ msg: String) { write("ERR ", msg) }

    private static func write(_ level: String, _ msg: String) {
        let line = "[\(df.string(from: Date()))] \(level) \(msg)\n"
        queue.sync {
            let dir = DirSupport.appSupport
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            if let fh = try? FileHandle(forWritingTo: logFileURL) {
                defer { try? fh.close() }
                _ = try? fh.seekToEnd()
                fh.write(Data(line.utf8))
            } else {
                try? line.data(using: .utf8)?.write(to: logFileURL)
            }
        }
        os_log("%{public}@", type: .default, msg)
    }
}

enum DirSupport {
    static var appSupport: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("Posthouse", isDirectory: true)
    }

    static var statusFileURL: URL { appSupport.appendingPathComponent("status.json") }
    static var configFileURL: URL { appSupport.appendingPathComponent("config.json") }
    static var pushEventsURL: URL { appSupport.appendingPathComponent("push-events.json") }
    static var achievementsURL: URL { appSupport.appendingPathComponent("achievements.json") }
    static var gazetteDatesURL: URL { appSupport.appendingPathComponent("gazette-dates.json") }
}
