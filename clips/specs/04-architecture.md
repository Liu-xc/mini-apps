# 04 · 技术架构

## 技术栈与模块结构

Compose Multiplatform：一个 `composeApp` Gradle 模块，三个 source set。

```
composeApp/src/
├─ commonMain/kotlin/com/leo/clips/
│  ├─ domain/
│  │  ├─ model/       ClipEntry TypeHint Source ClipData
│  │  ├─ repository/  ClipRepository(接口)
│  │  └─ usecase/     CaptureClip SearchClips CopyBack（淘汰为捕获内部步骤）
│  ├─ data/
│  │  ├─ json/JsonClipStore.kt       # clips.json 原子读写 + bak + schemaVersion 迁移
│  │  └─ repo/ClipRepositoryImpl.kt  # 内存快照 + StateFlow（SSOT）
│  ├─ platform/                       # 平台能力接口（common 定义，两端各给实现）
│  │  ├─ ClipboardGateway             # readCurrent(): String? / copy(text)
│  │  ├─ PlatformCloser               # 复制后退出（Android finish；Mac 空实现）
│  │  └─ AppSettingsStore             # 容量/暂停/自动粘贴 持久化
│  └─ ui/
│     ├─ theme/       ClipsTheme DesignTokens TypeBadge ClipMotion
│     ├─ components/  ClipRow SearchBar TypeFilterChips DayHeader EmptyState…
│     ├─ history/     HistoryContent( W1/W2/W3 共用的列表+搜索+过滤 )
│     └─ settings/    SettingsScreen( W5 )
├─ androidMain/kotlin/com/leo/clips/
│  ├─ MainActivity.kt             # onResume 捕获 + 挂 HistoryContent
│  ├─ CaptureTileService.kt       # 快捷设置磁贴 → 启动 MainActivity
│  ├─ ShareReceiverActivity.kt    # ACTION_SEND text/plain → 入库 → Toast → finish
│  └─ gateway/ AndroidClipboardGateway.kt
└─ desktopMain/kotlin/com/leo/clips/
   ├─ Main.kt                     # 装配组合根 + 窗口 + 托盘
   ├─ tray/TrayMenu.kt            # Tray 图标 + 菜单（暂停/主窗口/退出）
   ├─ hotkey/GlobalHotkey.kt      # jnativehook 注册 ⌘⇧V
   ├─ clipboard/AwtClipboardPoller.kt   # ~800ms 轮询 + 内容哈希判变更
   ├─ clipboard/AutoPaster.kt     # osascript 发送 ⌘V（异常→降级仅复制）
   └─ gateway/ DesktopClipboardGateway.kt
```

## 依赖规则（与 wardrobe 一致）

- `ui → domain ← data`，只能单向依赖；domain 纯 Kotlin、无平台依赖（JVM 可测）。
- 平台差异收敛在 `platform/` 接口后（门面模式）；UI 只见接口，组合根装配实现。
- 组合根：desktop = `Main.kt`，android = `Application` 子类；手动构造器注入，不引 DI 框架（同 wardrobe ADR-003）。

## 数据流（SSOT）

`ClipRepositoryImpl` 持内存快照，暴露 `StateFlow<List<ClipEntry>>`；任何写操作 = 改快照 → 原子落盘 → 流自动广播。三类捕获入口（Mac 轮询 / Android onResume / 分享）、复制回、置顶、删除、淘汰全部汇入同一 `CaptureClip`/变更入口，保证去重与淘汰不变量只有一份实现。

## 关键平台机制

| 端 | 机制 | 说明 |
|---|---|---|
| Mac | 剪贴板捕获 | AWT `Toolkit.getSystemClipboard()` 定时轮询（~800ms）+ 文本哈希比较；「暂停记录」时挂起入库（轮询继续，便于恢复后立即感知） |
| Mac | 全局热键 | `com.github.kwhat:jnativehook` 注册 ⌘⇧V；托盘左键等价 |
| Mac | 自动粘贴 | `osascript -e 'tell application "System Events" to keystroke "v" using command down'`；需「辅助功能」权限；异常捕获 → 仅复制 + 一次性提示 |
| Mac | 打包 | Compose Desktop `packageDmg` 产出 .app/.dmg；it-001 不做签名/公证 |
| Android | 捕获 | Android 10+ 仅焦点应用可读剪贴板 → `onResume` 读取（ADR-003）；**不申请**前台服务/无障碍/输入法 |
| Android | 快捷磁贴 | `TileService` `onClick` 启动 MainActivity（磁贴本身不读剪贴板） |
| Android | 分享入库 | manifest 注册 `ACTION_SEND text/plain`；不触碰剪贴板 |
| Android | 复制返回 | `ClipboardManager.setPrimaryClip` → 行内 ✓ → `finish()` 返回来源（仅当本次由外部进入） |

## 错误处理

- 落盘失败：回滚内存快照 + 状态条提示（沿用 wardrobe 模式）。
- 捕获异常（剪贴板被占用/读取失败）：静默跳过本轮，下轮重试。
- 导入非法 JSON：完整拒绝并提示，不触碰现有数据。
- 自动粘贴权限缺失：降级为仅复制，Toast/状态条提示一次，不反复打扰。

## 测试策略

- commonMain 纯 JVM 单测（`desktopTest`）：去重复用、淘汰（含置顶豁免/上限变更）、10k 截断、TypeHint 启发式边界、JsonClipStore 读写/损坏回退 bak/迁移。
- 平台层（热键、托盘、osascript、磁贴、onResume 捕获）以本机/真机手动验证，矩阵回填 it-001 验证记录。

## 构建配置

- Kotlin 2.1.x + Compose Multiplatform 插件（脚手架阶段锁定当前稳定版，版本记入 ADR-001 修订）；AGP 8.7.x + Gradle 8.9（与 wardrobe 对齐，本机已具备）。
- Android：minSdk 26 / target 35；权限仅 `ACCESS_NETWORK_STATE` 不需要——**manifest 不申请任何权限**（磁贴属于 Quick Settings 无需权限声明）。桌面：macOS 13+，JDK 17。
