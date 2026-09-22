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
    private let store: UsageStore
    private let viewModel = IslandViewModel()
    private var panel: NSPanel?
    private var contentView: IslandContentView?
    private var pinned = false
    private var monitors: [Any] = []

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
    }

    func reposition() {
        applyAppearance(pinned ? .expanded : .compact, animate: false)
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

    // MARK: - 布局

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    /// 紧凑态贴刘海右缘；展开态向左咬合刘海一点点、向下生长；无刘海屏回退顶部居中
    private func frame(for appearance: IslandViewModel.Appearance) -> NSRect {
        let screen = activeScreen
        let top = screen.frame.maxY
        let hasNotch = screen.safeAreaInsets.top > 0
        switch appearance {
        case .compact:
            let width: CGFloat = 118
            let height: CGFloat = 26
            let x = hasNotch
                ? (screen.auxiliaryTopRightArea?.minX ?? screen.frame.midX) + 6
                : screen.frame.midX - width / 2
            return NSRect(x: x, y: top - height, width: width, height: height)
        case .expanded:
            let width: CGFloat = 352
            let height: CGFloat = 228
            let x = hasNotch
                ? (screen.auxiliaryTopRightArea?.minX ?? screen.frame.midX) - 14
                : screen.frame.midX - width / 2
            return NSRect(x: x, y: top - height, width: width, height: height)
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
