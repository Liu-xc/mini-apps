import AppKit

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private let settings = AppSettings()
    private var store: UsageStore?
    private var island: IslandWindowController?
    private var menuBar: MenuBarController?
    private var settingsWindow: SettingsWindowController?
    private var observers: [NSObjectProtocol] = []

    func applicationDidFinishLaunching(_ notification: Notification) {
        let store = UsageStore(settings: settings)
        self.store = store

        let settingsWindow = SettingsWindowController(settings: settings, store: store)
        self.settingsWindow = settingsWindow

        let island = IslandWindowController(store: store)
        island.onOpenSettings = { [weak self] in self?.settingsWindow?.show() }
        island.onOpenConsole = { [weak self] in self?.openConsole() }
        island.install()
        self.island = island

        let menuBar = MenuBarController(store: store)
        menuBar.onOpenSettings = { [weak self] in self?.settingsWindow?.show() }
        menuBar.onOpenConsole = { [weak self] in self?.openConsole() }
        menuBar.install()
        self.menuBar = menuBar

        observers.append(NotificationCenter.default.addObserver(
            forName: NSApplication.didChangeScreenParametersNotification,
            object: nil,
            queue: .main
        ) { [weak island] _ in
            Task { @MainActor in island?.reposition() }
        })

        Task { await store.start() }
    }

    private func openConsole() {
        let urlString = settings.consoleURLString.isEmpty
            ? EndpointMode.bigmodel.defaultConsoleURL
            : settings.consoleURLString
        guard let url = URL(string: urlString) else { return }
        NSWorkspace.shared.open(url)
    }
}
