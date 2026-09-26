import SwiftUI
import AppKit

/// 灵岛根视图（it-003 改版：单环主锚 + 行式明细）。
/// 卡片常驻层级内，可见区域由「随显隐状态变化的圆角遮罩」驱动（ADR-007 终极架构）：
/// 隐藏 = 刘海挖槽尺寸（黑区融合不可见）；展开 = 全尺寸。无视图插入/移除，零闪现。
/// 尺寸全部取自 IslandLayout（单一真源），与窗口帧、hitTest 判定同源。
struct IslandRootView: View {
    @ObservedObject var store: UsageStore
    @ObservedObject var viewModel: IslandViewModel
    let openSettings: () -> Void

    private var layout: IslandLayout {
        IslandLayout.resolve(store: store, screen: Self.activeScreen())
    }

    var body: some View {
        card
            .frame(width: layout.width, height: layout.expandedHeight, alignment: .top)
            .contentShape(Rectangle())
            .onTapGesture {
                NSLog("[island][tap] 卡片 onTapGesture 触发（请求固定/收起）")
                viewModel.requestTogglePin()
            }
    }

    /// 卡片可见区域 = 动画化圆角遮罩（动画只挂在遮罩尺寸上：显隐与数据驱动的高度变化同一弹簧）
    private var card: some View {
        ExpandedIslandView(store: store, openSettings: openSettings, layout: layout)
            .frame(width: layout.width, height: layout.expandedHeight, alignment: .top)
            .background(Color.black)
            .mask {
                let size = maskSize
                UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: IslandLayout.cardCornerRadius,
                    bottomTrailingRadius: IslandLayout.cardCornerRadius,
                    topTrailingRadius: 0,
                    style: .continuous
                )
                .frame(width: size.width, height: size.height)
                .animation(.spring(response: 0.32, dampingFraction: 0.9), value: size)
                .frame(width: layout.width, height: layout.expandedHeight, alignment: .top)
            }
    }

    private var maskSize: CGSize {
        switch viewModel.appearance {
        case .hidden: layout.notchSize
        case .expanded: layout.expandedSize
        }
    }

    /// 与 IslandWindowController.activeScreen 同一兜底链（视图与窗口帧的 safeTop 必须同源）
    static func activeScreen() -> NSScreen? {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens.first
    }
}

// MARK: - 展开卡片：按源面板并排 + 页脚

struct ExpandedIslandView: View {
    @ObservedObject var store: UsageStore
    let openSettings: () -> Void
    let layout: IslandLayout

    var body: some View {
        VStack(spacing: IslandLayout.footerGap) {
            HStack(alignment: .top, spacing: IslandLayout.panelSpacing) {
                ForEach(ProviderRegistry.all) { descriptor in
                    ProviderPanel(
                        descriptor: descriptor,
                        state: store.state(descriptor.kind),
                        configured: store.isConfigured(descriptor.kind),
                        now: Date(),
                        height: layout.panelHeight,
                        width: layout.panelWidth,
                        openSettings: openSettings
                    )
                }
            }
            footer
        }
        .padding(EdgeInsets(
            top: layout.safeTop + IslandLayout.topGap,
            leading: IslandLayout.sidePadding,
            bottom: IslandLayout.bottomPadding,
            trailing: IslandLayout.sidePadding
        ))
    }

    /// 页脚：状态文案（TimelineView 每 15s 重算，「x前已刷新」不再滞留「刚刚」——修 P0-3）+ 刷新/设置
    private var footer: some View {
        HStack(spacing: 8) {
            TimelineView(.periodic(from: .now, by: 15)) { context in
                let kind = FooterStatus.kind(
                    states: store.states,
                    registry: ProviderRegistry.all.map(\.kind),
                    lastFetchedAt: store.lastFetchedAt
                )
                Text(FooterStatus.text(kind, demo: store.isDemoActive, now: context.date))
                    .font(.system(size: 9.5))
                    .foregroundStyle(FooterStatus.isError(kind)
                        ? IslandTheme.levelColor(0)
                        : .white.opacity(0.55))   // AC2：辅助文字 ≥55% 白
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            Spacer(minLength: 0)
            QuotaIconButton(systemName: "arrow.clockwise", spinning: store.isFetchingAny) {
                NSLog("[island][tap] ⟳ 刷新按钮")
                Task { await store.refreshAll() }
            }
            QuotaIconButton(systemName: "gearshape") {
                NSLog("[island][tap] ⚙ 设置按钮（卡片页脚）")
                openSettings()
            }
        }
        .frame(height: IslandLayout.footerHeight)
    }
}

// MARK: - 单源面板：面板头 + 主环 + 环下明细行

struct ProviderPanel: View {
    let descriptor: ProviderDescriptor
    let state: SourceState
    let configured: Bool
    let now: Date
    let height: CGFloat
    let width: CGFloat
    let openSettings: () -> Void

