import SwiftUI
import AppKit

/// 灵动岛根视图：默认完全隐藏（窗口即刘海挖槽区的隐形触发区），
/// hover/点击后从刘海向下延伸出明细卡片（顶边钉死，只向下生长）
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
        .animation(.easeOut(duration: 0.45), value: store.snapshot)
    }

    /// 卡片以完整尺寸布局，但可见高度由 reveal 驱动：32（刘海高度）→ 全高，
    /// 顶边钉死、内容自上而下被揭示 = 「从刘海向下延伸」
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

    /// 与 IslandWindowController.expandedSize 保持同一公式
    private var expandedHeight: CGFloat {
        let screen = NSScreen.screens.first { $0.safeAreaInsets.top > 0 } ?? NSScreen.main
        let safeTop = max(screen?.safeAreaInsets.top ?? 24, 24)
        let n = CGFloat(max(2, store.snapshot?.displayRows.count ?? 2))
        let content: CGFloat = 16 + 10 + n * 24 + (n - 1) * 10 + 10 + 14
        return safeTop + 6 + content + 14
    }
}

// MARK: - 展开卡片：极简两行明细（颜色只保留在进度条上）

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
            HStack {
                Text("剩余额度")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))
                Spacer()
            }
            ForEach(store.snapshot?.displayRows ?? []) { row in
                SimpleQuotaRow(row: row, now: Date())
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

    private var statusText: String {
        let prefix = store.isDemoActive ? "演示 · " : ""
        switch store.status {
        case .idle:
            return prefix + "待刷新"
        case .loading:
            return prefix + "刷新中…"
        case .loaded:
            if let snapshot = store.snapshot {
                return prefix + ResetFormatter.relativeAge(snapshot.fetchedAt, now: Date()) + "已刷新"
            }
            return prefix + "已加载"
        case .failed(let message):
            return prefix + "刷新失败：\(message)"
        }
    }
}

/// 单档一行：标签 + 百分比 · 重置时间，下方进度条
struct SimpleQuotaRow: View {
    let row: QuotaRow
    let now: Date

    var body: some View {
        VStack(spacing: 3) {
            HStack(spacing: 6) {
                Text(row.label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                Spacer(minLength: 0)
                if let remaining = row.remainingPercent {
                    Text("\(Int(remaining.rounded()))%")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(row.threshold == .exhausted ? Color(red: 0xF8 / 255, green: 0x71 / 255, blue: 0x71 / 255) : .white)
                } else {
                    Text("--%")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(.white.opacity(0.4))
                }
                if let reset = row.resetDate {
                    Text("· " + ResetFormatter.shortReset(reset, now: now))
                        .font(.system(size: 10))
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            QuotaBarTrack(color: color, fill: (row.remainingPercent ?? 0) / 100)
        }
    }

    private var color: Color {
        IslandTheme.stateColor(row.kind, remaining: row.remainingPercent)
    }
}

struct QuotaBarTrack: View {
    let color: Color
    let fill: Double
    var height: CGFloat = 6

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(Color.white.opacity(0.13))
            GeometryReader { proxy in
                Capsule()
                    .fill(color)
                    .frame(width: max(3, proxy.size.width * min(1, max(0, fill))))
                    .animation(.easeOut(duration: 0.5), value: fill)
            }
        }
        .frame(height: height)
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
