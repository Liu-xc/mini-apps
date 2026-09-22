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

    // MARK: - hover 状态机（防抖）

    private func handleEnter() {
        pendingHover?.cancel()
        guard !pinned, viewModel.appearance == .compact else { return }
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
                self.applyAppearance(.compact, animate: true)
            }
        }
        pendingHover = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18, execute: work)
    }

    private func togglePin() {
        pendingHover?.cancel()
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

    // MARK: - 布局（刘海正中）

    private var activeScreen: NSScreen {
        NSScreen.screens.first { $0.safeAreaInsets.top > 0 }
            ?? NSScreen.main
            ?? NSScreen.screens[0]
    }

    /// 紧凑/展开都以刘海水平中心为锚：刘海是硬件挖槽，永不与菜单栏图标冲突。
    /// 有刘海：贴屏幕顶沿（胶囊融进刘海黑区）；无刘海：悬浮在菜单栏之下顶部居中。
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
        // 内容顶边贴刘海下沿（yTopOffset = 安全区高度），绝不进刘海挖槽区
        let yTopOffset: CGFloat = max(screen.safeAreaInsets.top, 24)
        let size = appearance == .compact ? Self.compactSize : expandedSize
        return NSRect(
            x: center - size.width / 2,
            y: top - yTopOffset - size.height,
            width: size.width,
            height: size.height
        )
    }

    /// 紧凑态两行全信息（180×36，正好覆住刘海宽度）；展开态两档 178，第三档（other）出现时加高
    private static let compactSize = CGSize(width: 180, height: 36)

    private var expandedSize: CGSize {
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
