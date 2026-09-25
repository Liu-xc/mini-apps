import SwiftUI

struct SettingsView: View {
    @ObservedObject var settings: AppSettings
    @ObservedObject var store: UsageStore
    let onClose: () -> Void

    @State private var autoLaunch = AutoLauncher.isEnabled
    @State private var launchError: String?

    var body: some View {
        Form {
            // 凭证分区按注册表渲染（ADR-009：新增源无需改此文件）
            ForEach(ProviderRegistry.all) { descriptor in
                CredentialSection(descriptor: descriptor, store: store)
            }
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

    /// 诊断：逐源查看最近一次原始响应（并列源各自独立，不再只展示「最近的那一个」）
    private var diagnosticsSection: some View {
        Section("诊断") {
            ForEach(ProviderRegistry.all) { descriptor in
                let snapshot = store.state(descriptor.kind).snapshot
                DisclosureGroup(descriptor.title) {
                    ScrollView {
                        Text(rawText(for: descriptor, snapshot: snapshot))
                            .font(.system(size: 9, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                    }
                    .frame(maxHeight: 130)
                    HStack {
                        Button("拷贝响应") {
                            guard let raw = snapshot?.debugRawJSON else { return }
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(raw, forType: .string)
                        }
                        .disabled(snapshot == nil)
                        Spacer()
                    }
                }
            }
            HStack {
                Spacer()
                Button("关闭") { onClose() }
            }
        }
    }

    private func rawText(for descriptor: ProviderDescriptor, snapshot: UsageSnapshot?) -> String {
        if let raw = snapshot?.debugRawJSON { return raw }
        if store.isConfigured(descriptor.kind) { return "暂无 —— 配置凭证后自动刷新一次即可" }
        return "未配置凭证，未发起请求"
    }
}

// MARK: - 单源凭证分区（注册表驱动）

private struct CredentialSection: View {
    let descriptor: ProviderDescriptor
    @ObservedObject var store: UsageStore

    @State private var input = ""
    @State private var confirmClear = false

    private var configured: Bool { store.credentialKinds.contains(descriptor.kind) }

    var body: some View {
        Section(descriptor.sectionTitle) {
            HStack(spacing: 8) {
                SecureField(descriptor.credentialLabel, text: $input)
                    .textFieldStyle(.roundedBorder)
                Button("保存") {
                    store.saveSecret(descriptor.kind, input)
                    input = ""
                }
                .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if configured {
                HStack {
                    Label("\(descriptor.credentialNoun) 已存入本机（0600 加密限制文件）",
                          systemImage: "checkmark.seal.fill")
                        .font(.callout)
                        .foregroundStyle(.green)
                    Spacer()
                    Button("清除", role: .destructive) {
                        confirmClear = true
                    }
                    .confirmationDialog(
                        "清除 \(descriptor.title) 的 \(descriptor.credentialNoun)？",
                        isPresented: $confirmClear
                    ) {
                        Button("清除 \(descriptor.credentialNoun)", role: .destructive) {
                            store.clearCredential(descriptor.kind)
                        }
                        Button("取消", role: .cancel) {}
                    } message: {
                        Text("凭证与本机缓存的用量快照将一并删除，卡片上的该源回到未配置状态。")
                    }
                }
            }
            Text(descriptor.credentialHint)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
