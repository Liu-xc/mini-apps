import Testing
import Foundation
@testable import Island

@Suite
struct MiMoQuotaParserTests {
    /// spike 2026-09-23 实测响应：percent 为小数比例；limit=0 的补偿包应跳过
    @Test
    func parseRealResponseShape() throws {
        let json = """
        {"code":0,"message":"","data":{
          "monthUsage":{"percent":0.0118,"items":[
            {"name":"month_total_token","used":5387634858,"limit":456000000000,"percent":0.0118}]},
          "usage":{"percent":0.01,"items":[
            {"name":"plan_total_token","used":5387634858,"limit":456000000000,"percent":0.01},
            {"name":"compensation_total_token","used":0,"limit":0,"percent":0}]}}}
        """.data(using: .utf8)!
        let snapshot = try MiMoQuotaParser.parse(json, now: Date(timeIntervalSince1970: 1_789_998_000))

        #expect(snapshot.rows.count == 1)
        #expect(snapshot.endpointHost == "platform.xiaomimimo.com")

        let row = try #require(snapshot.rows.first)
        #expect(row.kind == .mimo)
        #expect(row.label == "MiMo")
        // percent 0.01 已用 → 剩余 99%
        #expect(abs((row.remainingPercent ?? -1) - 99) < 0.5)
        #expect(row.resetDate == nil, "MiMo 接口无重置时间字段")
    }

    @Test
    func envelopeErrorThrows() {
        let json = Data(#"{"code":401,"loginUrl":"https://account.xiaomi.com"}"#.utf8)
        #expect(throws: QuotaParseError.self) {
            try MiMoQuotaParser.parse(json)
        }
    }

    @Test
    func missingDataThrows() {
        let json = Data(#"{"code":0,"message":""}"#.utf8)
        #expect(throws: QuotaParseError.self) {
            try MiMoQuotaParser.parse(json)
        }
    }
}
