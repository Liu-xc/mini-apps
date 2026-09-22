import AppKit
import SwiftUI

@MainActor
final class SettingsWindowController {
    private let settings: AppSettings
    private let store: UsageStore
    private var window: NSWindow?

    init(settings: AppSettings, store: UsageStore) {
        self.settings = settings
        self.store = store
    }

    func show() {
        if window == nil {
            let win = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 460, height: 560),
                styleMask: [.titled, .closable],
                backing: .buffered,
                defer: false
            )
            win.title = "灵岛"
            win.contentViewController = NSHostingController(
                rootView: SettingsView(
                    settings: settings,
                    store: store,
                    onClose: { [weak win] in win?.performClose(nil) }
                )
            )
            win.isReleasedWhenClosed = false
            win.center()
            window = win
        }
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
}
