import SwiftUI

struct SettingsView: View {
    @ObservedObject var settings: AppSettings
    @ObservedObject var store: UsageStore
    let onClose: () -> Void

    @State private var glmKeyInput = ""
    @State private var mimoCookieInput = ""
    @State private var autoLaunch = AutoLauncher.isEnabled
    @State private var launchError: String?

    var body: some View {
        Form {
            glmSection
            mimoSection
            endpointSection
            refreshSection
            systemSection
            diagnosticsSection
        }
        .formStyle(.grouped)
        .frame(width: 460, height: 620)
        .onChange(of: settings.refreshMinutes) { _, _ in
            store.reschedule()
        }
        .onChange(of: settings.endpointMode) { _, _ in
            Task { await store.refreshAll() }
        }
    }

    private var endpointSection: some View {
        Section("端点（GLM）") {
            Picker("平台", selection: $settings.endpointMode) {
                ForEach(EndpointMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            TextField("控制台链接", text: $settings.consoleURLString)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var refreshSection: some View {
        Section("刷新") {
            Picker("间隔", selection: $settings.refreshMinutes) {
                ForEach([1, 2, 5, 10, 15, 30], id: \.self) { minutes in
                    Text("\(minutes) 分钟").tag(minutes)
                }
            }
            Toggle("演示模式（不请求接口，用假数据走查 UI）", isOn: Binding(
                get: { settings.demoMode },
                set: { store.setDemoMode($0) }
            ))
        }
    }

    private var systemSection: some View {
        Section("系统") {
            Toggle("开机自启", isOn: $autoLaunch)
                .onChange(of: autoLaunch) { _, enabled in
                    launchError = AutoLauncher.setEnabled(enabled)
                    if launchError != nil {
                        autoLaunch = !enabled
                    }
                }
            if let launchError {
                Text(launchError)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
    }

    private var diagnosticsSection: some View {
        Section("诊断") {
            DisclosureGroup("最近一次原始响应（当前内容源）") {
                ScrollView {
                    Text(store.lastFetchedSnapshot?.debugRawJSON ?? "暂无 —— 配置凭证后自动刷新一次即可")
                        .font(.system(size: 9, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                .frame(maxHeight: 150)
                HStack {
                    Button("拷贝响应") {
                        if let raw = store.lastFetchedSnapshot?.debugRawJSON {
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(raw, forType: .string)
                        }
                    }
                    Spacer()
                    Button("关闭") { onClose() }
                }
            }
        }
    }

}

private extension SettingsView {
    var glmSection: some View {
        Section("GLM Coding Plan") {
            HStack(spacing: 8) {
                SecureField("API Key", text: $glmKeyInput)
                    .textFieldStyle(.roundedBorder)
                Button("保存") {
                    store.saveGLMKey(glmKeyInput)
                    glmKeyInput = ""
                }
                .disabled(glmKeyInput.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if store.credentialKinds.contains(.glm) {
                HStack {
                    Label("Key 已存入本机钥匙串", systemImage: "checkmark.seal.fill")
                        .font(.callout)
                        .foregroundStyle(.green)
                    Spacer()
                    Button("清除", role: .destructive) {
                        store.clearGLMKey()
                    }
                }
            }
            Text("官方用量接口 /api/monitor/usage/quota/limit 仅查询、不消耗套餐额度；Key 只存本机钥匙串。")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    var mimoSection: some View {
        Section("小米 MiMo TOKEN Plan") {
            HStack(spacing: 8) {
                SecureField("Cookie 字符串", text: $mimoCookieInput)
                    .textFieldStyle(.roundedBorder)
                Button("保存") {
                    store.saveMimoCookie(mimoCookieInput)
                    mimoCookieInput = ""
                }
                .disabled(mimoCookieInput.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if store.credentialKinds.contains(.mimo) {
                HStack {
                    Label("Cookie 已存入本机钥匙串", systemImage: "checkmark.seal.fill")
                        .font(.callout)
                        .foregroundStyle(.green)
                    Spacer()
                    Button("清除", role: .destructive) {
                        store.clearMimoCookie()
                    }
                }
            }
            Text("该接口只认浏览器登录态：登录 platform.xiaomimimo.com 控制台后，从网络请求复制整段 Cookie 粘贴到这里；Cookie 过期时更新一次即可。")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
