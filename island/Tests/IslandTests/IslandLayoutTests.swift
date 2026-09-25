import Testing
import Foundation
@testable import Island

/// IslandLayout（布局单一真源）：宽度/高度公式与明细行数推导
/// ——这些数字就是卡片的实际渲染尺寸，改常量前先想清楚这里的预期。
@Suite
struct IslandLayoutTests {
    // MARK: 卡片宽：≤2 源 352，第 3 源起按 150/源扩展

    @Test
    func cardWidthFormula() {
        #expect(IslandLayout.cardWidth(providerCount: 1) == 352)
        #expect(IslandLayout.cardWidth(providerCount: 2) == 352)
        #expect(IslandLayout.cardWidth(providerCount: 3) == 506)   // 32 + 3×150 + 2×12
        #expect(IslandLayout.cardWidth(providerCount: 4) == 668)   // 32 + 4×150 + 3×12
        #expect(IslandLayout.cardWidth(providerCount: 0) == 352)   // 兜底至少 1 源宽
    }

    @Test
    func panelWidthSplitsCardContent() {
        // 352 - 2×16 内边距 - (n-1)×12 面板间距，均分 n 份
        #expect(IslandLayout.panelWidth(cardWidth: 352, providerCount: 2) == 154)
        #expect(IslandLayout.panelWidth(cardWidth: 506, providerCount: 3) == 150)
        #expect(IslandLayout.panelWidth(cardWidth: 352, providerCount: 0) == IslandLayout.panelWidth(cardWidth: 352, providerCount: 1))
    }

    // MARK: 面板高 = 内边距20 + 头14 + 段距6 + (环66 | 提示44) + 明细

    @Test
    func panelHeightMatchesRenderBranches() {
        // 有环无明细（已配置等首刷 / 单档无绝对量）
        #expect(IslandLayout.panelHeight(hasRing: true, detailLines: 0) == 106)
        // 有环 1 行明细（MiMo 套餐 tokens 行 / GLM 单档）
        #expect(IslandLayout.panelHeight(hasRing: true, detailLines: 1) == 127)
        // 有环 2 行明细（GLM：主档入环 + 每周行）
        #expect(IslandLayout.panelHeight(hasRing: true, detailLines: 2) == 146)
        // 未配置提示块替代环位
        #expect(IslandLayout.panelHeight(hasRing: false, detailLines: 0) == 84)
    }

    @Test
    func expandedHeightStacksAllSections() {
        // 避让32 + 顶距6 + 面板127 + 页脚距10 + 页脚24 + 底距14
        #expect(IslandLayout.expandedHeight(safeTop: 32, panelHeight: 127) == 213)
        #expect(IslandLayout.expandedHeight(safeTop: IslandLayout.minSafeTop, panelHeight: 0)
                == IslandLayout.minSafeTop + IslandLayout.topGap + IslandLayout.footerGap
                    + IslandLayout.footerHeight + IslandLayout.bottomPadding)
    }

    // MARK: 明细行数推导（与 ProviderPanel 渲染分支严格一致）

    @Test
    func detailLineCountFollowsRenderRules() {
        func row(_ id: String, tokens: Bool = false) -> QuotaRow {
            QuotaRow(
                id: id, kind: .other, label: id,
                remainingPercent: 50, resetDate: nil, percentInferred: false,
                usedTokens: tokens ? 1_000 : nil,
                limitTokens: tokens ? 10_000 : nil
            )
        }
        // 多档源：主档入环，行数 = 副档数
        #expect(IslandLayout.detailLineCount(rows: [row("a"), row("b"), row("c")], hasRing: true) == 2)
        // 单档 + 有绝对量 → 1 行 tokens
        #expect(IslandLayout.detailLineCount(rows: [row("a", tokens: true)], hasRing: true) == 1)
        // 单档无绝对量、无副档 → 0 行
        #expect(IslandLayout.detailLineCount(rows: [row("a")], hasRing: true) == 0)
        // 无环（提示块）/ 空数据 → 恒 0
        #expect(IslandLayout.detailLineCount(rows: [row("a"), row("b")], hasRing: false) == 0)
        #expect(IslandLayout.detailLineCount(rows: [], hasRing: true) == 0)
    }

    // MARK: 矩形推导（hitTest / 窗口帧消费的同一公式）

    @Test
    func rectsAnchorToScreenTopCenter() {
        let layout = IslandLayout(
            safeTop: 32, providerCount: 2, panelHeight: 127,
            width: 352, expandedHeight: 213
        )
        let card = layout.cardRect(centerX: 800, screenTop: 900)
        #expect(card.origin.x == CGFloat(800 - 176))
        #expect(card.origin.y == CGFloat(900 - 213))
        #expect(card.size == NSSize(width: 352, height: 213))

        let notch = layout.notchRect(centerX: 800, screenTop: 900)
        #expect(notch.origin.x == 800 - IslandLayout.notchWidth / 2)
        #expect(notch.origin.y == CGFloat(900 - 32))
        #expect(notch.size == NSSize(width: IslandLayout.notchWidth, height: 32))

        // 遮罩两端：隐藏 = 挖槽，展开 = 全卡片
        #expect(layout.notchSize == NSSize(width: IslandLayout.notchWidth, height: 32))
        #expect(layout.expandedSize == NSSize(width: 352, height: 213))
    }
}
