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
            .onTapGesture { viewModel.requestTogglePin() }
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
                Task { await store.refreshAll() }
            }
            QuotaIconButton(systemName: "gearshape") {
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
        HStack(spacing: 4) {
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
            }
        }
        .frame(height: IslandLayout.headerHeight)
    }

    @ViewBuilder
    private var content: some View {
        if !configured && rows.isEmpty {
            unconfiguredHint
        } else {
            VStack(alignment: .leading, spacing: 0) {
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
            // 单档源：绝对量明细行（无副档时的唯一环下行）
            HStack(spacing: 5) {
                Text("\(ResetFormatter.billion(primary.usedTokens!)) / \(ResetFormatter.billion(primary.limitTokens!))")
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.55))
                Spacer(minLength: 0)
            }
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
                Button("去设置") { openSettings() }
                    .font(.system(size: 10, weight: .medium))
                    .buttonStyle(.plain)
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color.white.opacity(0.08))
                    )
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.08), lineWidth: ringWidth)
                .frame(width: diameter, height: diameter)
            Circle()
                .trim(from: 0, to: fill > 0 ? max(fill, 0.005) : 0)
                .stroke(
                    AngularGradient(
                        colors: [color.opacity(0.55), color],
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(-90 + 360 * fill)
                    ),
                    style: StrokeStyle(lineWidth: ringWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .frame(width: diameter, height: diameter)
                .shadow(color: color.opacity(0.25), radius: 2)
                .animation(.easeOut(duration: 0.6), value: fill)
            VStack(spacing: 1) {
                Text(remaining.map { "\(Int($0))%" } ?? "--%")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
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
        HStack(spacing: 5) {
            Circle()
                .fill(IslandTheme.levelColor(row.remainingPercent))
                .frame(width: 6, height: 6)
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
            }
            if row.remainingPercent != nil, auxText != nil {
                Text("·")
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.3))
            }
            percentText
        }
        .frame(height: IslandLayout.detailRowHeight)
    }

    /// 百分比：向下取整对齐控制台口径（99.88% 显示 99%），数字变化滚动过渡（DESIGN §5.9）
    private var percentText: some View {
        Group {
            if let remaining = row.remainingPercent {
                Text("\(Int(remaining))%")
            } else {
                Text("--%").foregroundStyle(.white.opacity(0.4))
            }
        }
        .font(.system(size: 12, weight: .bold, design: .rounded))
        .foregroundStyle(.white)
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
                .animation(
                    spinning ? .linear(duration: 1.0).repeatForever(autoreverses: false) : nil,
                    value: spin
                )
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .onAppear { spin = spinning }
        .onChange(of: spinning) { _, newValue in
            spin = newValue   // 停止时 animation 为 nil → 原地归零，不倒转
        }
    }
}
