import ServiceManagement

/// 开机自启（SMAppService，需打包成 .app 后才生效；裸二进制运行会注册失败并回显原因）
enum AutoLauncher {
    static var isEnabled: Bool {
        SMAppService.mainApp.status == .enabled
    }

    /// 返回 nil 表示成功，否则为错误信息
    @discardableResult
    static func setEnabled(_ enabled: Bool) -> String? {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
            return nil
        } catch {
            return error.localizedDescription
        }
    }
}
