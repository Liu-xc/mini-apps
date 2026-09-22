# 04 — 架构

## 分层与依赖（单向）

```
main.swift ──> AppDelegate（装配根）
                 │
    ┌────────────┼───────────────────┐
    v            v                   v
IslandWindow   MenuBar           SettingsWindow
Controller     Controller        Controller
    │            │                   │
    └────> UsageStore (@MainActor ObservableObject) <──── SettingsView / IslandRootView
                │
    ┌───────────┼─────────────┐
    v           v             v
MonitorUsage  DemoUsage    SnapshotCache   KeychainStore   AppSettings
Provider      Provider     (磁盘快照)      (Security)      (UserDefaults)
    └──── QuotaResponseParser（宽松解析）────┘
```

- UI 层（SwiftUI）只读 `UsageStore` @Published 状态；动作经闭包回控制器。
- Provider 是值类型 struct，`UsageProviding` 协议隔离；演示/真实按 `AppSettings.demoMode` 切换。
- 解析器是纯静态函数（Data in / Snapshot out），now 注入可测。

## 并发模型

- 全部 UI/Store 状态在 MainActor；`main.swift` 用 `MainActor.assumeIsolated` 包住 runloop。
- 网络经 `URLSession.data(for:) async`；刷新由 `Task { await store.refreshNow() }` 驱动。
- 定时器 `Timer.scheduledTimer`（非重复），每轮回调里 `Task { @MainActor … }` 续期；
  失败退避 `min(8, 2^连续失败数) × 基准间隔`。

## 窗口管理

- `NSPanel`：borderless + nonactivatingPanel、level=.statusBar、
  collectionBehavior=[canJoinAllSpaces, fullScreenAuxiliary, stationary]、hasShadow=false。
- 两态 = 面板 setFrame（NSAnimationContext，cubic(0.16,1,0.3,1) 近似 spring）+ SwiftUI 内容切换。
- hover：NSHostingView 子类借 `mouseEntered/Exited` + `NSTrackingArea(.activeAlways, .inVisibleRect)`
  ——别的 App 前台也生效（本 App 常驻 accessory 不持焦点）。
- 点击外部收起：全局 + 本地 NSEvent monitor，`panel.frame.contains(NSEvent.mouseLocation)` 判定。
- 定位（**刘海下沿锚点**，ADR-005）：刘海挖槽（0…safeTop 高度）是硬件遮挡区，窗口内容
  绝不进入——紧凑/展开两态顶边都贴在 `safeAreaInsets.top`（刘海底沿），水平以刘海中心居中：
  紧凑 180×36 正好接在刘海下方如"刘海长出一截"，展开 352×178 贴同一顶边向下生长；
  无刘海屏回退菜单栏之下顶部居中。监听 `didChangeScreenParametersNotification` 重定位。
- hover 防抖状态机：enter 60ms 延迟展开、exit 180ms 延迟收起（均可取消）；
  「exit 时光标仍在面板 frame 内」= 窗口变形动画补发的假离开，直接忽略。
  CGEvent 模拟悬停实测：一次展开→稳定→真离开后一次收起，零振荡。

## 已知怪癖

系统级全屏截图（screencapture 全屏模式）不合成该 statusBar 层透明面板，
但 `screencapture -l <windowID>` 单窗口成像正常、CGWindowList 在屏——不影响真实显示。
验证截图一律用 `-l`。
