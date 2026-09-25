import Testing
import Foundation
@testable import Island

@Suite
struct SnapshotCacheTests {
    @Test
    func roundTrip() throws {
        let cache = SnapshotCache(
            directory: FileManager.default.temporaryDirectory
                .appendingPathComponent("island-tests-\(UUID().uuidString)"),
            fileName: "snapshot-test.json"
        )
        let rows = [
            QuotaRow(
                id: RowKind.fiveHour.rawValue,
                kind: .fiveHour,
                label: "5 小时",
                remainingPercent: 33.3,
                resetDate: Date(timeIntervalSince1970: 1_789_999_200),
                percentInferred: false
            ),
        ]
        let snapshot = UsageSnapshot(
            rows: rows,
            fetchedAt: Date(timeIntervalSince1970: 1_789_998_000),
            endpointHost: "https://open.bigmodel.cn",
            debugRawJSON: "{}"
        )

        try cache.save(snapshot)
        let loaded = try cache.load()

        #expect(loaded == snapshot)
    }

    @Test
    func loadMissingThrows() {
        let cache = SnapshotCache(
            directory: FileManager.default.temporaryDirectory
                .appendingPathComponent("island-tests-missing-\(UUID().uuidString)"),
            fileName: "snapshot-test.json"
        )
        #expect(throws: (any Error).self) {
            try cache.load()
        }
    }
}
