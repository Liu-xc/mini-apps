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
                │  states: [ProviderKind: SourceState]   credentialKinds
                │  refreshAll() 按 registry 逐源成败独立记录
    ┌───────────┼──────────────────────────────────────┐
    v           v                                      v
ProviderRegistry ──> makeProvider(settings)      SnapshotCache(每源一份)
（凭证/文案/演示数据                           │
  单表驱动一切）                               CredentialStore（0600 文件）
    │                                          AppSettings（UserDefaults）
    v
MonitorUsageProvider(GLM) / MiMoUsageProvider / StaticDemoProvider
    └──── QuotaResponseParser · MiMoQuotaParser（宽松解析，共用 JSONLoose）──┘

UI 侧单一真源：IslandLayout ←─ resolve(store, screen)
    ├─> SwiftUI 遮罩尺寸（IslandRootView）
    ├─> hitTest 穿透判定（IslandWindowController.visibleCardRect）
    └─> 窗口帧（expandedFrame；store.objectWillChange → syncLayout）
FooterStatus（页脚状态纯函数）· IslandTheme（健康度阈值/色值，SwiftUI+AppKit 共用）
```

- UI 层（SwiftUI）只读 `UsageStore` @Published 状态；动作经闭包回控制器。
- **内容源注册表（ADR-009，it-003）**：`ProviderRegistry.all` 单表驱动——凭证路径、卡片面板、
  设置分区、菜单摘要、演示数据全部 `ForEach` 遍历本表，**无按源硬编码 switch**；
  新增源 = `ProviderKind` case + registry 一条 +（新格式才需）解析器。
- **按源状态**：`states[kind] = SourceState(snapshot, lastError, isFetching)`——
  任一源失败落在自己头上（面板头「刷新失败」+ 页脚首个错误），他源照常展示，成功即清除；
  旧的全局 `status` 单值已废止（it-002 AC4 吞错根因）。
- 快照按源独立缓存（snapshot-<kind>.json）；凭证清除时缓存一并删除（防重启幽灵数据）。
- 演示/真实按 `AppSettings.demoMode` 切换（demo 数据由 registry 注入，走同一条刷新管线）。
- 解析器是纯静态函数（Data in / Snapshot out），now 注入可测。
- GLM auto 端点赢家由 store 回写 `settings.preferredEndpoint`（ADR-003）。

## 并发模型

- 全部 UI/Store 状态在 MainActor；`main.swift` 用 `MainActor.assumeIsolated` 包住 runloop。
- 网络经 `URLSession.data(for:) async`；刷新由 `Task { await store.refreshAll() }` 驱动。
- 定时器 `Timer.scheduledTimer`（非重复），每轮回调里 `Task { @MainActor … }` 续期；
  失败退避 `min(8, 2^连续失败数) × 基准间隔`（任一源成功即清零）。

## 窗口管理

- `NSPanel`：borderless + nonactivatingPanel、level=.statusBar、
  collectionBehavior=[canJoinAllSpaces, fullScreenAuxiliary, stationary]、hasShadow=false。
- 定位与显隐（**终极架构**，ADR-005/007 演进定稿）：**窗口永久固定为展开尺寸、永不改变大小**
  ——显隐/动画 100% 由 SwiftUI 圆角遮罩驱动（hidden = 刘海挖槽尺寸 180×safeTop，与黑区融合
  不可见；expanded = 全尺寸）。窗口几何零变化 = 零闪现、零位移、零结尾跳变。
  尺寸随明细行数变化时由 `syncLayout()` 幂等 setFrame（帧不变则不动，
  store.objectWillChange 触发、推迟一拍解析）。
- 尺寸与矩形**全部来自 `IslandLayout`**（单一真源，ADR-009）：遮罩、hitTest、窗口帧消费
  同一实例；面板高度公式 = 实际渲染分支（`detailLineCount` 与视图渲染严格一致）。
- 悬停 = 30Hz 光标位置轮询（`NSEvent.mouseLocation` 与可见矩形求交，不依赖事件路由）；
  点击 = contentView `hitTest` 返回 nil 实现可见区域外穿透（菜单图标照常可点）；
  收/展 = 遮罩宽高 spring(response 0.32, damping 0.9)（动画只挂遮罩尺寸，窗口不动）。
  监听 `didChangeScreenParametersNotification` 重定位窗口。
- hover 防抖状态机：enter 60ms 延迟展开、exit 180ms 延迟收起（均可取消）；
  「光标仍在可见卡片矩形内」= 假离开或过渡区，直接忽略。
  CGEvent 模拟悬停 + CGWindowList 采样实测：一次展开→稳定→真离开后一次收起，零振荡。

## 菜单栏

- 图标 = 各源主档健康度色条（至多三根，`IslandTheme.levelNSColor`，无数据灰），
  `store.objectWillChange` 触发重绘；非 template（保色）。
- 摘要按 registry 分组：源名头 + 各档行（缩进），空源显示未配置/错误/尚未获取；数据与卡片同源。

## 已知怪癖

系统级全屏截图（screencapture 全屏模式）不合成该 statusBar 层透明面板，
`screencapture -l <windowID>` 还需要屏录 TCC 授权——**走查统一用 DebugShot 自截图**
（本进程 `cacheDisplay` 渲染自身窗口，无 TCC 依赖，ADR-011 / `GLM_ISLAND_SHOT`）。
