import AppKit

/// 调试自截图（it-003）：渲染本进程自己的窗口成 PNG，不走系统截屏路径——
/// `screencapture -l` / ScreenCaptureKit 都要 TCC 屏录授权，开发机常拿不到；
/// 自渲染（cacheDisplay）无授权依赖，UI 迭代走查可脚本化复现。
///
/// 用法：
///   GLM_ISLAND_SHOT=<目录>            启动 ~2.2s 后（展开动画+刷新已落定）截岛卡并退出
///   GLM_ISLAND_SHOT_SETTINGS=1        同时先弹设置窗，一起截图
@MainActor
enum DebugShot {
    static func schedule(directory: String, islandView: NSView?, settingsWindow: SettingsWindowController) {
        let showSettings = ProcessInfo.processInfo.environment["GLM_ISLAND_SHOT_SETTINGS"] == "1"
        if showSettings {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { settingsWindow.show() }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
            try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
            if let islandView, let image = render(islandView) {
                write(image, directory + "/island-card.png")
            }
            if showSettings, let settingsView = settingsWindow.hostedView, let image = render(settingsView) {
                write(image, directory + "/island-settings.png")
            }
            exit(0)
        }
    }

    /// 把视图按屏幕 backingScale 渲染成位图（自己的视图，无 TCC 依赖）
    private static func render(_ view: NSView) -> NSBitmapImageRep? {
        view.layoutSubtreeIfNeeded()
        let size = view.bounds.size
        guard size.width > 0, size.height > 0 else { return nil }
        let scale = view.window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2
        guard let rep = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: Int(size.width * scale),
            pixelsHigh: Int(size.height * scale),
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else { return nil }
        rep.size = size
        NSGraphicsContext.saveGraphicsState()
        if let context = NSGraphicsContext(bitmapImageRep: rep) {
            NSGraphicsContext.current = context
            view.cacheDisplay(in: view.bounds, to: rep)
        }
        NSGraphicsContext.restoreGraphicsState()
        return rep
    }

    private static func write(_ rep: NSBitmapImageRep, _ path: String) {
        guard let data = rep.representation(using: .png, properties: [:]) else { return }
        try? data.write(to: URL(fileURLWithPath: path))
        NSLog("[island] 调试截图已写入 \(path)")
    }
}
