import SwiftUI
import AppKit

/// 灵岛根视图：卡片常驻层级内，可见区域由「随显隐状态变化的圆角遮罩」驱动——
/// 隐藏 = 刘海挖槽尺寸（黑区融合不可见）；展开 = 从刘海向下生长到全尺寸。
/// 无视图插入/移除，动画从第一帧就在刘海上，零闪现。
struct IslandRootView: View {
    @ObservedObject var store: UsageStore
    @ObservedObject var viewModel: IslandViewModel
    let openSettings: () -> Void

    var body: some View {
        card
            .frame(width: 352, height: expandedHeight, alignment: .top)
            .contentShape(Rectangle())
            .onTapGesture { viewModel.requestTogglePin() }
            .animation(.spring(response: 0.32, dampingFraction: 0.9), value: viewModel.appearance)
            .animation(.easeOut(duration: 0.45), value: store.allRows)
    }

    /// 卡片可见区域 = 动画化圆角遮罩：hidden=刘海挖槽尺寸（黑区融合），expanded=全尺寸
    private var card: some View {
        ExpandedIslandView(store: store, openSettings: openSettings)
            .frame(width: 352, height: expandedHeight, alignment: .top)
            .background(Color.black)
            .mask {
                UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: 16,
                    bottomTrailingRadius: 16,
                    topTrailingRadius: 0,
                    style: .continuous
                )
                .frame(width: maskSize.width, height: maskSize.height)
                .frame(width: 352, height: expandedHeight, alignment: .top)
            }
    }

    private var maskSize: CGSize {
        switch viewModel.appearance {
        case .hidden:
            notchTriggerSize
        case .expanded:
            CGSize(width: 352, height: expandedHeight)
        }
    }

    /// 隐藏态遮罩 = 刘海挖槽矩形
    private var notchTriggerSize: CGSize {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        return CGSize(width: 180, height: max(screen?.safeAreaInsets.top ?? 24, 24))
    }

    /// 与 IslandWindowController.expandedSize 保持同一公式：
    /// topInset + 面板(定高) + 页脚间距
    private var expandedHeight: CGFloat {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        let safeTop = max(screen?.safeAreaInsets.top ?? 24, 24)
        return safeTop + 6 + IslandRootView.panelHeight + 10 + 14 + 14
    }

    static let panelHeight: CGFloat = 132
}

// MARK: - 展开卡片：并排双源环形面板

struct ExpandedIslandView: View {
    @ObservedObject var store: UsageStore
    let openSettings: () -> Void

    /// 内容顶边避开刘海挖槽，留边距
    private var topInset: CGFloat {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        return max(screen?.safeAreaInsets.top ?? 24, 24) + 6
    }

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                ProviderPanel(kind: .glm, rows: providerRows(.glm), now: Date()) {
                    openSettings()
                }
                ProviderPanel(kind: .mimo, rows: providerRows(.mimo), now: Date()) {
                    openSettings()
                }
            }
            HStack(spacing: 8) {
                Text(statusText)
                    .font(.system(size: 9))
                    .foregroundStyle(.white.opacity(0.45))
                Spacer()
                QuotaIconButton(systemName: "arrow.clockwise", spinning: store.status == .loading) {
                    Task { await store.refreshAll() }
                }
                QuotaIconButton(systemName: "gearshape") {
                    openSettings()
                }
            }
        }
        .padding(EdgeInsets(top: topInset, leading: 16, bottom: 14, trailing: 16))
    }

    private func providerRows(_ kind: ProviderKind) -> [QuotaRow] {
        store.snapshots[kind]?.displayRows ?? []
    }

    private var statusText: String {
        let prefix = store.isDemoActive ? "演示 · " : ""
        switch store.status {
        case .idle:
            return prefix + "待刷新"
        case .loading:
            return prefix + "刷新中…"
        case .loaded:
            if let fetchedAt = store.lastFetchedAt {
                return prefix + ResetFormatter.relativeAge(fetchedAt, now: Date()) + "已刷新"
            }
            return prefix + "已加载"
        case .failed(let message):
            return prefix + "刷新失败：\(message)"
        }
    }
}

// MARK: - 单源面板：环 + 图例

struct ProviderPanel: View {
    let kind: ProviderKind
    let rows: [QuotaRow]
    let now: Date
    let openSettings: () -> Void

