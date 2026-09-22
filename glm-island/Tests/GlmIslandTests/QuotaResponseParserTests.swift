import Testing
import Foundation
@testable import GlmIsland

@Suite
struct QuotaResponseParserTests {
    private func date(_ text: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = TimeZone.current
        return formatter.date(from: text)!
    }

    @Test
    func parseWithRatioAsUsedAndMixedFieldStyles() throws {
        // 形状假设 A：data.limits；ratio=已用比例(0-1)、usage=已用百分数、remaining 直给、毫秒时间戳
        let json = """
        {"code":200,"success":true,"data":{"limits":[
          {"windowType":"5_hours","ratio":0.67,"resetTime":"2026-09-22T19:00:00+08:00"},
          {"windowType":"weekly","usage":38,"resetTime":"2026-09-28T00:00:00+08:00"},
          {"tool":"zcode_mcp","remaining":100,"resetTime":1789999200000}
        ]}}
        """.data(using: .utf8)!
        let snapshot = try QuotaResponseParser.parse(
            json,
            host: "https://open.bigmodel.cn",
            now: date("2026-09-22 17:00:00")
        )

        #expect(snapshot.rows.count == 3)
        #expect(snapshot.endpointHost == "https://open.bigmodel.cn")
        #expect(snapshot.debugRawJSON != nil)

        let fiveHour = try #require(snapshot.row(.fiveHour))
        #expect(abs((fiveHour.remainingPercent ?? -1) - 33) < 0.01)
        #expect(fiveHour.percentInferred)
        #expect(fiveHour.label == "5 小时")

        let weekly = try #require(snapshot.row(.weekly))
        #expect(abs((weekly.remainingPercent ?? -1) - 62) < 0.01)

        let mcp = try #require(snapshot.row(.zcodeMcp))
        #expect(abs((mcp.remainingPercent ?? -1) - 100) < 0.01)
        #expect(!mcp.percentInferred)
        #expect(mcp.resetDate != nil)
    }

    @Test
    func labelLessLimitsFallBackToConsoleOrder() throws {
        // 字段没给类型时按控制台顺序对号：5小时 → 每周 → MCP
        let json = """
        {"limits":[{"a":1,"usage":10},{"b":2,"usage":20},{"c":3,"usage":30}]}
        """.data(using: .utf8)!
        let snapshot = try QuotaResponseParser.parse(json, host: "x")

        #expect(abs((snapshot.row(.fiveHour)?.remainingPercent ?? -1) - 90) < 0.01)
        #expect(abs((snapshot.row(.weekly)?.remainingPercent ?? -1) - 80) < 0.01)
        #expect(abs((snapshot.row(.zcodeMcp)?.remainingPercent ?? -1) - 70) < 0.01)
    }

    @Test
    func displayRowsKeepsConsoleOrder() {
        let rows = [
            QuotaRow(id: "zcodeMcp", kind: .zcodeMcp, label: "ZCode MCP", remainingPercent: 4, resetDate: nil, percentInferred: false),
            QuotaRow(id: "weekly", kind: .weekly, label: "每周", remainingPercent: 62, resetDate: nil, percentInferred: false),
        ]
        let snapshot = UsageSnapshot(rows: rows, fetchedAt: Date(), endpointHost: "x", debugRawJSON: nil)
        #expect(snapshot.displayRows.map(\.kind) == [.weekly, .zcodeMcp])
    }

    @Test
    func envelopeBusinessErrorThrows() {
        let json = Data(#"{"code":401,"success":false,"msg":"令牌已过期或验证不正确"}"#.utf8)
        #expect(throws: QuotaParseError.self) {
            try QuotaResponseParser.parse(json, host: "x")
        }
    }

    @Test
    func codeOnlyEnvelopeThrows() {
        let json = Data(#"{"code":401,"msg":"令牌已过期"}"#.utf8)
        #expect(throws: QuotaParseError.self) {
            try QuotaResponseParser.parse(json, host: "x")
        }
    }

    @Test
    func noLimitsThrows() {
        let json = Data(#"{"code":200,"data":{}}"#.utf8)
        #expect(throws: QuotaParseError.self) {
            try QuotaResponseParser.parse(json, host: "x")
        }
    }

    @Test
    func malformedBodyThrows() {
        #expect(throws: QuotaParseError.self) {
            try QuotaResponseParser.parse(Data("not json".utf8), host: "x")
        }
    }

    @Test
    func classify() {
        #expect(QuotaResponseParser.classify("TIME_WINDOW_5H") == .fiveHour)
        #expect(QuotaResponseParser.classify("weekly") == .weekly)
        #expect(QuotaResponseParser.classify("zcode_mcp") == .zcodeMcp)
        #expect(QuotaResponseParser.classify("whatever") == .other)
        #expect(QuotaResponseParser.classify(nil) == .other)
    }
}
