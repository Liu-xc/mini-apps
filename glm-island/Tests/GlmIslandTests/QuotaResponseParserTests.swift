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
    func spikeRealResponseShape20260923() throws {
        // M0 spike 实测响应（2026-09-23，open.bigmodel.cn 与 api.z.ai 返回一致）：
        // unit 3×5=5 小时窗口、unit 6×1=每周；percentage=已用%；remaining 等为绝对 token 数
        let json = """
        {"code":200,"msg":"操作成功","data":{"limits":[
          {"type":"CREDIT_LIMIT","unit":3,"number":5,"usage":12000,"currentValue":0,"remaining":12000,"percentage":0,"nextResetTime":1790114662670},
          {"type":"CREDIT_LIMIT","unit":6,"number":1,"usage":60000,"currentValue":40229,"remaining":19770,"percentage":67,"nextResetTime":1790586447976}
        ],"level":"pro"},"success":true}
        """.data(using: .utf8)!
        let snapshot = try QuotaResponseParser.parse(
            json,
            host: "https://open.bigmodel.cn",
            now: date("2026-09-23 16:00:00")
        )

        #expect(snapshot.rows.count == 2)
        #expect(snapshot.debugRawJSON != nil)

        let fiveHour = try #require(snapshot.row(.fiveHour))
        #expect(abs((fiveHour.remainingPercent ?? -1) - 100) < 0.01, "percentage 0 = 未使用，剩余 100%")
        #expect(fiveHour.label == "5 小时")
        #expect(fiveHour.resetDate != nil)

        let weekly = try #require(snapshot.row(.weekly))
        #expect(abs((weekly.remainingPercent ?? -1) - 33) < 0.5, "percentage 67 已用 → 剩余 33%")
        #expect(weekly.label == "每周")

        // MCP 不在该账号 limits 中；displayRows 固定 5 小时 → 每周
        #expect(snapshot.displayRows.map(\.kind) == [.fiveHour, .weekly])
    }

    @Test
    func currentValueUsageRatioFallback() throws {
        // percentage 缺失时用 currentValue/usage 反推
        let json = """
        {"limits":[{"unit":6,"currentValue":40229,"usage":60000}]}
        """.data(using: .utf8)!
        let snapshot = try QuotaResponseParser.parse(json, host: "x")
        let weekly = try #require(snapshot.row(.weekly))
        #expect(abs((weekly.remainingPercent ?? -1) - 32.95) < 0.05)
    }

    @Test
    func labelLessLimitsFallBackToConsoleOrder() throws {
        // 字段没给类型时按控制台顺序对号：5小时 → 每周 → MCP
        let json = """
        {"limits":[{"a":1,"used":10},{"b":2,"used":20},{"c":3,"used":30}]}
        """.data(using: .utf8)!
        let snapshot = try QuotaResponseParser.parse(json, host: "x")

        #expect(abs((snapshot.row(.fiveHour)?.remainingPercent ?? -1) - 90) < 0.01)
        #expect(abs((snapshot.row(.weekly)?.remainingPercent ?? -1) - 80) < 0.01)
        #expect(abs((snapshot.row(.zcodeMcp)?.remainingPercent ?? -1) - 70) < 0.01)
    }

    @Test
    func displayRowsKeepsConsoleOrderAndHidesMCP() {
        let rows = [
            QuotaRow(id: "zcodeMcp", kind: .zcodeMcp, label: "ZCode MCP", remainingPercent: 4, resetDate: nil, percentInferred: false),
            QuotaRow(id: "weekly", kind: .weekly, label: "每周", remainingPercent: 62, resetDate: nil, percentInferred: false),
            QuotaRow(id: "fiveHour", kind: .fiveHour, label: "5 小时", remainingPercent: 33, resetDate: nil, percentInferred: false),
        ]
        let snapshot = UsageSnapshot(rows: rows, fetchedAt: Date(), endpointHost: "x", debugRawJSON: nil)
        // MCP 档按需求不展示（Leo, it-001）；顺序保持 5 小时 → 每周 → 其余
        #expect(snapshot.displayRows.map(\.kind) == [.fiveHour, .weekly])
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
