import AppKit
import SwiftUI

/// 色彩语义（it-003 收编）：**颜色 = 健康度**（剩余 ≥50% 绿 / 20–50% 橙 / <20% 红 / 无数据 灰白）。
/// 阈值只在本类型一处定义；SwiftUI 与 AppKit（菜单栏图标）取同一套级别。
enum IslandTheme {
    enum Level: Equatable {
        case good, warn, bad, unknown
    }

    static func level(of remaining: Double?) -> Level {
        guard let remaining else { return .unknown }
        if remaining >= 50 { return .good }
        if remaining >= 20 { return .warn }
        return .bad
    }

    static func levelColor(_ remaining: Double?) -> Color {
        switch level(of: remaining) {
        case .good: Color(red: 48 / 255, green: 209 / 255, blue: 88 / 255)      // #30D158
        case .warn: Color(red: 255 / 255, green: 159 / 255, blue: 10 / 255)     // #FF9F0A
        case .bad:  Color(red: 255 / 255, green: 69 / 255, blue: 58 / 255)      // #FF453A
        case .unknown: .white.opacity(0.25)
        }
    }

    /// 菜单栏图标用等价 NSColor（同阈值同色值，不复制语义）
    static func levelNSColor(_ remaining: Double?) -> NSColor {
        switch level(of: remaining) {
        case .good: NSColor(srgbRed: 48 / 255, green: 209 / 255, blue: 88 / 255, alpha: 1)
        case .warn: NSColor(srgbRed: 255 / 255, green: 159 / 255, blue: 10 / 255, alpha: 1)
        case .bad:  NSColor(srgbRed: 255 / 255, green: 69 / 255, blue: 58 / 255, alpha: 1)
        case .unknown: NSColor(srgbRed: 1, green: 1, blue: 1, alpha: 0.3)
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
    var onTogglePin: (() -> Void)?

    func requestTogglePin() {
        onTogglePin?()
    }
}
