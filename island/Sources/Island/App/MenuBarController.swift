import AppKit
import Combine

@MainActor
final class MenuBarController: NSObject, NSMenuDelegate {
    private let store: UsageStore
    private var statusItem: NSStatusItem?
    private var cancellables = Set<AnyCancellable>()

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        let item = NSStatusBar.system.statusItem(withLength: 26)
        item.button?.image = Self.icon(primaries: primaryRows())
        item.button?.toolTip = "灵岛 · Coding Plan 用量"
        let menu = NSMenu()
        menu.delegate = self
        item.menu = menu
        statusItem = item

        // 数据状态变化 → 图标健康色跟随（objectWillChange 先于提交，Task 推迟一拍再画）
        store.objectWillChange
            .sink { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.statusItem?.button?.image = Self.icon(primaries: self.primaryRows())
                }
            }
            .store(in: &cancellables)
    }

    /// 每源主档一行（注册表顺序，至多三根彩条）
    private func primaryRows() -> [QuotaRow] {
        ProviderRegistry.all.compactMap {
            store.state($0.kind).snapshot?.displayRows.first
        }
    }

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        for item in summaryItems() {
            menu.addItem(item)
        }
        menu.addItem(.separator())

        menu.addItem(selfmenuItem("立即刷新", action: #selector(refreshTapped)))
        let demo = selfmenuItem("演示模式", action: #selector(demoTapped))
        demo.state = store.isDemoActive ? .on : .off
        menu.addItem(demo)
        menu.addItem(.separator())

        let settings = selfmenuItem("设置…", action: #selector(settingsTapped), key: ",")
        menu.addItem(settings)
        menu.addItem(selfmenuItem("打开控制台", action: #selector(consoleTapped)))
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "退出灵岛", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
    }

    private func selfmenuItem(_ title: String, action: Selector, key: String = "") -> NSMenuItem {
        let item = NSMenuItem(title: title, action: action, keyEquivalent: key)
        item.target = self
        return item
    }

    // MARK: - 摘要（按源分组：源名头 + 缩进行；逐源空态/错误独立展示）

    private func summaryItems() -> [NSMenuItem] {
        var items: [NSMenuItem] = []
        for descriptor in ProviderRegistry.all {
            items.append(staticLine(descriptor.title, indent: 0))
            let state = store.state(descriptor.kind)
            let rows = state.snapshot?.displayRows ?? []
            if rows.isEmpty {
                items.append(staticLine(emptyLine(descriptor: descriptor, state: state), indent: 2))
            } else {
                for row in rows {
                    let pct = row.remainingPercent.map { "\(Int($0))%" } ?? "--%"
                    items.append(staticLine("\(row.label) \(pct)", indent: 2))
                }
                if let error = state.lastError {
                    items.append(staticLine("⚠︎ \(error)", indent: 2))
                }
            }
        }
        return items
    }

    private func emptyLine(descriptor: ProviderDescriptor, state: SourceState) -> String {
        if !store.isConfigured(descriptor.kind) { return "未配置凭证（设置…）" }
        if let error = state.lastError { return "⚠︎ \(error)" }
        return "尚未获取到用量数据"
    }

    private func staticLine(_ title: String, indent: Int) -> NSMenuItem {
        let item = NSMenuItem(
            title: String(repeating: " ", count: indent) + title,
            action: nil,
            keyEquivalent: ""
        )
        item.isEnabled = false
        return item
    }

    // MARK: - 动作

    @objc private func refreshTapped() {
        Task { await store.refreshAll() }
    }

    @objc private func demoTapped() {
        store.setDemoMode(!store.isDemoActive)
    }

    @objc private func settingsTapped() {
        onOpenSettings?()
    }

    @objc private func consoleTapped() {
        onOpenConsole?()
    }

    // MARK: - 图标（it-003：三根彩条 = 各源主档健康度，语义与卡片环一致）

    /// 至多三根（每源主档一根），x = index×7、4×10 圆角 1.5；
    /// 无数据 → 三根中性灰条（占位轮廓不塌缩）；非模板图，保留健康色。
    private static func icon(primaries: [QuotaRow]) -> NSImage {
        let size = NSSize(width: 18, height: 14)
        let image = NSImage(size: size)
        image.lockFocus()
        for index in 0..<3 {
            let rect = NSRect(x: CGFloat(index) * 7, y: 2, width: 4, height: 10)
            let color: NSColor
            if index < primaries.count {
                color = IslandTheme.levelNSColor(primaries[index].remainingPercent)
            } else {
                color = NSColor.systemGray.withAlphaComponent(0.55)
            }
            color.setFill()
            NSBezierPath(roundedRect: rect, xRadius: 1.5, yRadius: 1.5).fill()
        }
        image.unlockFocus()
        return image
    }
}
