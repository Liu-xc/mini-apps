import AppKit
import SwiftUI

/// NSHostingView 子类：可见卡片矩形之外的事件全部穿透（hitTest 返回 nil，
/// 事件落到下层窗口——菜单栏图标照常可点）
final class IslandContentView: NSHostingView<IslandRootView> {
    /// point（contentView 坐标，左下原点）是否落在当前可见卡片内
    var visibleCardChecker: ((NSPoint) -> Bool)?

    override func hitTest(_ point: NSPoint) -> NSView? {
        if let checker = visibleCardChecker, !checker(point) { return nil }
        return super.hitTest(point)
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
    /// 悬停轮询 + 防抖
    private var hoverTimer: Timer?
    private var pendingHover: DispatchWorkItem?
    private var lastInside = false

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        // 窗口永久固定为展开尺寸、永不改变大小——显隐/动画全部由遮罩驱动，零窗口跳动
        let panel = NSPanel(
            contentRect: NSRect(origin: frame(for: .hidden).origin, size: expandedSize),
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
        panel.contentView = content
        contentView = content

        viewModel.onTogglePin = { [weak self] in self?.togglePin() }
        self.panel = panel

        // 点击穿透判定：仅当前可见卡片矩形内响应（随 reveal 动画实时更新）
        content.visibleCardChecker = { [weak self] point in
            self?.visibleCardRect.contains(point) ?? false
        }

        panel.orderFrontRegardless()
        applyAppearance(.hidden)

        // 首次未配置任何凭证：展开引导；调试钩子：GLM_ISLAND_EXPAND=1 启动即展开
        if ProcessInfo.processInfo.environment["GLM_ISLAND_EXPAND"] == "1"
            || (store.credentialKinds.isEmpty && store.allRows.isEmpty) {
            applyAppearance(.expanded)
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

        // 悬停轮询：30Hz 读取光标位置与可见卡片矩形求交（不依赖事件路由，
        // 永久全尺寸窗口 + hitTest 穿透的形态下最可靠）
        hoverTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.updateHover()
            }
        }

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(screenChanged),
            name: NSApplication.didChangeScreenParametersNotification,
            object: nil
        )
    }

    // MARK: - hover 状态机（轮询 + 防抖）

    private func updateHover() {
        let inside = visibleCardRect.contains(NSEvent.mouseLocation)
        guard inside != lastInside else { return }
        lastInside = inside
        inside ? handleEnter() : handleExit()
    }

    private func handleEnter() {
        pendingHover?.cancel()
        guard !pinned, viewModel.appearance == .hidden else { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                self.applyAppearance(.expanded)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06, execute: work)
    }

    private func handleExit() {
        pendingHover?.cancel()
        guard !pinned, viewModel.appearance == .expanded else { return }
        // 光标仍在可见卡片内就不收
        if visibleCardRect.contains(NSEvent.mouseLocation) { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                if self.visibleCardRect.contains(NSEvent.mouseLocation) { return }
                self.applyAppearance(.hidden)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18, execute: work)
    }

    private func togglePin() {
        pendingHover?.cancel()
        pinned.toggle()
        applyAppearance(pinned ? .expanded : .hidden)
    }

    private func handleOutsideClick(at screenPoint: NSPoint) {
        guard pinned else { return }
        if !visibleCardRect.contains(screenPoint) {
            pinned = false
            applyAppearance(.hidden)
        }
    }

    @objc private func screenChanged() {
        // 显示器/刘海参数变化：窗口（永久全尺寸）重新对位
        guard let panel else { return }
        panel.setFrame(frame(for: .expanded), display: false)
    }

    func reposition() {
        // 显示器/刘海参数变化：窗口（永久全尺寸）重新对位
        guard let panel else { return }
        panel.setFrame(frame(for: .expanded), display: false)
    }

    // MARK: - 布局（刘海下沿锚点，遮罩驱动）

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    private func frameCenter(on screen: NSScreen) -> CGFloat {
        let hasNotch = screen.safeAreaInsets.top > 0
        let midX = screen.frame.midX
        guard hasNotch else { return midX }
        let left = screen.auxiliaryTopLeftArea?.maxX ?? midX - 90
        let right = screen.auxiliaryTopRightArea?.minX ?? midX + 90
        return (left + right) / 2
    }

    private func frame(for appearance: IslandViewModel.Appearance) -> NSRect {
        let screen = activeScreen
        let top = screen.frame.maxY
        let center = frameCenter(on: screen)
        let size = expandedSize
        return NSRect(x: center - size.width / 2, y: top - size.height, width: size.width, height: size.height)
    }

    /// 展开卡片尺寸：内容顶边避开刘海挖槽（safeTop + 边距）
    private var expandedSize: CGSize {
        let screen = activeScreen
        let safeTop = max(screen.safeAreaInsets.top, 24)
        return CGSize(width: 352, height: safeTop + 6 + IslandRootView.panelHeight + 10 + 14 + 14)
    }

    /// 当前可见卡片矩形（全局坐标，左下原点；随显隐变化，与视图遮罩同尺寸）
    private var visibleCardRect: NSRect {
        let screen = activeScreen
        let safeTop = max(screen.safeAreaInsets.top, 24)
        let hidden = viewModel.appearance == .hidden
        let w: CGFloat = hidden ? 180 : 352
        let h: CGFloat = hidden ? safeTop : safeTop + 6 + IslandRootView.panelHeight + 10 + 14 + 14
        let center = frameCenter(on: screen)
        let top = screen.frame.maxY
        return NSRect(x: center - w / 2, y: top - h, width: w, height: h)
    }

    private func applyAppearance(_ appearance: IslandViewModel.Appearance) {
        // 窗口永不改变大小；显隐完全由 SwiftUI 遮罩尺寸驱动（从刘海长出/缩回）
        viewModel.appearance = appearance
    }
}