    private var rows: [QuotaRow] { state.snapshot?.displayRows ?? [] }
    private var primary: QuotaRow? { rows.first }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            content
        }
        .padding(.horizontal, IslandLayout.panelHPadding)
        .padding(.vertical, IslandLayout.panelVPadding)
        .frame(width: width, height: height, alignment: .top)
        .background(
            RoundedRectangle(cornerRadius: IslandLayout.panelCornerRadius, style: .continuous)
                .fill(Color.white.opacity(0.04))
        )
        .overlay(
            RoundedRectangle(cornerRadius: IslandLayout.panelCornerRadius, style: .continuous)
                .stroke(Color.white.opacity(0.05), lineWidth: 0.5)
        )
    }

    /// 面板头：源名 + 主档重置（失败时右侧改示「刷新失败」红字，错误全文见页脚）
    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(descriptor.title)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(.white.opacity(0.92))
                .lineLimit(1)
            Spacer(minLength: 4)
            if state.lastError != nil {
                Text("刷新失败")
                    .font(.system(size: 9.5))
                    .foregroundStyle(IslandTheme.levelColor(0))
            } else if let reset = primary.flatMap({ ResetFormatter.rowReset($0, now: now) }) {
                Text("\(reset)重置")
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.55))
                    .lineLimit(1)
                    .contentTransition(.numericText())
                    .animation(.snappy(duration: 0.25), value: reset)
            }
        }
        .frame(height: IslandLayout.headerHeight)
    }

    @ViewBuilder
    private var content: some View {
        if !configured && rows.isEmpty {
            unconfiguredHint
        } else {
            // alignment .center：主环在面板内水平居中（02 线框 W2 画法；修审计 P2「环左贴右侧留白 65pt」）
            VStack(alignment: .center, spacing: 0) {
                HeroRing(remaining: primary?.remainingPercent, caption: primary?.label ?? "")
                    .padding(.top, IslandLayout.sectionGap)
                if IslandLayout.detailLineCount(rows: rows, hasRing: true) > 0 {
                    detailLines
                        .padding(.top, IslandLayout.sectionGap)
                }
            }
            .frame(maxWidth: .infinity, alignment: .top)
        }
    }

    @ViewBuilder
    private var detailLines: some View {
        if rows.count > 1 {
            VStack(spacing: IslandLayout.detailRowSpacing) {
                ForEach(Array(rows.dropFirst())) { row in
                    DetailRow(row: row, now: now)
                }
            }
        } else if let primary, primary.usedTokens != nil, primary.limitTokens != nil {
            // 单档源：绝对量明细行（无副档时的唯一环下行）——与主环同轴居中 + 数字过渡（P0-B，DESIGN §5.9）
            let quantity = "\(ResetFormatter.billion(primary.usedTokens!)) / \(ResetFormatter.billion(primary.limitTokens!))"
            Text(quantity)
                .font(.system(size: 9.5))
                .foregroundStyle(.white.opacity(0.55))
                .contentTransition(.numericText())
                .animation(.snappy(duration: 0.25), value: quantity)
                .frame(maxWidth: .infinity, alignment: .center)
                .frame(height: IslandLayout.detailRowHeight)
        }
    }

    /// 未配置：图形（空环虚线，主环的未配置占位）+ 文字 + 行动按钮（DESIGN.md §5.8 空态基线）
    private var unconfiguredHint: some View {
        HStack(spacing: 8) {
            Circle()
                .strokeBorder(style: StrokeStyle(lineWidth: 3, dash: [3, 3]))
                .foregroundStyle(.white.opacity(0.18))
                .frame(width: 26, height: 26)
            VStack(alignment: .leading, spacing: 6) {
                Text(descriptor.unconfiguredText)
                    .font(.system(size: 10))
                    .foregroundStyle(.white.opacity(0.55))
                    .lineLimit(1)
                Button("去设置") {
                    NSLog("[island][tap] 「去设置」按钮（空态）")
                    openSettings()
                }
                    .font(.system(size: 10, weight: .medium))
                    .buttonStyle(.plain)
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(minHeight: 24)   // 命中区 ≥24pt（审计 P1-E：原 50×21pt 连本应用自定 24 都不达）
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color.white.opacity(0.08))
                    )
            }
        }
        // 整体居中：与数据态主环同轴（修审计 P1-C 空态↔数据态锚点跳变）
        .frame(maxWidth: .infinity, alignment: .center)
        .frame(height: IslandLayout.hintHeight)
        .padding(.top, IslandLayout.sectionGap)
    }
}

