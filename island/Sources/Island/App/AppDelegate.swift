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

        NSLog("[island][boot] 启动完成 credentialKinds=\(store.credentialKinds.map(\.rawValue).sorted()) demo=\(settings.demoMode) refresh=\(settings.refreshMinutes)min")
        Task { await store.start() }

        // 调试自截图：GLM_ISLAND_SHOT=<目录>（可选 GLM_ISLAND_SHOT_SETTINGS=1），拍完即退
        if let directory = ProcessInfo.processInfo.environment["GLM_ISLAND_SHOT"] {
            DebugShot.schedule(directory: directory, islandView: island.hostedView, settingsWindow: settingsWindow)
        }
    }

    private func openConsole() {
        let urlString = settings.consoleURLString.isEmpty
            ? EndpointMode.bigmodel.defaultConsoleURL
            : settings.consoleURLString
        guard let url = URL(string: urlString) else { return }
        NSWorkspace.shared.open(url)
    }
}
