import AppKit

// GUI 入口：accessory 策略（无 Dock 图标，菜单栏 + 灵动岛胶囊常驻）
let app = NSApplication.shared
app.setActivationPolicy(.accessory)

MainActor.assumeIsolated {
    let delegate = AppDelegate()
    app.delegate = delegate
    app.run()
}
