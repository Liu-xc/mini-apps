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
- Provider（ADR-008）：`ProviderKind`（glm/mimo）→ `ProviderUsageFetcher` 按源分发
  （GLM=MonitorUsageProvider；MiMo=Cookie 请求 + MiMoQuotaParser）；
  快照按源独立缓存（snapshot-<kind>.json），`UsageStore.activeKind` 决定展示与刷新目标；
  新增厂商 = 新 RowKind/解析器 + fetcher 分支（it-002 起按 provider registry 演进）。
- 演示/真实按 `AppSettings.demoMode` 切换。
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
- 定位与显隐（**终极架构**，ADR-005/007 演进定稿）：**窗口永久固定为展开尺寸、永不改变大小**
  ——显隐/动画 100% 由 SwiftUI 圆角遮罩驱动（hidden = 刘海挖槽尺寸 180×safeTop，与黑区融合
  不可见；expanded = 全尺寸）。窗口几何零变化 = 零闪现、零位移、零结尾跳变。
  悬停 = 30Hz 光标位置轮询（`NSEvent.mouseLocation` 与可见矩形求交，不依赖事件路由）；
  点击 = contentView `hitTest` 返回 nil 实现可见区域外穿透（菜单图标照常可点）；
  收/展 = 遮罩宽高 spring（先渲染起始帧再延迟 50ms 触发，防初插不补间）。
  监听 `didChangeScreenParametersNotification` 重定位窗口。
- hover 防抖状态机：enter 60ms 延迟展开、exit 180ms 延迟收起（均可取消）；
  「光标仍在可见卡片矩形内」= 假离开或过渡区，直接忽略。
  CGEvent 模拟悬停 + CGWindowList 采样实测：一次展开→稳定→真离开后一次收起，零振荡。

## 已知怪癖

系统级全屏截图（screencapture 全屏模式）不合成该 statusBar 层透明面板，
但 `screencapture -l <windowID>` 单窗口成像正常、CGWindowList 在屏——不影响真实显示。
验证截图一律用 `-l`。
