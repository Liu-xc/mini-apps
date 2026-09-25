import AppKit
import SwiftUI

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