    /// 统一间距节奏：10 边距 / 环 / 10 / 图例（行高 18、行距 6），内容垂直居中
    var body: some View {
        Group {
            if rows.count <= 1 {
                singleContent
            } else {
                multiContent
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: IslandRootView.panelHeight, alignment: .center)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.04))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.05), lineWidth: 0.5)
        )
    }

    /// 单档：大环居中 + 单行图例
    private var singleContent: some View {
        VStack(spacing: 12) {
            ActivityRingsView(rows: rows, centerTitle: kind.title, clusterSize: 68, ringWidth: 8)
            if let row = rows.first {
                RingStatRow(row: row, now: now)
            } else {
                unconfiguredHint
            }
        }
    }

    /// 多档：双环 + 图例
    private var multiContent: some View {
        VStack(spacing: 12) {
            ActivityRingsView(rows: rows, centerTitle: "", clusterSize: 60, ringWidth: 7)
            VStack(spacing: 6) {
                ForEach(rows) { row in
                    RingStatRow(row: row, now: now)
                }
            }
        }
    }

    private var unconfiguredHint: some View {
        VStack(spacing: 4) {
            Text(kind == .glm ? "未配置 API Key" : "未配置 Cookie")
                .font(.system(size: 9.5))
                .foregroundStyle(.white.opacity(0.5))
            Button("去设置") { openSettings() }
                .font(.system(size: 9))
                .buttonStyle(.plain)
                .foregroundStyle(.white.opacity(0.7))
        }
    }
}

// MARK: - 同心环（每个环=一档，填充=剩余量，颜色=健康度）

struct ActivityRingsView: View {
    let rows: [QuotaRow]
    var centerTitle: String = ""
    var clusterSize: CGFloat = 72
    var ringWidth: CGFloat = 8

    private var gap: CGFloat { 4 }

    var body: some View {
        ZStack {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                let diameter = clusterSize - ringWidth
                    - CGFloat(index) * 2 * (ringWidth + gap)
                let fill = min(1, max(0, (row.remainingPercent ?? 0) / 100))
                let color = IslandTheme.levelColor(row.remainingPercent)
                Circle()
                    .stroke(Color.white.opacity(0.08), lineWidth: ringWidth)
                    .frame(width: diameter, height: diameter)
                Circle()
                    .trim(from: 0, to: fill)
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
            }
            if rows.isEmpty {
                Circle()
                    .stroke(Color.white.opacity(0.08), lineWidth: ringWidth)
                    .frame(width: clusterSize - ringWidth, height: clusterSize - ringWidth)
            }
            if !centerTitle.isEmpty {
                Text(centerTitle)
                    .font(.system(size: 8.5, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
        .frame(width: clusterSize, height: clusterSize)
    }
}

/// 环对应的图例行：色点 + 标签/重置 + 百分比（色点与所在环同色）
struct RingStatRow: View {
    let row: QuotaRow
    let now: Date

    /// 5 小时档的重置时刻只显示 HH:mm（跨天也不带日期，保证单行放下）
    private var resetText: String? {
        guard let reset = row.resetDate else { return nil }
        return row.kind == .fiveHour
            ? ResetFormatter.clock(reset)
            : ResetFormatter.shortReset(reset, now: now)
    }

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(IslandTheme.levelColor(row.remainingPercent))
                .frame(width: 6, height: 6)
            Text(row.label)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.white.opacity(0.9))
            Spacer(minLength: 0)
            if let reset = resetText {
                Text(reset)
                    .font(.system(size: 8))
                    .foregroundStyle(.white.opacity(0.42))
            }
            if let used = row.usedTokens, let limit = row.limitTokens, limit >= 1_000_000_000 {
                Text("\(ResetFormatter.billion(used))/\(ResetFormatter.billion(limit))")
                    .font(.system(size: 8))
                    .foregroundStyle(.white.opacity(0.42))
                    .padding(.trailing, resetText == nil ? 2 : 0)
            }
            if row.remainingPercent != nil, resetText != nil {
                Text("·")
                    .font(.system(size: 8))
                    .foregroundStyle(.white.opacity(0.25))
            }
            if let remaining = row.remainingPercent {
                // 向下取整对齐控制台口径（99.88% 显示 99%，不进位成 100%）
                Text("\(Int(remaining))%")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            } else {
                Text("--%")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.4))
            }
        }
    }
}

struct QuotaIconButton: View {
    let systemName: String
    var spinning: Bool = false
    let action: () -> Void

    @State private var spin = false

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(.white.opacity(0.55))
                .rotationEffect(.degrees(spin ? 360 : 0))
                .animation(
                    spinning
                        ? .linear(duration: 1.0).repeatForever(autoreverses: false)
                        : .default,
                    value: spin
                )
        }
        .buttonStyle(.plain)
        .onAppear { spin = spinning }
        .onChange(of: spinning) { _, newValue in
            spin = newValue
        }
    }
}
