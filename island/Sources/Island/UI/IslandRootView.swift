import SwiftUI
import AppKit

/// 灵岛根视图：默认完全隐藏（窗口即刘海挖槽区的隐形触发区），
/// hover/点击后从刘海向下延伸出内容卡片（并排双源环形面板）
struct IslandRootView: View {
    @ObservedObject var store: UsageStore
    @ObservedObject var viewModel: IslandViewModel
    let openSettings: () -> Void

    var body: some View {
        Group {
            switch viewModel.appearance {
            case .hidden:
                Color.clear
            case .expanded:
                card
            }
        }
        .frame(width: contentSize.width, height: contentSize.height, alignment: .top)
        .contentShape(Rectangle())
        .onTapGesture { viewModel.requestTogglePin() }
        .animation(.spring(response: 0.32, dampingFraction: 0.9), value: viewModel.reveal)
        .animation(.easeOut(duration: 0.45), value: store.allRows)
    }

    /// 卡片以完整尺寸布局，可见区域由动画化圆角遮罩驱动：从刘海尺寸向下长到全高
    private var card: some View {
        ExpandedIslandView(store: store, openSettings: openSettings)
            .frame(width: 352, height: expandedHeight, alignment: .top)
            .background {
                UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: 16,
                    bottomTrailingRadius: 16,
                    topTrailingRadius: 0,
                    style: .continuous
                )
                .fill(Color.black)
            }
            .frame(height: viewModel.reveal ? expandedHeight : notchTriggerSize.height, alignment: .top)
            .clipped()
            .transition(.opacity)
    }

    private var contentSize: CGSize {
        switch viewModel.appearance {
        case .hidden:
            notchTriggerSize
        case .expanded:
            CGSize(width: 352, height: expandedHeight)
        }
    }

    /// 隐形触发区 = 刘海挖槽矩形
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

    static let panelHeight: CGFloat = 138
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
                    .font(.system(size: 9.5))
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

    var body: some View {
        VStack(spacing: 10) {
            ActivityRingsView(rows: rows, centerTitle: kind.title)
                .frame(width: 64, height: 64)
                .padding(.top, 2)
            if rows.isEmpty {
                VStack(spacing: 4) {
                    Text(kind == .glm ? "未配置 API Key" : "未配置 Cookie")
                        .font(.system(size: 9.5))
                        .foregroundStyle(.white.opacity(0.5))
                    Button("去设置") { openSettings() }
                        .font(.system(size: 9))
                        .buttonStyle(.plain)
                        .foregroundStyle(.white.opacity(0.7))
                }
                .padding(.bottom, 2)
            } else {
                VStack(spacing: 7) {
                    ForEach(rows) { row in
                        RingStatRow(row: row, now: now)
                    }
                }
            }
        }
        .padding(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 10))
        .frame(maxWidth: .infinity)
        .frame(height: IslandRootView.panelHeight)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.045))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.06), lineWidth: 0.5)
        )
    }
}

// MARK: - 同心环（每个环=一档，填充=剩余量，颜色=健康度）

struct ActivityRingsView: View {
    let rows: [QuotaRow]
    var centerTitle: String = ""

    private var ringWidth: CGFloat { rows.count >= 2 ? 8 : 9 }
    private var gap: CGFloat { 3 }
    private var clusterSize: CGFloat { 64 }

    var body: some View {
        ZStack {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                let diameter = clusterSize - ringWidth
                    - CGFloat(index) * 2 * (ringWidth + gap)
                let fill = min(1, max(0.008, (row.remainingPercent ?? 0) / 100))
                let color = IslandTheme.levelColor(row.remainingPercent)
                Circle()
                    .stroke(Color.white.opacity(0.10), lineWidth: ringWidth)
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
                    .shadow(color: color.opacity(0.3), radius: 2.5)
                    .animation(.easeOut(duration: 0.6), value: fill)
            }
            if rows.isEmpty {
                Circle()
                    .stroke(Color.white.opacity(0.10), lineWidth: ringWidth)
                    .frame(width: clusterSize - ringWidth, height: clusterSize - ringWidth)
            }
            if !centerTitle.isEmpty {
                Text(centerTitle)
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.6))
            }
        }
        .frame(width: clusterSize, height: clusterSize)
    }
}

/// 环对应的图例行：色点 + 标签/重置 + 百分比（色点与所在环同色）
struct RingStatRow: View {
    let row: QuotaRow
    let now: Date

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(IslandTheme.levelColor(row.remainingPercent))
                .frame(width: 5, height: 5)
            VStack(alignment: .leading, spacing: 1) {
                Text(row.label)
                    .font(.system(size: 10.5, weight: .medium))
                    .foregroundStyle(.white.opacity(0.9))
                if let reset = row.resetDate {
                    Text(ResetFormatter.shortReset(reset, now: now) + " 重置")
                        .font(.system(size: 8.5))
                        .foregroundStyle(.white.opacity(0.4))
                }
            }
            Spacer(minLength: 0)
            if let remaining = row.remainingPercent {
                // 向下取整对齐控制台口径（99.88% 显示 99%，不进位成 100%）
                Text("\(Int(remaining))%")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            } else {
                Text("--%")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
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
