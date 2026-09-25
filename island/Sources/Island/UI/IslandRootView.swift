import SwiftUI
import AppKit

/// 灵岛根视图：默认完全隐藏（窗口即刘海挖槽区的隐形触发区），
/// hover/点击后从刘海向下延伸出内容卡片（内容源 chips + Apple 健康式同心环 + 明细）
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
        .animation(.spring(response: 0.32, dampingFraction: 0.9), value: store.activeKind)
        .animation(.easeOut(duration: 0.45), value: store.displaySnapshot)
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
    /// topInset + chips + 圆环簇 + 页脚（圆环簇定高 80）
    private var expandedHeight: CGFloat {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        let safeTop = max(screen?.safeAreaInsets.top ?? 24, 24)
        return safeTop + 6 + 18 + 8 + 80 + 10 + 14 + 14
    }
}

// MARK: - 展开卡片：内容源 chips + 同心环 + 明细（颜色=档位身份色，环填充=剩余量）

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
            providerChips
            if let rows = displayRows, !rows.isEmpty {
                HStack(spacing: 16) {
                    ActivityRingsView(rows: rows, centerTitle: store.activeKind.title)
                        .frame(width: 80, height: 80)
                    VStack(spacing: 12) {
                        ForEach(rows) { row in
                            RingStatRow(row: row, now: Date())
                        }
                    }
                }
            } else {
                emptyState
            }
            HStack(spacing: 8) {
                Text(statusText)
                    .font(.system(size: 9.5))
                    .foregroundStyle(.white.opacity(0.45))
                Spacer()
                QuotaIconButton(systemName: "arrow.clockwise", spinning: store.status == .loading) {
                    Task { await store.refreshNow() }
                }
                QuotaIconButton(systemName: "gearshape") {
                    openSettings()
                }
            }
        }
        .padding(EdgeInsets(top: topInset, leading: 16, bottom: 14, trailing: 16))
    }

    private var providerChips: some View {
        HStack(spacing: 6) {
            ForEach(ProviderKind.allCases) { kind in
                let active = store.activeKind == kind
                Button {
                    store.switchProvider(kind)
                } label: {
                    Text(kind.title)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(active ? Color.white.opacity(0.95) : Color.white.opacity(0.45))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(
                            Capsule().fill(Color.white.opacity(active ? 0.16 : 0.05))
                        )
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
    }

    /// 最多展示 3 个环
    private var displayRows: [QuotaRow]? {
        let rows = store.displaySnapshot?.displayRows
        guard rows?.isEmpty == false else { return nil }
        return Array(rows!.prefix(3))
    }

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 8) {
            if store.status == .loading {
                ProgressView()
                    .controlSize(.small)
            }
            if !store.isConfigured(store.activeKind) {
                Text(store.activeKind == .glm ? "尚未配置 GLM API Key" : "尚未配置 MiMo Cookie")
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
            if let snapshot = store.displaySnapshot {
                return prefix + ResetFormatter.relativeAge(snapshot.fetchedAt, now: Date()) + "已刷新"
            }
            return prefix + "已加载"
        case .failed(let message):
            return prefix + "刷新失败：\(message)"
        }
    }
}

// MARK: - Apple 健康式同心环（外环=第一档，填充比例=剩余量）

struct ActivityRingsView: View {
    let rows: [QuotaRow]
    var centerTitle: String = ""

    private var ringWidth: CGFloat { rows.count >= 3 ? 8 : 10 }
    private var gap: CGFloat { 4 }
    private var clusterSize: CGFloat { 80 }

    var body: some View {
        ZStack {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                let diameter = clusterSize - ringWidth
                    - CGFloat(index) * 2 * (ringWidth + gap)
                let fill = min(1, max(0.005, (row.remainingPercent ?? 0) / 100))
                let color = IslandTheme.identityColor(row.kind)
                Circle()
                    .stroke(Color.white.opacity(0.12), lineWidth: ringWidth)
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
                    .animation(.easeOut(duration: 0.6), value: fill)
            }
            if !centerTitle.isEmpty {
                Text(centerTitle)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
        .frame(width: clusterSize, height: clusterSize)
    }
}

/// 环对应的图例行：色点 + 标签 + 重置时间 · 百分比
struct RingStatRow: View {
    let row: QuotaRow
    let now: Date

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(IslandTheme.identityColor(row.kind))
                .frame(width: 6, height: 6)
            Text(row.label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.white.opacity(0.85))
            Spacer(minLength: 0)
            if let reset = row.resetDate {
                Text(ResetFormatter.shortReset(reset, now: now))
                    .font(.system(size: 10))
                    .foregroundStyle(.white.opacity(0.5))
            }
            if row.resetDate != nil, row.remainingPercent != nil {
                Text("·")
                    .font(.system(size: 10))
                    .foregroundStyle(.white.opacity(0.3))
            }
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
