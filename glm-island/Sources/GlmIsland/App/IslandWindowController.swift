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
    /// 防抖：进入/退出都走可取消延迟，吸收窗口变形时窗口服务器补发的成对 enter/exit
    private var pendingHover: DispatchWorkItem?

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        let panel = NSPanel(
            contentRect: frame(for: .hidden),
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
        panel.acceptsMouseMovedEvents = true

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
        applyAppearance(.hidden, animate: false)

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
        applyAppearance(pinned ? .expanded : .hidden, animate: false)
    }

    // MARK: - hover 状态机（防抖）

    private func handleEnter() {
        pendingHover?.cancel()
        guard !pinned, viewModel.appearance == .hidden else { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                self.applyAppearance(.expanded, animate: true)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06, execute: work)
    }

    private func handleExit() {
        pendingHover?.cancel()
        guard !pinned, let panel, viewModel.appearance == .expanded else { return }
        // 假离开：窗口变形时窗口服务器会补发 exit，但光标其实还在面板内
        if panel.frame.contains(NSEvent.mouseLocation) { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, let panel = self.panel, !self.pinned else { return }
                // 延迟期间光标又进来了就不收
                if panel.frame.contains(NSEvent.mouseLocation) { return }
                self.applyAppearance(.hidden, animate: true)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18, execute: work)
    }

    private func togglePin() {
        pendingHover?.cancel()
        if pinned {
            pinned = false
            applyAppearance(.hidden, animate: true)
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
            applyAppearance(.hidden, animate: true)
        }
    }

    @objc private func screenChanged() {
        reposition()
    }

    // MARK: - 布局（刘海下沿锚点）

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    /// 隐藏态 = 刘海挖槽矩形本身（不可见但收 hover）；展开态从刘海中心向下生长、
    /// 顶边贴屏幕顶沿（顶部两角直角，与顶边无缝、不与刘海之间留缝）
    private func frame(for appearance: IslandViewModel.Appearance) -> NSRect {
        let screen = activeScreen
        let top = screen.frame.maxY
        let hasNotch = screen.safeAreaInsets.top > 0
        let midX = screen.frame.midX
        let center: CGFloat
        if hasNotch {
            let left = screen.auxiliaryTopLeftArea?.maxX ?? midX - 90
            let right = screen.auxiliaryTopRightArea?.minX ?? midX + 90
            center = (left + right) / 2
        } else {
            center = midX
        }
        let safeTop = max(screen.safeAreaInsets.top, 24)
        switch appearance {
        case .hidden:
            return NSRect(x: center - 90, y: top - safeTop, width: 180, height: safeTop)
        case .expanded:
            let size = expandedSize
            return NSRect(x: center - size.width / 2, y: top - size.height, width: size.width, height: size.height)
        }
    }

    private var expandedSize: CGSize {
        // 两档 178；接口吐出第三档（other）时加高容纳
        let rowCount = max(2, store.snapshot?.displayRows.count ?? 2)
        return CGSize(width: 352, height: rowCount >= 3 ? 222 : 178)
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
