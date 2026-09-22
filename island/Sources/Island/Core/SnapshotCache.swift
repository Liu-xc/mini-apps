import Foundation

/// 最近一次快照落盘（Application Support/island/snapshot.json），
/// 断网/重启后先展示陈旧数据；缓存里只有用量，没有 Key
struct SnapshotCache {
    private let directory: URL

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("island", isDirectory: true)
        }
    }

    private var fileURL: URL {
        directory.appendingPathComponent("snapshot.json")
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
