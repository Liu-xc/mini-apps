import SwiftUI
import AppKit

/// 菜单栏 UI 的可观测状态（@Published 一律在主队列更新）
final class AppModel: ObservableObject {
    @Published var statuses: [RepoStatus] = []
    @Published var overall: BeaconState = .ok
    @Published var lastProbeAt: Date?
    @Published var config: PosthouseConfig
    @Published var lastPushMessage = ""

    let engine: WatchEngine

    init(engine: WatchEngine? = nil) {
        let cfg = PosthouseConfig.loadOrSeed()
        config = cfg
        self.engine = engine ?? WatchEngine(config: cfg)
        self.engine.onStatuses = { [weak self] statuses, overall in
            DispatchQueue.main.async {
                self?.statuses = statuses
                self?.overall = overall
                self?.lastProbeAt = Date()
            }
        }
        self.engine.onPushResult = { [weak self] repo, message in
            DispatchQueue.main.async {
                self?.lastPushMessage = "\(repo): \(message)"
            }
        }
        self.engine.start()
        self.engine.probeNow()
    }

    var backlogCount: Int { statuses.filter { $0.beacon != .ok }.count }

    func probeNow() { engine.probeNow() }

    func pushNow(_ path: String) { engine.pushNow(path: path) }

    func generateGazetteNow() { engine.generateGazette() }

    /// 全局自动推送总开关
    func toggleAutoPush() {
        config.autoPushEnabled.toggle()
        config.save()
        engine.updateConfig(config)
        PLog.info("自动推送总开关 → \(config.autoPushEnabled)")
    }

    /// 仓库级白名单开关
    func toggleWhitelist(_ path: String) {
        config.autoPushWhitelist[path] = !(config.autoPushWhitelist[path] == true)
        config.save()
        engine.updateConfig(config)
    }

    func openConfigFile() { NSWorkspace.shared.open(DirSupport.configFileURL) }
    func openLogFile() { NSWorkspace.shared.open(PLog.logFileURL) }

    /// M2 一键网络诊断：生成 .command 交给终端跑 network-rescue 的诊断脚本
    func openNetworkDoctor() {
        let script = """
        #!/bin/zsh
        echo "=== 驿站 · 网络诊断（network-rescue） ==="
        bash "$HOME/.agents/skills/network-rescue/scripts/network_doctor.sh"
        echo
        read -k1 -s -r -p "按任意键关闭..."
        """
        let url = DirSupport.appSupport.appendingPathComponent("network-doctor.command")
        try? script.data(using: .utf8)?.write(to: url)
        try? FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: url.path)
        NSWorkspace.shared.open(url)
        PLog.info("已打开网络诊断终端窗口")
    }

    func openGazetteDir() {
        let url = URL(fileURLWithPath: config.gazetteOutputDir, isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        NSWorkspace.shared.open(url)
    }
}

/// MenuBarExtra 的 label 与 content 是两个视图树，用单例共享同一 AppModel
final class SharedModel {
    static let shared = AppModel()
}

@main
struct PosthouseApp: SwiftUI.App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    init() {
        // 一次性 CLI 模式（--probe/--push/--gazette）：跑完即退，不起菜单栏
        if let code = CLI.runOnce(args: CommandLine.arguments) {
            exit(code)
        }
    }

    var body: some Scene {
        MenuBarExtra {
            MenuContentView()
        } label: {
            MenuBarLabel()
        }
        .menuBarExtraStyle(.menu)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        PLog.info("Posthouse 启动")
    }
}

/// 菜单栏图标：三态 + 异常仓库计数
struct MenuBarLabel: View {
    @ObservedObject private var model = SharedModel.shared

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: model.overall.systemImage)
            if model.backlogCount > 0 {
                Text("\(model.backlogCount)")
            }
        }
    }
}

struct MenuContentView: View {
    @ObservedObject var model = SharedModel.shared

    var body: some View {
        Section {
            Text(overviewLine)
        }
        Divider()

        if model.statuses.isEmpty {
            Text("未发现仓库，请检查配置")
        } else {
            ForEach(model.statuses) { status in
                repoMenu(status)
            }
        }
        Divider()

        Section("烽火台") {
            Button("立即探测全部") { model.probeNow() }
            Button("网络诊断（network-rescue）…") { model.openNetworkDoctor() }
            Toggle("自动推送总开关", isOn: Binding(
                get: { model.config.autoPushEnabled },
                set: { _ in model.toggleAutoPush() }
            ))
            if !model.lastPushMessage.isEmpty {
                Text(model.lastPushMessage)
            }
        }
        Divider()

        Section("自动推送白名单") {
            ForEach(model.statuses.filter { $0.remoteConfigured }) { status in
                Toggle(status.name, isOn: Binding(
                    get: { model.config.autoPushWhitelist[status.path] == true },
                    set: { _ in model.toggleWhitelist(status.path) }
                ))
            }
        }
        Divider()

        Section("邸报") {
            Button("立即生成今日邸报") { model.generateGazetteNow() }
            Button("打开邸报目录") { model.openGazetteDir() }
        }
        Divider()

        Button("打开配置…") { model.openConfigFile() }
        Button("打开日志…") { model.openLogFile() }
        Button("退出驿站") { NSApp.terminate(nil) }
    }

    private var overviewLine: String {
        let backlog = model.statuses.filter { $0.beacon == .backlog }.count
        let dead = model.statuses.filter { $0.beacon == .unreachable }.count
        var parts = ["\(model.statuses.count) 仓在守"]
        if backlog > 0 { parts.append("狼烟 \(backlog)") }
        if dead > 0 { parts.append("熄火 \(dead)") }
        if backlog == 0 && dead == 0 { parts.append("全线平安") }
        if let t = model.lastProbeAt {
            parts.append("探测 " + GazetteStore.timeFormatter.string(from: t))
        }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder
    private func repoMenu(_ s: RepoStatus) -> some View {
        Menu {
            Button {
                model.pushNow(s.path)
            } label: {
                Text(s.ahead > 0 ? "推送 \(s.ahead) 个提交" : "无积压可推")
            }
            .disabled(s.ahead == 0 || !s.remoteConfigured)
            Button("在访达中打开") {
                NSWorkspace.shared.open(URL(fileURLWithPath: s.path, isDirectory: true))
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: s.beacon.systemImage)
                Text(s.name)
                Spacer().frame(width: 6)
                Text(s.summaryLine)
            }
        }
    }
}
