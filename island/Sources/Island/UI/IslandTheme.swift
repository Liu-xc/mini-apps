import AppKit
import SwiftUI

/// 与控制台行同色系：蓝 / 绿 / 橙；警戒态覆盖为橙红、耗尽为红
enum IslandTheme {
    static func identityColor(_ kind: RowKind) -> Color {
        switch kind {
        case .fiveHour: Color(red: 0x3B / 255, green: 0x82 / 255, blue: 0xF6 / 255)
        case .weekly: Color(red: 0x22 / 255, green: 0xC5 / 255, blue: 0x5E / 255)
        case .zcodeMcp: Color(red: 0xF5 / 255, green: 0x9E / 255, blue: 0x0B / 255)
        case .other: Color(red: 0x94 / 255, green: 0xA3 / 255, blue: 0xB8 / 255)
        }
    }

    static func stateColor(_ kind: RowKind, remaining: Double?) -> Color {
        guard let remaining else { return .white.opacity(0.25) }
        switch ThresholdState(remainingPercent: remaining) {
        case .healthy: return identityColor(kind)
        case .warn: return Color(red: 0xFB / 255, green: 0x92 / 255, blue: 0x3C / 255)
        case .exhausted: return Color(red: 0xF8 / 255, green: 0x71 / 255, blue: 0x71 / 255)
        }
    }
}

/// 胶囊两态由窗口控制器驱动；点击「固定展开」的回调也由它转发
@MainActor
final class IslandViewModel: ObservableObject {
    enum Appearance {
        /// 默认：完全隐藏，窗口即刘海挖槽区的隐形 hover 触发区
        case hidden
        /// 从刘海向下展开的明细卡片
        case expanded
    }

    @Published var appearance: Appearance = .hidden
    /// 卡片揭示高度：false=只露出刘海高度（黑条与挖槽融为一体），true=向下展开到全高
    @Published var reveal = false
    var onTogglePin: (() -> Void)?

    func requestTogglePin() {
        onTogglePin?()
    }
}
