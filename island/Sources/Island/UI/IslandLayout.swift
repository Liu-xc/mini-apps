import AppKit

/// 布局单一真源（it-003 ADR-009）：卡片尺寸、刘海触发区、面板度量全部由本类型一处推导——
/// SwiftUI 遮罩、hitTest 穿透、窗口帧消费**同一实例**。改布局只改这里的常量与公式；
/// 面板高度随明细行数自适应（第三档 other 出现时卡片自动加高，it-001 承诺的公式回归）。
struct IslandLayout {
    // MARK: 静态度量（视觉调整只动这些）

    static let notchWidth: CGFloat = 180        // 隐形触发区宽（= 刘海挖槽）
    static let minSafeTop: CGFloat = 24         // 无刘海屏兜底
    static let sidePadding: CGFloat = 16        // 卡片左右内边距
    static let topGap: CGFloat = 6              // 内容顶边与屏幕顶沿间距（避让挖槽）
    static let panelSpacing: CGFloat = 12       // 面板间距
    static let footerGap: CGFloat = 10          // 面板行与页脚间距
    static let footerHeight: CGFloat = 24       // 页脚行高 = 图标按钮命中区（it-003 AC2 ≥24pt）
    static let bottomPadding: CGFloat = 14      // 卡片底部内边距
    static let panelVPadding: CGFloat = 10      // 面板上下内边距
    static let panelHPadding: CGFloat = 10      // 面板左右内边距
    static let headerHeight: CGFloat = 14       // 面板头（源名 + 主档重置）
    static let sectionGap: CGFloat = 6          // 头↔环、环↔明细的段间距
    static let ringDiameter: CGFloat = 66       // 主环外径
    static let ringWidth: CGFloat = 8           // 主环环宽
    static let hintHeight: CGFloat = 44         // 未配置提示块高（文字+按钮，替代环位）
    static let detailRowHeight: CGFloat = 15    // 环下明细行高
    static let detailRowSpacing: CGFloat = 4    // 明细行间距
    static let panelCornerRadius: CGFloat = 14  // 面板圆角
    static let cardCornerRadius: CGFloat = 16   // 卡片底角（顶角直角贴屏幕顶沿）

    // MARK: 推导（纯函数，可单测）

    /// 卡片宽：≤2 源固定 352；第 3 源起按每源 150pt 扩展（面板并排自动增列）
    static func cardWidth(providerCount: Int) -> CGFloat {
        let n = max(1, providerCount)
        return max(352, sidePadding * 2 + CGFloat(n) * 150 + CGFloat(n - 1) * panelSpacing)
    }

    /// 面板宽：卡片内容宽均分
    static func panelWidth(cardWidth: CGFloat, providerCount: Int) -> CGFloat {
        let n = max(1, providerCount)
        return (cardWidth - sidePadding * 2 - panelSpacing * CGFloat(n - 1)) / CGFloat(n)
    }

    /// 单面板高 = 上下内边距 + 头 + (环 | 提示块) + 明细行
    static func panelHeight(hasRing: Bool, detailLines: Int) -> CGFloat {
        var height = panelVPadding * 2 + headerHeight + sectionGap
            + (hasRing ? ringDiameter : hintHeight)
        if detailLines > 0 {
            height += sectionGap
                + CGFloat(detailLines) * detailRowHeight
                + CGFloat(detailLines - 1) * detailRowSpacing
        }
        return height
    }

    /// 展开卡片高 = 避让 + 面板行 + 页脚间距 + 页脚 + 底距
    static func expandedHeight(safeTop: CGFloat, panelHeight: CGFloat) -> CGFloat {
        safeTop + topGap + panelHeight + footerGap + footerHeight + bottomPadding
    }

    // MARK: 实例（屏幕 + 店铺状态解析）

    let safeTop: CGFloat
    let providerCount: Int
    /// 各面板取高者（面板行等高对齐）
    let panelHeight: CGFloat
    let width: CGFloat
    let expandedHeight: CGFloat

    var notchSize: CGSize { CGSize(width: Self.notchWidth, height: safeTop) }
    var expandedSize: CGSize { CGSize(width: width, height: expandedHeight) }

    var panelWidth: CGFloat {
        Self.panelWidth(cardWidth: width, providerCount: providerCount)
    }

    /// 展开卡片全局矩形（左下原点）
    func cardRect(centerX: CGFloat, screenTop: CGFloat) -> NSRect {
        NSRect(x: centerX - width / 2, y: screenTop - expandedHeight, width: width, height: expandedHeight)
    }

    /// 隐形触发区（刘海挖槽）全局矩形（左下原点）
    func notchRect(centerX: CGFloat, screenTop: CGFloat) -> NSRect {
        NSRect(x: centerX - Self.notchWidth / 2, y: screenTop - safeTop,
               width: Self.notchWidth, height: safeTop)
    }

    /// 由屏幕 + 店铺状态解析当前布局（视图与窗口控制器都走这里，保证同源）
    @MainActor
    static func resolve(store: UsageStore, screen: NSScreen?) -> IslandLayout {
        let safeTop = max(screen?.safeAreaInsets.top ?? 0, minSafeTop)
        let count = ProviderRegistry.all.count

        var maxPanel: CGFloat = 0
        for descriptor in ProviderRegistry.all {
            let state = store.state(descriptor.kind)
            let rows = state.snapshot?.displayRows ?? []
            let configured = store.isConfigured(descriptor.kind)
            // 有数据（真实/演示/陈旧）→ 环；已配置没数据 → 空环等首刷；都没 → 提示块
            let hasRing = !rows.isEmpty || configured
            maxPanel = max(maxPanel, panelHeight(hasRing: hasRing, detailLines: detailLineCount(rows: rows, hasRing: hasRing)))
        }

        let width = cardWidth(providerCount: count)
        return IslandLayout(
            safeTop: safeTop,
            providerCount: count,
            panelHeight: maxPanel,
            width: width,
            expandedHeight: expandedHeight(safeTop: safeTop, panelHeight: maxPanel)
        )
    }

    /// 环下明细行数：多档源 = 副档数；单档源 = 有绝对量则 1（tokens 行）；提示块恒 0。
    /// 与 ProviderPanel 的渲染分支严格一致（高度公式 = 实际内容）。纯函数，可单测。
    static func detailLineCount(rows: [QuotaRow], hasRing: Bool) -> Int {
        guard hasRing, !rows.isEmpty else { return 0 }
        if rows.count > 1 { return rows.count - 1 }
        let primary = rows[0]
        return (primary.usedTokens != nil && primary.limitTokens != nil) ? 1 : 0
    }
}
