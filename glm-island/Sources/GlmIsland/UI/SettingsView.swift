import SwiftUI

struct SettingsView: View {
    @ObservedObject var settings: AppSettings
    @ObservedObject var store: UsageStore
    let onClose: () -> Void

    @State private var apiKeyInput = ""
    @State private var autoLaunch = AutoLauncher.isEnabled
    @State private var launchError: String?

    var body: some View {
        Form {
            Section("GLM Coding Plan") {
                HStack(spacing: 8) {
                    SecureField("API Key", text: $apiKeyInput)
                        .textFieldStyle(.roundedBorder)
                    Button("保存") {
                        store.saveAPIKey(apiKeyInput)
                        apiKeyInput = ""
                    }
                    .disabled(apiKeyInput.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                if store.hasCredential {
                    HStack {
                        Label("Key 已存入本机钥匙串", systemImage: "checkmark.seal.fill")
                            .font(.callout)
                            .foregroundStyle(.green)
                        Spacer()
                        Button("清除", role: .destructive) {
                            store.clearAPIKey()
                        }
                    }
                }
                Text("官方用量接口 /api/monitor/usage/quota/limit 仅查询、不消耗套餐额度；Key 只存本机钥匙串，不写入任何文件或日志。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("端点") {
                Picker("平台", selection: $settings.endpointMode) {
                    ForEach(EndpointMode.allCases) { mode in
                        Text(mode.title).tag(mode)
                    }
                }
                TextField("控制台链接", text: $settings.consoleURLString)
                    .textFieldStyle(.roundedBorder)
            }

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

            Section("诊断") {
                DisclosureGroup("最近一次原始响应（spike 校准用）") {
                    ScrollView {
                        Text(store.snapshot?.debugRawJSON ?? "暂无 —— 保存 Key 后自动刷新一次即可")
                            .font(.system(size: 9, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                    }
                    .frame(maxHeight: 150)
                    HStack {
                        Button("拷贝响应") {
                            if let raw = store.snapshot?.debugRawJSON {
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
        .formStyle(.grouped)
        .frame(width: 460, height: 560)
        .onChange(of: settings.refreshMinutes) { _, _ in
            store.reschedule()
        }
        .onChange(of: settings.endpointMode) { _, _ in
            Task { await store.refreshNow() }
        }
    }
}