// MARK: - 主环：填充=剩余量、颜色=健康度，环心=剩余% + 档名（单环主锚）

struct HeroRing: View {
    let remaining: Double?
    let caption: String
    var diameter: CGFloat = IslandLayout.ringDiameter
    var ringWidth: CGFloat = IslandLayout.ringWidth

    private var fill: Double { min(1, max(0, (remaining ?? 0) / 100)) }
    private var color: Color { IslandTheme.levelColor(remaining) }
    /// stroke 以路径为中心向两侧各溢 ringWidth/2——路径取「外径−环宽」，墨迹外缘才等于 diameter（修审计 P1-C 外溢 4pt）
    private var pathDiameter: CGFloat { diameter - ringWidth }
    /// 无数据：整圈底轨用 health/unknown 白 25%（与色点/菜单同值，修审计 P1-D「spec 承诺的 unknown 环色永不出现」）；
    /// 有数据：白 8% 底轨 + 健康色弧。纯函数，可单测。
    static func trackOpacity(remaining: Double?) -> Double { remaining == nil ? 0.25 : 0.08 }

    private var trackOpacity: Double { Self.trackOpacity(remaining: remaining) }
    /// round cap 两端共伸出 ringWidth（切向），从 trim 扣除后视觉弧长 = fill 比例
    /// （否则 ≥96% 时帽交叠盖死缝隙，99% 与 100% 无视觉差——修审计 P2）。纯函数，可单测。
    static func arcTrimEnd(fill: Double, ringWidth: CGFloat, pathDiameter: CGFloat) -> Double {
        guard pathDiameter > 0 else { return 0.005 }
        let cap = Double(ringWidth / (CGFloat.pi * pathDiameter))
        return max(fill - cap, 0.005)
    }

    private var trimEnd: Double {
        Self.arcTrimEnd(fill: fill, ringWidth: ringWidth, pathDiameter: pathDiameter)
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(trackOpacity), lineWidth: ringWidth)
                .frame(width: pathDiameter, height: pathDiameter)
            if remaining != nil {
                // 实色弧：一环一色一义（健康度），渐变/辉光会让单环读出「两种状态」（Leo 反馈 2026-09-26）。
                // 有数据（含 0%）始终绘弧——0% 保最小红弧，不再与「无数据」同渲染（修审计 P1-D）
                Circle()
                    .trim(from: 0, to: trimEnd)
                    .stroke(color, style: StrokeStyle(lineWidth: ringWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: pathDiameter, height: pathDiameter)
                    .animation(.easeOut(duration: 0.6), value: fill)
            }
            VStack(spacing: 1) {
                Text(remaining.map { "\(Int($0))%" } ?? "--%")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    // 占位「--%」与明细行同为 55% 白（修审计 P2：环心 100% / 明细 40% 两档不一致且破 AC2）
                    .foregroundStyle(remaining == nil ? Color.white.opacity(0.55) : Color.white)
                    .contentTransition(.numericText())
                    .animation(.snappy(duration: 0.3), value: remaining)
                if !caption.isEmpty {
                    Text(caption)
                        .font(.system(size: 9.5, weight: .medium))
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(1)
                }
            }
        }
        .frame(width: diameter, height: diameter)
    }
}

