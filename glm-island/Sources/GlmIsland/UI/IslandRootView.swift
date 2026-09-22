import SwiftUI

/// 灵动岛根视图：紧凑胶囊 ↔ 展开面板，纯黑底与刘海融为一体
struct IslandRootView: View {
    @ObservedObject var store: UsageStore
    @ObservedObject var viewModel: IslandViewModel
    let openSettings: () -> Void

    var body: some View {
        Group {
            switch viewModel.appearance {
            case .compact:
                CompactIslandView(snapshot: store.snapshot)
                    .transition(.opacity)
            case .expanded:
                ExpandedIslandView(store: store, openSettings: openSettings)
                    .transition(.opacity)
            }
        }
        .frame(width: contentSize.width, height: contentSize.height, alignment: .top)
        .background {
            // 顶部两角直角（与刘海下沿无缝衔接），只圆下方两角
            UnevenRoundedRectangle(
                topLeadingRadius: 0,
                bottomLeadingRadius: viewModel.appearance == .compact ? 13 : 16,
                bottomTrailingRadius: viewModel.appearance == .compact ? 13 : 16,
                topTrailingRadius: 0,
                style: .continuous
            )
            .fill(Color.black)
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(Color.white.opacity(0.07))
                    .frame(height: 0.5)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { viewModel.requestTogglePin() }
        .animation(.spring(response: 0.36, dampingFraction: 0.85), value: viewModel.appearance)
        .animation(.easeOut(duration: 0.45), value: store.snapshot)
    }

    private var contentSize: CGSize {
        switch viewModel.appearance {
        case .compact:
            CGSize(width: 180, height: 36)
        case .expanded:
            CGSize(width: 352, height: expandedHeight)
        }
    }

    /// 两档 178；若接口吐出第三档（other）则加高容纳
    private var expandedHeight: CGFloat {
        let rowCount = max(2, store.snapshot?.displayRows.count ?? 2)
        return rowCount >= 3 ? 222 : 178
    }
}

// MARK: - 紧凑态：两行全信息（标签 + 迷你条 + 百分比 + 重置时间）

struct CompactIslandView: View {
    let snapshot: UsageSnapshot?

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(displayRows) { row in
                CompactQuotaRow(row: row, now: Date())
            }
        }
        .padding(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 10))
    }

    private var displayRows: [QuotaRow] {
        let rows = Array((snapshot?.displayRows ?? []).prefix(2))
        guard rows.isEmpty == false else {
            return [
                QuotaRow(id: "ph-0", kind: .fiveHour, label: "5 小时", remainingPercent: nil, resetDate: nil, percentInferred: false),
                QuotaRow(id: "ph-1", kind: .weekly, label: "每周", remainingPercent: nil, resetDate: nil, percentInferred: false),
            ]
        }
        return rows
    }
}

struct CompactQuotaRow: View {
    let row: QuotaRow
    let now: Date

    var body: some View {
        HStack(spacing: 5) {
            Text(row.label)
                .font(.system(size: 9.5, weight: .semibold))
                .foregroundStyle(color)
                .fixedSize()
            QuotaBarTrack(color: color, fill: (row.remainingPercent ?? 0) / 100, height: 4)
                .frame(width: 40)
            Text(percentText)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .fixedSize()
            if let reset = row.resetDate {
                Text("· " + ResetFormatter.shortReset(reset, now: now))
                    .font(.system(size: 9))
                    .foregroundStyle(.white.opacity(0.55))
                    .fixedSize()
            }
            Spacer(minLength: 0)
        }
    }

    private var color: Color {
        IslandTheme.stateColor(row.kind, remaining: row.remainingPercent)
    }

    private var percentText: String {
        row.remainingPercent.map { "\(Int($0.rounded()))%" } ?? "--%"
    }
}

// MARK: - 展开态：与控制台一致的三行明细

struct ExpandedIslandView: View {
    @ObservedObject var store: UsageStore
    let openSettings: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            header
            if let snapshot = store.snapshot, !snapshot.displayRows.isEmpty {
                VStack(spacing: 10) {
                    ForEach(snapshot.displayRows) { row in
                        QuotaRowView(row: row, now: Date())
                    }
                }
            } else {
                emptyState
            }
            footer
        }
        .padding(EdgeInsets(top: 12, leading: 16, bottom: 10, trailing: 16))
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text("剩余额度")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
            if store.isDemoActive {
                QuotaBadge(text: "演示数据", color: IslandTheme.identityColor(.fiveHour))
            }
            Spacer()
        }
    }

    private var footer: some View {
        HStack(spacing: 10) {
            Text(statusText)
                .font(.system(size: 9.5))
                .foregroundStyle(statusColor)
            Spacer()
            QuotaIconButton(systemName: "arrow.clockwise", spinning: store.status == .loading) {
                Task { await store.refreshNow() }
            }
            QuotaIconButton(systemName: "gearshape") {
                openSettings()
            }
        }
    }

    private var statusText: String {
        switch store.status {
        case .idle: ""
        case .loading: "刷新中…"
        case .loaded:
            if let snapshot = store.snapshot {
                ResetFormatter.relativeAge(snapshot.fetchedAt, now: Date()) + "已刷新"
            } else {
                "已加载"
            }
        case .failed(let message): "刷新失败：\(message)"
        }
    }

    private var statusColor: Color {
        if case .failed = store.status {
            return Color(red: 0xFB / 255, green: 0x92 / 255, blue: 0x3C / 255)
        }
        return .white.opacity(0.45)
    }

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 8) {
            if store.status == .loading {
                ProgressView()
                    .controlSize(.small)
            }
            if !store.hasCredential {
                Text("尚未配置 API Key")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.8))
                Button("去设置") { openSettings() }
                    .controlSize(.small)
                    .buttonStyle(.borderedProminent)
            } else if case .failed(let message) = store.status {
                Text("获取失败：\(message)")
                    .font(.system(size: 10))
                    .foregroundStyle(.red.opacity(0.9))
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
}

struct QuotaRowView: View {
    let row: QuotaRow
    let now: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 5) {
                Text(row.label)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(color)
                if row.threshold == .exhausted, let reset = row.resetDate {
                    Text("已用完 · \(ResetFormatter.shortReset(reset, now: now))重置")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundStyle(.red.opacity(0.9))
                }
                Spacer()
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                if let remaining = row.remainingPercent {
                    Text("\(Int(remaining.rounded()))%")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                } else {
                    Text("--%")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(.white.opacity(0.4))
                }
                if let reset = row.resetDate, row.threshold != .exhausted {
                    Text("· " + ResetFormatter.shortReset(reset, now: now))
                        .font(.system(size: 10))
                        .foregroundStyle(.white.opacity(0.55))
                }
                Spacer()
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

struct QuotaBadge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .medium))
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.16)))
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
                .foregroundStyle(.white.opacity(0.7))
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
