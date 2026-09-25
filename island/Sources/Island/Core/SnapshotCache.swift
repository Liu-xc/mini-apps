import Foundation

/// 每个内容源独立一份快照缓存（snapshot-<kind>.json），
/// 断网/重启后先展示陈旧数据；缓存里只有用量，没有凭证
struct SnapshotCache {
    private let directory: URL
    private let fileName: String

    init(kind: ProviderKind, directory: URL? = nil) {
        self.directory = directory ?? Self.defaultDirectory()
        self.fileName = "snapshot-\(kind.rawValue).json"
    }

    /// 测试用：自定义目录 + 文件名
    init(directory: URL, fileName: String) {
        self.directory = directory
        self.fileName = fileName
    }

    private static func defaultDirectory() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("island", isDirectory: true)
    }

    private var fileURL: URL {
        directory.appendingPathComponent(fileName)
    }

    func save(_ snapshot: UsageSnapshot) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        try encoder.encode(snapshot).write(to: fileURL, options: .atomic)
    }

    func load() throws -> UsageSnapshot {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            throw ProviderError(message: "暂无缓存")
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(UsageSnapshot.self, from: Data(contentsOf: fileURL))
    }
}
