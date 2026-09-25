import Testing
import Foundation
@testable import Island

/// ADR-009 注册表不变量：新增源时这些测试会强制注册条目齐全且互不冲突
@Suite
struct ProviderRegistryTests {
    /// 注册表与 ProviderKind.allCases 一一对应（新增 case 忘记注册 → 失败）
    @Test
    func registryCoversAllKindsOneToOne() {
        #expect(ProviderRegistry.all.count == ProviderKind.allCases.count)
        #expect(Set(ProviderRegistry.all.map(\.kind)) == Set(ProviderKind.allCases))
        #expect(Set(ProviderRegistry.all.map(\.kind)).count == ProviderRegistry.all.count)
    }

    /// 凭证账户互不冲突（credentials.json 不同 key）
    @Test
    func credentialAccountsAreDistinct() {
        let accounts = ProviderRegistry.all.map(\.credentialAccount)
        #expect(Set(accounts).count == accounts.count)
        for account in accounts {
            #expect(!account.isEmpty)
        }
    }

    /// 文案齐全：标题、设置分区、凭证名词、未配置提示均非空且面板标题与旧 kind.title 一致
    @Test
    func copyFieldsArePresent() {
        for descriptor in ProviderRegistry.all {
            #expect(!descriptor.title.isEmpty)
            #expect(descriptor.title == descriptor.kind.title)
            #expect(!descriptor.sectionTitle.isEmpty)
            #expect(!descriptor.credentialLabel.isEmpty)
            #expect(!descriptor.credentialNoun.isEmpty)
            #expect(!descriptor.credentialHint.isEmpty)
            #expect(!descriptor.unconfiguredText.isEmpty)
        }
    }

    /// 演示数据可直接渲染：非空、id 行内唯一、百分比在 0...100、重置时刻在 now 之后
    @Test
    func demoRowsAreValid() {
        let now = Date()
        for descriptor in ProviderRegistry.all {
            let rows = descriptor.demoRows(now)
            #expect(!rows.isEmpty, "\(descriptor.kind) 的演示数据为空")
            #expect(Set(rows.map(\.id)).count == rows.count)
            for row in rows {
                if let remaining = row.remainingPercent {
                    #expect(remaining >= 0 && remaining <= 100)
                }
                if let reset = row.resetDate {
                    #expect(reset > now)
                }
            }
        }
    }

    /// descriptor(_) 未知 kind 会 preconditionFailure——已注册 kind 必须能取到且指向自身
    @Test
    func descriptorLookupReturnsMatchingKind() {
        for kind in ProviderKind.allCases {
            #expect(ProviderRegistry.descriptor(kind).kind == kind)
        }
    }
}
