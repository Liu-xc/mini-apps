import Testing
import Foundation
@testable import Island

/// 页脚状态纯函数：优先级 在途 > 错误 > 新鲜度 > 待刷新（it-003 P0-3 / AC4）
@Suite
struct FooterStatusTests {
    private let registry: [ProviderKind] = [.glm, .mimo]
    private let ref = Date(timeIntervalSince1970: 1_789_998_000)

    private func states(
        glmFetching: Bool = false, glmError: String? = nil,
        mimoFetching: Bool = false, mimoError: String? = nil
    ) -> [ProviderKind: SourceState] {
        [
            .glm: SourceState(snapshot: nil, lastError: glmError, isFetching: glmFetching),
            .mimo: SourceState(snapshot: nil, lastError: mimoError, isFetching: mimoFetching),
        ]
    }

    @Test
    func loadingBeatsEverything() {
        let kind = FooterStatus.kind(
            states: states(glmFetching: true, mimoError: "mimo 挂了"),
            registry: registry,
            lastFetchedAt: ref
        )
        #expect(kind == .loading)
    }

    @Test
    func firstErrorInRegistryOrderWins() {
        let kind = FooterStatus.kind(
            states: states(glmError: "glm 挂了", mimoError: "mimo 挂了"),
            registry: registry,
            lastFetchedAt: ref
        )
        #expect(kind == .error("glm 挂了"), "页脚单行按注册表顺序取首个错误")
    }

    @Test
    func errorBeatsAge() {
        let kind = FooterStatus.kind(
            states: states(mimoError: "mimo 挂了"),
            registry: registry,
            lastFetchedAt: ref
        )
        #expect(kind == .error("mimo 挂了"))
    }

    @Test
    func ageWhenQuietAndFetchedBefore() {
        let kind = FooterStatus.kind(states: states(), registry: registry, lastFetchedAt: ref)
        #expect(kind == .age(ref))
    }

    @Test
    func idleWhenNeverFetched() {
        let kind = FooterStatus.kind(states: [:], registry: registry, lastFetchedAt: nil)
        #expect(kind == .idle)
    }

    @Test
    func textRendersWithDemoPrefix() {
        let now = ref.addingTimeInterval(90)
        #expect(FooterStatus.text(.loading, demo: false, now: now) == "刷新中…")
        #expect(FooterStatus.text(.loading, demo: true, now: now) == "演示 · 刷新中…")
        #expect(FooterStatus.text(.error("Key 过期"), demo: false, now: now) == "Key 过期")
        #expect(FooterStatus.text(.idle, demo: false, now: now) == "待刷新")
        let age = FooterStatus.text(.age(ref), demo: false, now: now)
        #expect(age.hasSuffix("已刷新") && age != "已刷新", "相对时间 + 已刷新")
        #expect(FooterStatus.text(.age(ref), demo: true, now: now).hasPrefix("演示 · "))
    }

    @Test
    func isErrorClassification() {
        #expect(FooterStatus.isError(.error("x")))
        #expect(!FooterStatus.isError(.loading))
        #expect(!FooterStatus.isError(.age(ref)))
        #expect(!FooterStatus.isError(.idle))
    }
}
