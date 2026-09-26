import AppKit
import Combine
import SwiftUI

/// NSHostingView 子类：可见卡片矩形之外的事件全部穿透（hitTest 返回 nil，
/// 事件落到下层窗口——菜单栏图标照常可点）
final class IslandContentView: NSHostingView<IslandRootView> {
    /// **屏幕全局坐标**（左下原点）是否落在当前可见卡片内。
    /// hitTest 收到的 point 是窗口基坐标系，判定前必须经 `screenPoint(forWindowPoint:windowFrame:)` 换算
    /// （两套坐标系直接比较恒 false → 展开态点击全死，it-003 审计 P0-A）。
    var visibleCardChecker: ((NSPoint) -> Bool)?

    /// 窗口基坐标点 → 屏幕全局坐标点（纯函数，可单测）。
    static func screenPoint(forWindowPoint point: NSPoint, windowFrame: NSRect?) -> NSPoint {
        guard let frame = windowFrame else { return point }
        return NSPoint(x: frame.minX + point.x, y: frame.minY + point.y)
    }

    override func hitTest(_ point: NSPoint) -> NSView? {
        if let checker = visibleCardChecker {
            let screenPoint = Self.screenPoint(forWindowPoint: point, windowFrame: window?.frame)
            if !checker(screenPoint) { return nil }
        }
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
    /// 点击链路日志状态（it-003 实机诊断探针）
    private var hitCount = 0
    private var lastCheckerInside: Bool?
    /// 判定矩形的 appearance 切换插值（修 it-003 审计 P2：判定瞬时切换 vs 遮罩 0.32s 动画不同步——
    /// 收起期可见卡片点击穿透、展开期未画出区域吞点击、收起途中移回不中止）
    private var rectAnimFrom: NSRect?
    private var rectAnimStart: TimeInterval = 0
    private static let rectAnimDuration: TimeInterval = 0.45
    /// 悬停轮询 + 防抖
    private var hoverTimer: Timer?
    private var pendingHover: DispatchWorkItem?
    private var lastInside = false
    private var cancellables = Set<AnyCancellable>()

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?
    /// 岛卡宿主视图（DebugShot 自截图用）
    var hostedView: NSView? { contentView }

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        // 窗口永久固定为展开尺寸、永不改变大小——显隐/动画全部由遮罩驱动，零窗口跳动
        let panel = NSPanel(
            contentRect: expandedFrame,
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

        // 点击穿透判定：仅当前可见卡片矩形内响应（point 已换算为屏幕全局坐标，见 IslandContentView）。
        // 日志：首次调用 + 结果翻转（hitTest 在窗口 bounds 内事件才触发，翻转节流防刷屏）
        content.visibleCardChecker = { [weak self] point in
            guard let self else { return false }
            let inside = self.visibleCardRect.contains(point)
            self.hitCount += 1
            if self.hitCount == 1 {
                NSLog("[island][hit] hitTest 首次调用 screen=(\(Int(point.x)),\(Int(point.y))) inside=\(inside)")
            }
            if inside != self.lastCheckerInside {
                self.lastCheckerInside = inside
                NSLog("[island][hit] 判定翻转 inside=\(inside) screen=(\(Int(point.x)),\(Int(point.y))) rect=(\(Int(self.visibleCardRect.minX)),\(Int(self.visibleCardRect.minY)),\(Int(self.visibleCardRect.width)),\(Int(self.visibleCardRect.height)))")
            }
            return inside
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

        // 数据状态变化（明细行数/配置态改变高度）→ 窗口帧跟随 IslandLayout。
        // objectWillChange 先于变更提交，Task 推迟到提交后再解析。
        store.objectWillChange
            .sink { [weak self] _ in
                Task { @MainActor [weak self] in self?.syncLayout() }
            }
            .store(in: &cancellables)
    }

    // MARK: - hover 状态机（轮询 + 防抖）

    private func updateHover() {
        let inside = visibleCardRect.contains(NSEvent.mouseLocation)
        guard inside != lastInside else { return }
        lastInside = inside
        NSLog("[island][hover] 光标\(inside ? "进入" : "离开")可见区 loc=(\(Int(NSEvent.mouseLocation.x)),\(Int(NSEvent.mouseLocation.y))) appearance=\(viewModel.appearance) pinned=\(pinned)")
        inside ? handleEnter() : handleExit()
    }

    private func handleEnter() {
        pendingHover?.cancel()
        guard !pinned, viewModel.appearance == .hidden else {
            NSLog("[island][hover] enter 被守卫挡下 pinned=\(pinned) appearance=\(viewModel.appearance)")
            return
        }
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
        guard !pinned, viewModel.appearance == .expanded else {
            NSLog("[island][hover] exit 被守卫挡下 pinned=\(pinned) appearance=\(viewModel.appearance)")
            return
        }
        // 光标仍在可见卡片内就不收
        if visibleCardRect.contains(NSEvent.mouseLocation) { return }
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.pinned else { return }
                if self.visibleCardRect.contains(NSEvent.mouseLocation) { return }
                NSLog("[island][hover] 180ms 防抖到期 → 收起")
                self.applyAppearance(.hidden)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18, execute: work)
    }

    private func togglePin() {
        NSLog("[island][tap] togglePin 旧 pinned=\(pinned) appearance=\(viewModel.appearance)")
        pendingHover?.cancel()
        pinned.toggle()
        applyAppearance(pinned ? .expanded : .hidden)
    }

    private func handleOutsideClick(at screenPoint: NSPoint) {
        guard pinned else { return }
        if !visibleCardRect.contains(screenPoint) {
            NSLog("[island][tap] 卡片外点击 (\(Int(screenPoint.x)),\(Int(screenPoint.y))) → 取消固定")
            pinned = false
            applyAppearance(.hidden)
        }
    }

    @objc private func screenChanged() {
        // 显示器/刘海参数变化：窗口（永久全尺寸）重新对位
        syncLayout()
    }

    func reposition() {
        // 外部调用（设置页移动等）的统一重对位入口
        syncLayout()
    }

    // MARK: - 布局（IslandLayout 单一真源：视图遮罩 / hitTest / 窗口帧同源）

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    private var layout: IslandLayout {
        IslandLayout.resolve(store: store, screen: activeScreen)
    }

    private func frameCenter(on screen: NSScreen) -> CGFloat {
        let hasNotch = screen.safeAreaInsets.top > 0
        let midX = screen.frame.midX
        guard hasNotch else { return midX }
        let left = screen.auxiliaryTopLeftArea?.maxX ?? midX - 90
        let right = screen.auxiliaryTopRightArea?.minX ?? midX + 90
        return (left + right) / 2
    }

    /// 永久全尺寸窗口帧（左下原点，刘海下沿锚定）
    private var expandedFrame: NSRect {
        let screen = activeScreen
        return layout.cardRect(centerX: frameCenter(on: screen), screenTop: screen.frame.maxY)
    }

    /// 数据/屏幕变化后同步窗口帧；帧不变则不动（窗口仍「常驻」，只在高度公式变化时 setSize）
    private func syncLayout() {
        guard let panel else { return }
        let target = expandedFrame
        if panel.frame != target {
            panel.setFrame(target, display: false)
        }
    }

    /// 目标可见卡片矩形（全局坐标，左下原点）：隐藏 = 刘海挖槽、展开 = 全卡片，与视图遮罩同尺寸
    private var targetCardRect: NSRect {
        let current = layout
        let screen = activeScreen
        let center = frameCenter(on: screen)
        let top = screen.frame.maxY
        return viewModel.appearance == .hidden
            ? current.notchRect(centerX: center, screenTop: top)
            : current.cardRect(centerX: center, screenTop: top)
    }

    /// 当前判定矩形：appearance 切换后 0.45s 内从旧矩形平滑插值到目标（smoothstep ≈ 遮罩 spring 前慢中快后收）。
    /// 修 it-003 审计 P2：判定瞬时切换 vs 遮罩 0.32s 动画不同步——收起期可见卡片点击穿透、
    /// 展开期未画出区域吞点击、收起途中移回可见区不中止（插值后光标进入可见区即可 hover 重展开）。
    private var visibleCardRect: NSRect {
        let target = targetCardRect
        guard let from = rectAnimFrom else { return target }
        let t = (ProcessInfo.processInfo.systemUptime - rectAnimStart) / Self.rectAnimDuration
        guard t < 1 else { return target }
        let p = t * t * (3 - 2 * t)   // smoothstep
        return NSRect(
            x: from.origin.x + (target.origin.x - from.origin.x) * p,
            y: from.origin.y + (target.origin.y - from.origin.y) * p,
            width: from.width + (target.width - from.width) * p,
            height: from.height + (target.height - from.height) * p
        )
    }

    private func applyAppearance(_ appearance: IslandViewModel.Appearance) {
        // 窗口永不改变大小；显隐完全由 SwiftUI 遮罩尺寸驱动（从刘海长出/缩回）。
        // 判定矩形同步起插值（从当前值出发，支持动画中途改向）
        NSLog("[island][mask] appearance \(viewModel.appearance) → \(appearance)")
        rectAnimFrom = visibleCardRect
        rectAnimStart = ProcessInfo.processInfo.systemUptime
        viewModel.appearance = appearance
    }
}
