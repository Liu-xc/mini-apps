# 04 · 技术架构

双端**分开实现、各自原生**（ADR-001）：Android 与 wardrobe 同栈同构；macOS 用 SwiftUI/AppKit 做轻量菜单栏应用。两端不共享代码，共享的是规格（本目录）与数据格式（`clips.json`，ADR-009）。

## 目录结构

```
clips/
├─ README.md / specs/        # 共享规格（唯一跨端契约层）
├─ android/                  # Kotlin + Compose，独立 Gradle 工程
└─ mac/                      # SwiftUI + AppKit，独立 Swift Package
```

## Android 端（android/，与 wardrobe 同栈同构）

```
com.leo.clips/
├─ ClipsApp.kt                       # Application：组合根（手动装配）
├─ MainActivity.kt                   # 单 Activity：onResume 捕获 + 挂 UI
├─ CaptureTileService.kt             # 快捷设置磁贴 → 启动 MainActivity
├─ ShareReceiverActivity.kt          # ACTION_SEND text/plain → 入库 → Toast → finish
├─ domain/
│  ├─ model/      ClipEntry TypeHint Source
│  ├─ repository/ ClipRepository(接口)
│  └─ usecase/    CaptureClip SearchClips CopyBack（淘汰为捕获内部步骤）
├─ data/
│  ├─ json/JsonClipStore.kt          # 原子写 + bak + schemaVersion 迁移
│  └─ repo/ClipRepositoryImpl.kt     # 内存快照 + StateFlow（SSOT）
├─ platform/AndroidClipboardGateway.kt   # ClipboardManager 封装（读 onResume / 写复制回）
└─ ui/
   ├─ theme/      ClipsTheme DesignTokens ClipMotion（05 的 token · Compose 版）
   ├─ components/ ClipRow SearchBar TypeFilterChips DayHeader EmptyState…
   ├─ history/    HistoryScreen( W3 ) EntrySheet( W4 )
   └─ settings/   SettingsScreen( W5 )
```

- 分层/依赖规则/SSOT/手动 DI 与 wardrobe 完全一致（照搬其 04 与 ADR-003/008 模式）。
- minSdk 26 / target 35；**manifest 不申请任何权限**（磁贴、分享接收均无需权限声明）。

## macOS 端（mac/，SwiftUI + AppKit）

```
Clips/
├─ App/
│  ├─ ClipsApp.swift            # MenuBarExtra 菜单栏常驻 + 快速面板/主窗口
│  └─ AppState.swift            # 组合根（手动装配，对应 Android 的 Application）
├─ Core/
│  ├─ Model/ClipEntry.swift     # Codable，字段与 03-data-model 一字不差
│  ├─ Store/ClipStore.swift     # clips.json 原子写 + bak + 迁移（对应 JsonClipStore）
│  ├─ Capture/PasteboardWatcher.swift   # Timer 轮询 NSPasteboard.general.changeCount（~0.8s）
│  ├─ Capture/TypeHint.swift    # 启发式（规则同 03 的 Swift 实现）
│  ├─ Hotkey/HotkeyCenter.swift # Carbon RegisterEventHotKey 注册 ⌘⇧V
│  ├─ Paste/AutoPaster.swift    # CGEvent 发送 ⌘V；辅助功能权限，失败降级仅复制
│  └─ Settings/AppSettings.swift # UserDefaults：容量/暂停/自动粘贴/开机自启（SMAppService）
└─ UI/
   ├─ PaletteView.swift         # W1 快速面板（搜索 + 键盘导航）
   ├─ MainView.swift            # W2 主窗口（管理：右键复制/置顶/删除、导出导入）
   ├─ SettingsView.swift        # W5
   └─ Components/ClipRow.swift…  # 05 的 token · SwiftUI 版
```

- 状态：`ClipStore` 内存快照 + `@Observable` 广播（SSOT，对应 Android 侧 StateFlow）。
- 平台机制：`changeCount` 轮询是业界通行做法（Maccy 同源思路）；开机自启 `SMAppService.mainApp`（macOS 13+）；打包方式（SPM 可执行 + 脚本裹 .app，或 Xcode 工程）在脚手架阶段定并回填此处。

## 双端对齐点（改动必须同步 specs）

| 契约 | 载体 |
|---|---|
| clips.json schema + TypeHint 判定 + 去重/淘汰不变量 | [03-data-model.md](03-data-model.md)；双端单测跑**同一组用例**，契约靠测试对齐 |
| 视觉 token / 相对时间规则 | [05-design-system.md](05-design-system.md)；Compose 与 SwiftUI 各实现一份 |
| 用户故事与验收 | [01-user-stories.md](01-user-stories.md) |

## 错误处理（两端同规则）

- 落盘失败：回滚内存快照 + 状态提示（沿用 wardrobe 模式）。
- 捕获异常（剪贴板占用/读取失败）：静默跳过本轮，下轮重试。
- 导入非法 JSON：完整拒绝并提示，不触碰现有数据。
- Mac 自动粘贴权限缺失/发送失败：降级为仅复制，提示一次。

## 测试策略

- Android：JVM 单测 `testDebugUnitTest`（去重/淘汰/截断/TypeHint/JsonClipStore 读写与损坏回退）。
- macOS：`swift test` 跑**同一组契约用例**（尤其 TypeHint 判定与去重/淘汰——双实现防漂移的关键）。
- 平台层（热键/托盘/CGEvent/磁贴/onResume 捕获）以本机/真机手动验证，矩阵回填 it-001。

## 构建配置

- Android：Kotlin 2.1.x + AGP 8.7.x + Gradle 8.9 + Compose BOM/material3（与 wardrobe 一致）
- macOS：Swift 5.9+ / macOS 13+（MenuBarExtra 与 SMAppService 的版本下限），SPM 工程
