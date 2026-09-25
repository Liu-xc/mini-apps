import SwiftUI
import AppKit

/// 灵岛根视图：默认完全隐藏（窗口即刘海挖槽区的隐形触发区），
/// hover/点击后从刘海向下延伸出内容卡片（Apple 健康式三环 + 全部内容源明细）
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
    /// topInset + 圆环簇(80) 与 图例块 取高者 + 页脚
    private var expandedHeight: CGFloat {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        let safeTop = max(screen?.safeAreaInsets.top ?? 24, 24)
        let n = CGFloat(max(1, store.allRows.count))
        let legendBlock = n * 30 + max(0, n - 1) * 10
        return safeTop + 6 + max(80, legendBlock) + 10 + 14 + 14
    }
}

// MARK: - 展开卡片：Apple 健康式三环 + 全部内容源明细

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
            if store.allRows.isEmpty {
                emptyState
            } else {
                HStack(alignment: .center, spacing: 16) {
                    ActivityRingsView(rows: store.allRows)
                        .frame(width: 80, height: 80)
                    VStack(spacing: 10) {
                        ForEach(store.allRows) { row in
                            RingStatRow(row: row, now: Date())
                        }
                    }
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

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 8) {
            if store.status == .loading {
                ProgressView()
                    .controlSize(.small)
            }
            if store.credentialKinds.isEmpty {
                Text("尚未配置内容源凭证")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.8))
                Button("去设置") { openSettings() }
                    .controlSize(.small)
                    .buttonStyle(.borderedProminent)
            } else if case .failed(let message) = store.status {
                Text("获取失败：\(message)")
                    .font(.system(size: 10))
                    .foregroundStyle(Color(red: 0xFB / 255, green: 0x92 / 255, blue: 0x3C / 255))
                    .multilineTextAlignment(.center)
            } else {
                Text("正在获取套餐用量…")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.8))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
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

// MARK: - 颜色语义：颜色=健康度（剩余 ≥50% 绿 / 20–50% 橙 / <20% 红）

enum IslandTheme {
    static func levelColor(_ remaining: Double?) -> Color {
        guard let remaining else { return .white.opacity(0.25) }
        if remaining >= 50 {
            return Color(red: 48 / 255, green: 209 / 255, blue: 88 / 255)      // #30D158
        }
        if remaining >= 20 {
            return Color(red: 255 / 255, green: 159 / 255, blue: 10 / 255)     // #FF9F0A
        }
        return Color(red: 255 / 255, green: 69 / 255, blue: 58 / 255)          // #FF453A
    }
}

// MARK: - Apple 健康式同心环（外环=5 小时，中环=每周，内环=MiMo；填充=剩余量）

struct ActivityRingsView: View {
    let rows: [QuotaRow]

    private var ringWidth: CGFloat { rows.count >= 3 ? 8 : 10 }
    private var gap: CGFloat { 4 }
    private var clusterSize: CGFloat { 80 }

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
                    .shadow(color: color.opacity(0.35), radius: 3)
                    .animation(.easeOut(duration: 0.6), value: fill)
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
        HStack(spacing: 8) {
            Circle()
                .fill(IslandTheme.levelColor(row.remainingPercent))
                .frame(width: 7, height: 7)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white.opacity(0.9))
                if let reset = row.resetDate {
                    Text(ResetFormatter.shortReset(reset, now: now) + " 重置")
                        .font(.system(size: 9))
                        .foregroundStyle(.white.opacity(0.45))
                }
            }
            Spacer(minLength: 0)
            if let remaining = row.remainingPercent {
                // 向下取整对齐控制台口径（99.88% 显示 99%，不进位成 100%）
                Text("\(Int(remaining))%")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            } else {
                Text("--%")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
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
