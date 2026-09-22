import AppKit
import SwiftUI

/// NSHostingView 子类：借用 mouseEntered/Exited 实现 hover 展开（.activeAlways，别的 App 前台也生效）
final class IslandContentView: NSHostingView<IslandRootView> {
    var onMouseEnter: (() -> Void)?
    var onMouseExit: (() -> Void)?

    override func mouseEntered(with event: NSEvent) {
        onMouseEnter?()
    }

    override func mouseExited(with event: NSEvent) {
        onMouseExit?()
    }
}

@MainActor
final class IslandWindowController: NSObject {
    /// 胶囊落位锚点：按菜单栏图标占用自动挑选
    enum Anchor {
        /// 刘海右缘（空隙够宽时的首选）
        case rightOfNotch
        /// 刘海左缘
        case leftOfNotch
        /// 刘海正下方、菜单栏之下悬浮（左右都放不下时的兜底）
        case belowMenuBar
    }

    struct BarLayout {
        let screen: NSScreen
        let notchLeft: CGFloat
        let notchRight: CGFloat
        let barHeight: CGFloat
        let anchor: Anchor
    }

    private let store: UsageStore
    private let viewModel = IslandViewModel()
    private var panel: NSPanel?
    private var contentView: IslandContentView?
    private var pinned = false
    private var monitors: [Any] = []
    private var relayoutTimer: Timer?

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        let panel = NSPanel(
            contentRect: frame(for: .compact),
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.isFloatingPanel = true
        panel.level = .statusBar
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
        panel.hidesOnDeactivate = false
        panel.becomesKeyOnlyIfNeeded = true
        panel.ignoresMouseEvents = false

        let root = IslandRootView(
            store: store,
            viewModel: viewModel,
            openSettings: { [weak self] in self?.onOpenSettings?() }
        )
        let content = IslandContentView(rootView: root)
        content.autoresizingMask = [.width, .height]
        content.onMouseEnter = { [weak self] in self?.handleEnter() }
        content.onMouseExit = { [weak self] in self?.handleExit() }
        panel.contentView = content
        contentView = content

        viewModel.onTogglePin = { [weak self] in self?.togglePin() }
        self.panel = panel

        content.addTrackingArea(NSTrackingArea(
            rect: .zero,
            options: [.mouseEnteredAndExited, .activeAlways, .inVisibleRect],
            owner: content,
            userInfo: nil
        ))

        panel.orderFrontRegardless()
        applyAppearance(.compact, animate: false)

        // 首次未配置 Key：展开引导；调试钩子：GLM_ISLAND_EXPAND=1 启动即展开
        if ProcessInfo.processInfo.environment["GLM_ISLAND_EXPAND"] == "1"
            || (!store.hasCredential && store.snapshot == nil) {
            expand(pinned: true)
        }

        monitors.append(NSEvent.addGlobalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleOutsideClick(at: NSEvent.mouseLocation)
            }
        })
        if let token = NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] event in
            if let self, event.window !== self.panel {
                self.handleOutsideClick(at: NSEvent.mouseLocation)
            }
            return event
        } {
            monitors.append(token)
        }

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screenChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )

        // 菜单栏图标会被 App 启停改动（App 菜单变长/状态项增减），周期性重算落位
        relayoutTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.relayout()
            }
        }
    }

    func reposition() {
        applyAppearance(pinned ? .expanded : .compact, animate: false)
    }

    /// 菜单栏占用变化后重新落位；固定展开或鼠标正悬停时不挪窝
    private func relayout() {
        guard let panel, !pinned else { return }
        if let content = contentView,
           panel.frame.contains(NSEvent.mouseLocation) {
            return
        }
        let target = frame(for: viewModel.appearance)
        if panel.frame != target {
            applyAppearance(viewModel.appearance, animate: false)
        }
    }

    // MARK: - 状态流转

    private func handleEnter() {
        guard !pinned else { return }
        applyAppearance(.expanded, animate: true)
        NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .now)
    }

    private func handleExit() {
        guard !pinned else { return }
        applyAppearance(.compact, animate: true)
    }

    private func togglePin() {
        if pinned {
            pinned = false
            applyAppearance(.compact, animate: true)
        } else {
            expand(pinned: true)
        }
    }

    private func expand(pinned newValue: Bool) {
        pinned = newValue
        applyAppearance(.expanded, animate: true)
    }

    private func handleOutsideClick(at screenPoint: NSPoint) {
        guard pinned, let panel else { return }
        if !panel.frame.contains(screenPoint) {
            pinned = false
            applyAppearance(.compact, animate: true)
        }
    }

    @objc private func screenChanged() {
        reposition()
    }

    // MARK: - 布局（智能避让）

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    /// 落位优先级：刘海右缘空隙 ≥ 胶囊宽 → 贴右缘；左缘够 → 贴左缘；都不够 → 刘海正下方悬浮。
    /// 展开态跟随同一锚点（交互态临时盖过图标可接受，常驻的紧凑态绝不压图标）。
    private func computeLayout() -> BarLayout {
        let screen = activeScreen
        let hasNotch = screen.safeAreaInsets.top > 0
        let barHeight = max(screen.safeAreaInsets.top, 24)
        let midX = screen.frame.midX
        let notchRight = hasNotch
            ? (screen.auxiliaryTopRightArea?.minX ?? midX + 90)
            : midX + 90
        let notchLeft = hasNotch
            ? (screen.auxiliaryTopLeftArea?.maxX ?? midX - 90)
            : midX - 90

        let anchor: Anchor
        if hasNotch {
            let occupied = menuBarOccupancy(on: screen, barHeight: barHeight)
            let margin: CGFloat = 6
            let rightNeighbor = occupied.first { $0.lowerBound >= notchRight }?.lowerBound
                ?? screen.frame.maxX
            let leftNeighbor = occupied.last { $0.upperBound <= notchLeft }?.upperBound
                ?? screen.frame.minX
            if rightNeighbor - notchRight - margin >= Self.compactSize.width {
                anchor = .rightOfNotch
            } else if notchLeft - margin - leftNeighbor >= Self.compactSize.width {
                anchor = .leftOfNotch
            } else {
                anchor = .belowMenuBar
            }
        } else {
            // 无刘海屏：不进菜单栏带，悬浮在菜单栏之下顶部居中
            anchor = .belowMenuBar
        }
        return BarLayout(
            screen: screen,
            notchLeft: notchLeft,
            notchRight: notchRight,
            barHeight: barHeight,
            anchor: anchor
        )
    }

    /// 本屏菜单栏带内其他窗口的 x 占用区间（layer 24=App 菜单 / 25=状态项；排除自家进程）
    private func menuBarOccupancy(on screen: NSScreen, barHeight: CGFloat) -> [ClosedRange<CGFloat>] {
        guard let list = CGWindowListCopyWindowInfo([.optionOnScreenOnly], kCGNullWindowID)
            as? [[String: Any]] else { return [] }
        let screenMinX = screen.frame.minX
        let screenMaxX = screen.frame.maxX
        let bandBottom = barHeight + 8
        let myPID = ProcessInfo.processInfo.processIdentifier

        var ranges: [ClosedRange<CGFloat>] = []
        for window in list {
            guard let layer = window[kCGWindowLayer as String] as? Int,
                  (24...25).contains(layer) else { continue }
            guard let pid = window[kCGWindowOwnerPID as String] as? Int, pid != myPID else { continue }
            guard let bounds = window[kCGWindowBounds as String] as? [String: Any],
                  let x = (bounds["X"] as? NSNumber)?.doubleValue,
                  let y = (bounds["Y"] as? NSNumber)?.doubleValue,
                  let width = (bounds["Width"] as? NSNumber)?.doubleValue,
                  let height = (bounds["Height"] as? NSNumber)?.doubleValue else { continue }
            guard width > 1, height > 1 else { continue }
            guard x + width > screenMinX, x < screenMaxX else { continue }
            guard y < bandBottom else { continue }
            ranges.append(max(x, screenMinX)...min(x + width, screenMaxX))
        }
        return ranges.sorted { $0.lowerBound < $1.lowerBound }
    }

    private static let compactSize = CGSize(width: 118, height: 26)
    private static let expandedSize = CGSize(width: 352, height: 228)

    private func frame(for appearance: IslandViewModel.Appearance) -> NSRect {
        let layout = computeLayout()
        let top = layout.screen.frame.maxY
        let center = (layout.notchLeft + layout.notchRight) / 2
        switch appearance {
        case .compact:
            let size = Self.compactSize
            switch layout.anchor {
            case .rightOfNotch:
                return NSRect(x: layout.notchRight + 6, y: top - size.height, width: size.width, height: size.height)
            case .leftOfNotch:
                return NSRect(x: layout.notchLeft - 6 - size.width, y: top - size.height, width: size.width, height: size.height)
            case .belowMenuBar:
                return NSRect(x: center - size.width / 2, y: top - layout.barHeight - 4 - size.height, width: size.width, height: size.height)
            }
        case .expanded:
            let size = Self.expandedSize
            switch layout.anchor {
            case .rightOfNotch:
                return NSRect(x: layout.notchRight - 14, y: top - size.height, width: size.width, height: size.height)
            case .leftOfNotch:
                return NSRect(x: layout.notchLeft + 14 - size.width, y: top - size.height, width: size.width, height: size.height)
            case .belowMenuBar:
                return NSRect(x: center - size.width / 2, y: top - layout.barHeight - 4 - size.height, width: size.width, height: size.height)
            }
        }
    }

    private func applyAppearance(_ appearance: IslandViewModel.Appearance, animate: Bool) {
        viewModel.appearance = appearance
        guard let panel else { return }
        let target = frame(for: appearance)
        if animate {
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.36
                context.timingFunction = CAMediaTimingFunction(controlPoints: 0.16, 1.0, 0.3, 1.0)
                context.allowsImplicitAnimation = true
                panel.setFrame(target, display: true)
                contentView?.layoutSubtreeIfNeeded()
            }
        } else {
            panel.setFrame(target, display: false)
        }
    }
}