// MARK: - 环下明细行：色点 + 档名 …… 重置 [绝对量] · 百分比

struct DetailRow: View {
    let row: QuotaRow
    let now: Date

    /// 辅助文本：重置优先，无重置的档展示绝对量（如 MiMo 的 5.6B / 456B）
    private var auxText: String? {
        if let reset = ResetFormatter.rowReset(row, now: now) { return reset }
        if let used = row.usedTokens, let limit = row.limitTokens, limit >= 1_000_000_000 {
            return "\(ResetFormatter.billion(used)) / \(ResetFormatter.billion(limit))"
        }
        return nil
    }

    var body: some View {
        // firstTextBaseline：12pt 百分比与 9.5pt 辅助文共基线（修审计 P2「三种字号基线不齐，差 1.5pt」）
        HStack(alignment: .firstTextBaseline, spacing: 5) {
            Circle()
                .fill(IslandTheme.levelColor(row.remainingPercent))
                .frame(width: 6, height: 6)
                // 色点无基线：让点底 ≈ 文本基线（点心落在 10pt 字面光学中心）
                .alignmentGuide(.firstTextBaseline) { d in d[VerticalAlignment.center] + 3 }
            Text(row.label)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.white.opacity(0.88))
                .lineLimit(1)
            Spacer(minLength: 2)
            if let aux = auxText {
                Text(aux)
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.55))
                    .lineLimit(1)
                    .contentTransition(.numericText())   // P0-B：绝对量/重置数字跳变（DESIGN §5.9）
                    .animation(.snappy(duration: 0.25), value: aux)
            }
            if row.remainingPercent != nil, auxText != nil {
                Text("·")
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.55))   // 30% → 55%：对比 2.6:1 < §2.2 3:1，且与 AC2「≥55% 白」自洽（审计 P2）
            }
            percentText
        }
        .frame(height: IslandLayout.detailRowHeight)
    }

    /// 百分比：向下取整对齐控制台口径（99.88% 显示 99%），数字变化滚动过渡（DESIGN §5.9）。
    /// 占位「--%」= 55% 白，与环心占位同值（修审计 P2：原 40% 破 AC2、且与环心 100% 两档不一致）
    private var percentText: some View {
        Group {
            if let remaining = row.remainingPercent {
                Text("\(Int(remaining))%")
                    .foregroundStyle(.white)
            } else {
                Text("--%")
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
        .font(.system(size: 12, weight: .bold, design: .rounded))
        .contentTransition(.numericText())
        .animation(.snappy(duration: 0.25), value: row.remainingPercent)
    }
}

// MARK: - 页脚图标按钮：24pt 命中区 + hover 反馈（DESIGN §2.5/§3）

struct QuotaIconButton: View {
    let systemName: String
    var spinning: Bool = false
    let action: () -> Void

    @State private var spin = false
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(.white.opacity(hovering ? 0.95 : 0.6))
                .rotationEffect(.degrees(spin ? 360 : 0))
                .frame(width: IslandLayout.footerHeight, height: IslandLayout.footerHeight)
                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(Color.white.opacity(hovering ? 0.10 : 0))
                )
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .onAppear { setSpinning(spinning) }
        .onChange(of: spinning) { _, now in setSpinning(now) }
    }

    /// 旋转启停（it-003 审计三轮修复）：停不下来的根因是 **离散写入无法打断 in-flight repeatForever**——
    /// 旧写法 `.animation(nil, value:)` 如此，实测 `withTransaction(disablesAnimations)` 也如此
    /// （模型 spin=false 后模板匹配仍测到 67.5°/255°/105°/300° 持续变化，对照实验匹配器 SAD=0 可信）。
    /// 唯一可靠打断 = **有限动画覆盖同 keypath 的无限动画**；0.01s 视觉即「原地归零不倒转」
    /// （规范 05 动效表），验证停转后角度恒 0°。
    private func setSpinning(_ on: Bool) {
        NSLog("[island][spin] setSpinning(\(on)) 旧值=\(spin)")
        if on {
            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) { spin = true }
        } else {
            withAnimation(.linear(duration: 0.01)) { spin = false }
        }
    }
}
