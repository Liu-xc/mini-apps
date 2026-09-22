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
        applyAppearance(.hidden)

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
        applyAppearance(pinned ? .expanded : .hidden)
    }

    // MARK: - hover 状态机（防抖）

    private func handleEnter() {
        pendingHover?.cancel()
        guard !pinned else { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                self.expandAnimated()
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
                self.collapseAnimated()
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18, execute: work)
    }

    private func togglePin() {
        pendingHover?.cancel()
        if pinned {
            pinned = false
            collapseAnimated()
        } else {
            expand(pinned: true)
        }
    }

    private func expand(pinned newValue: Bool) {
        pinned = newValue
        guard let panel else { return }
        viewModel.appearance = .expanded
        viewModel.reveal = true
        panel.setFrame(frame(for: .expanded), display: true)
    }

    private func handleOutsideClick(at screenPoint: NSPoint) {
        guard pinned, let panel else { return }
        if !panel.frame.contains(screenPoint) {
            pinned = false
            collapseAnimated()
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

    private func frameCenter(on screen: NSScreen) -> CGFloat {
        let hasNotch = screen.safeAreaInsets.top > 0
        let midX = screen.frame.midX
        guard hasNotch else { return midX }
        let left = screen.auxiliaryTopLeftArea?.maxX ?? midX - 90
        let right = screen.auxiliaryTopRightArea?.minX ?? midX + 90
        return (left + right) / 2
    }

    /// 展开卡片尺寸：内容顶边避开刘海挖槽（safeTop + 边距），高度随档数走
    private var expandedSize: CGSize {
        let screen = activeScreen
        let safeTop = max(screen.safeAreaInsets.top, 24)
        let n = CGFloat(max(2, store.snapshot?.displayRows.count ?? 2))
        let content: CGFloat = 16 + 10 + n * 24 + (n - 1) * 10 + 10 + 14   // 头 + 行 + 页脚
        return CGSize(width: 352, height: safeTop + 6 + content + 14)
    }

    private func applyAppearance(_ appearance: IslandViewModel.Appearance) {
        viewModel.appearance = appearance
        guard let panel else { return }
        panel.setFrame(frame(for: appearance), display: false)
    }

    /// 展开：窗口瞬间就位（此时内容是刘海高度的透明黑条），50ms 后把 reveal 拉到全高——
    /// 先让起始态真正渲染一帧，SwiftUI 才会播「从刘海向下延伸」的高度动画
    private func expandAnimated() {
        guard !pinned, let panel else { return }
        viewModel.reveal = false
        viewModel.appearance = .expanded
        panel.setFrame(frame(for: .expanded), display: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                self.viewModel.reveal = true
            }
        }
    }

    /// 收起：reveal 归零 = 卡片向上缩回刘海，动画结束后窗口瞬移回刘海挖槽矩形
    private func collapseAnimated() {
        guard !pinned else { return }
        viewModel.reveal = false
        let snap = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned, self.viewModel.appearance == .expanded else { return }
                self.viewModel.appearance = .hidden
                self.panel?.setFrame(self.frame(for: .hidden), display: false)
            }
        }
        pendingHover = snap
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.36, execute: snap)
    }
}
