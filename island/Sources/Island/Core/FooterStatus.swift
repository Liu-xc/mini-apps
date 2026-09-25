import Foundation

/// 页脚状态（it-003）：纯函数推导，便于单测。
/// 优先级：**在途 > 活跃错误 > 新鲜度 > 待刷新**——刷新中给即时反馈，
/// 错误在刷新结束后持续明示（任一源失败不再被整体状态吞掉，修 it-002 AC4）。
enum FooterStatus {
    enum Kind: Equatable {
        case loading
        case error(String)
        case age(Date)
        case idle
    }

    /// registry 顺序取首个活跃错误（页脚单行，多错展示优先级与面板顺序一致）
    static func kind(
        states: [ProviderKind: SourceState],
        registry: [ProviderKind],
        lastFetchedAt: Date?
    ) -> Kind {
        if states.values.contains(where: \.isFetching) { return .loading }
        for kind in registry {
            if let error = states[kind]?.lastError { return .error(error) }
        }
        if let lastFetchedAt { return .age(lastFetchedAt) }
        return .idle
    }

    static func text(_ kind: Kind, demo: Bool, now: Date) -> String {
        let prefix = demo ? "演示 · " : ""
        let body: String = switch kind {
        case .loading: "刷新中…"
        case .error(let message): message
        case .age(let date): ResetFormatter.relativeAge(date, now: now) + "已刷新"
        case .idle: "待刷新"
        }
        return prefix + body
    }

    /// 错误用健康度红，其余用次级白
    static func isError(_ kind: Kind) -> Bool {
        if case .error = kind { return true }
        return false
    }
}
