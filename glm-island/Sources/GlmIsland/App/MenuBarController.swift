import AppKit

@MainActor
final class MenuBarController: NSObject, NSMenuDelegate {
    private let store: UsageStore
    private var statusItem: NSStatusItem?

    var onOpenSettings: (() -> Void)?
    var onOpenConsole: (() -> Void)?

    init(store: UsageStore) {
        self.store = store
    }

    func install() {
        let item = NSStatusBar.system.statusItem(withLength: 26)
        item.button?.image = Self.icon()
        item.button?.toolTip = "GLM Coding Plan 用量"
        let menu = NSMenu()
        menu.delegate = self
        item.menu = menu
        statusItem = item
    }

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        menu.addItem(summaryItem())
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
        menu.addItem(NSMenuItem(title: "退出 GLM 灵动岛", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
    }

    private func selfmenuItem(_ title: String, action: Selector, key: String = "") -> NSMenuItem {
        let item = NSMenuItem(title: title, action: action, keyEquivalent: key)
        item.target = self
        return item
    }

    private func summaryItem() -> NSMenuItem {
        let title: String
        if let snapshot = store.snapshot, !snapshot.rows.isEmpty {
            title = snapshot.rows.map { row in
                let value = row.remainingPercent.map { "\(Int($0.rounded()))%" } ?? "--%"
                return "\(row.label) \(value)"
            }
            .joined(separator: " · ")
        } else {
            title = "尚未获取到用量数据"
        }
        let item = NSMenuItem(title: title, action: nil, keyEquivalent: "")
        item.isEnabled = false
        return item
    }

    @objc private func refreshTapped() {
        Task { await store.refreshNow() }
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

    /// 三根小彩条图标（5h 蓝 / 每周绿 / MCP 橙）
    private static func icon() -> NSImage {
        let size = NSSize(width: 18, height: 14)
        let image = NSImage(size: size)
        image.lockFocus()
        let bars: [(NSRect, NSColor)] = [
            (NSRect(x: 0, y: 2, width: 4, height: 10), .systemBlue),
            (NSRect(x: 7, y: 2, width: 4, height: 10), .systemGreen),
            (NSRect(x: 14, y: 2, width: 4, height: 10), .systemOrange),
        ]
        for (rect, color) in bars {
            color.setFill()
            NSBezierPath(roundedRect: rect, xRadius: 1.5, yRadius: 1.5).fill()
        }
        image.unlockFocus()
        return image
    }
}
