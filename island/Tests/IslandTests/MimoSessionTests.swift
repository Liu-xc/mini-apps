import Testing
import Foundation
@testable import Island

/// it-004：登录态纯工具——Cookie 提取、serviceToken 判定、401 loginUrl 解析
@Suite
struct MimoSessionTests {
    private func cookie(_ name: String, _ value: String, domain: String) -> HTTPCookie {
        HTTPCookie(properties: [.name: name, .value: value, .domain: domain, .path: "/"])!
    }

    @Test
    func cookieHeaderTakesOnlyXiaomimimoDomain() {
        let cookies = [
            cookie("api-platform_serviceToken", "abc123", domain: ".xiaomimimo.com"),
            cookie("userId", "10001", domain: "platform.xiaomimimo.com"),
            cookie("api-platform_slh", "s3cr3t", domain: ".xiaomimimo.com"),
            cookie("PASS_TOKEN", "should-drop", domain: ".xiaomi.com"),   // 账号域不进平台请求头
        ]
        let header = MimoSSO.cookieHeader(from: cookies)
        #expect(header.contains("api-platform_serviceToken=abc123"))
        #expect(header.contains("userId=10001"))
        #expect(header.contains("api-platform_slh=s3cr3t"))
        #expect(!header.contains("PASS_TOKEN"))
        #expect(!header.contains("xiaomi.com"))
    }

    @Test
    func hasServiceTokenRequiresNonEmptyValue() {
        #expect(MimoSSO.hasServiceToken("api-platform_serviceToken=tok; userId=1"))
        #expect(!MimoSSO.hasServiceToken("api-platform_serviceToken=; userId=1"))
        #expect(!MimoSSO.hasServiceToken("userId=1"))
        #expect(!MimoSSO.hasServiceToken(""))
    }

    /// 2026-09-29 实测 401 响应体形态（loginUrl 指向账号 SSO）
    @Test
    func loginURLParsesFrom401Body() {
        let body = Data(#"{"code":401,"loginUrl":"https://account.xiaomi.com/pass/serviceLogin?sid=api-platform"}"#.utf8)
        let url = MimoSSO.loginURL(from401Body: body)
        #expect(url?.host == "account.xiaomi.com")
        #expect(url?.path.hasPrefix("/pass/serviceLogin") == true)
    }

    @Test
    func loginURLAbsentWhenBodyMissing() {
        #expect(MimoSSO.loginURL(from401Body: Data(#"{"code":401}"#.utf8)) == nil)
        #expect(MimoSSO.loginURL(from401Body: Data("not-json".utf8)) == nil)
    }

    @Test
    func hostClassificationForRenewal() {
        #expect(MimoSSO.isPlatformURL(URL(string: "https://platform.xiaomimimo.com/console/plan-manage")))
        #expect(MimoSSO.isPlatformURL(URL(string: "https://platform.xiaomimimo.com/sts?sign=x")))
        #expect(!MimoSSO.isPlatformURL(URL(string: "https://account.xiaomi.com/pass/serviceLogin")))
        #expect(MimoSSO.isAccountLoginURL(URL(string: "https://account.xiaomi.com/fe/service/login")))
        #expect(!MimoSSO.isAccountLoginURL(nil))
    }

    /// 静默续期闸门状态机：四种状态的持久化编码互不冲突
    @Test
    func sessionStateRawValuesAreDistinct() {
        let raws = SessionState.allCases.map(\.rawValue)
        #expect(Set(raws).count == raws.count)
        #expect(SessionState(rawValue: "active") == .active)
        #expect(SessionState(rawValue: "expired") == .expired)
        #expect(SessionState(rawValue: "bogus") == nil)
    }
}
