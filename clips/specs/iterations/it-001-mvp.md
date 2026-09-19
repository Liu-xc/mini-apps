# it-001 · 双端 MVP

- **状态**：提案（待用户确认，未开工）
- **提案日期**：2026-09-20（同日修订：按用户意见由 Compose Multiplatform 改为**双端分开原生实现**，见 ADR-001）
- **范围**：Mac + Android 双端各自可用闭环——捕获 → 历史库 → 呼出检索 → 复制回，含置顶/淘汰/类型徽章/设置/备份

## 背景与动机

新应用首个迭代，目标见 [../00-overview.md](../00-overview.md)：系统剪贴板只留最后一条，在 Mac（菜单栏常驻、全局热键、自动粘贴）与 Android（磁贴 + 打开即捕获 + 分享入库）各做一个轻量本地历史库。

技术路线：双端分开原生、功能按平台取舍（ADR-001）；Android 与 wardrobe 同栈同构（模式照搬，风险低）；macOS 用 SwiftUI 从零搭菜单栏应用。跨端只共享规格与 `clips.json` 契约（ADR-009）。

## 用户故事

US-01 ~ US-12 全量（见 [../01-user-stories.md](../01-user-stories.md)）；NFR-01/02/04 达标，NFR-03/05 基础达标。

**可滑出**（周期吃紧时顺延至 it-002，不阻塞交付）：US-12 备份导入导出；Mac「自动粘贴」（降级路径=仅复制，天然可滑）。

## 实施步骤（两条独立轨道，Android 先行、Mac 随后，可交错）

1. ✅ specs 固化（本提案 + 00–06 常青文档，含双端分工修订）

**Android 轨道（android/）**
2. 脚手架：复用 wardrobe 工程模板（AGP/Kotlin/镜像配置），空 App 可安装运行
3. domain + data：实体 / ClipRepository / JsonClipStore（去重、淘汰、截断、TypeHint 启发式）+ JVM 单测——**契约用例第一份在这里落地**（用例清单同时写进本文件供 Mac 对齐）
4. UI + 平台通道：W3 列表 / W4 操作单 / W5 设置；onResume 捕获、磁贴、分享入库、复制返回、滑动删除

**Mac 轨道（mac/）**
5. 脚手架：SPM 工程 + MenuBarExtra 菜单栏常驻空跑 + 设置骨架；打包方式定稿并回填 04
6. Core：ClipStore / PasteboardWatcher / TypeHint + `swift test` 对齐 Android 的契约用例
7. UI 与集成：W1 快速面板（⌘⇧V + 键盘导航）、W2 托盘与主窗口、暂停态、开机自启、自动粘贴（可降级，可滑）

**收尾**
8. 双端设置完备 + 备份导出/导入（可滑）
9. 动效与深色模式对照 [../05-design-system.md](../05-design-system.md) 清单过一遍
10. 双端验证（矩阵见下）→ 验证记录回填 → CHANGELOG → 交付（APK + .app）

## 验收标准

各 US 的 Given/When/Then 见 [../01-user-stories.md](../01-user-stories.md)。整体出口条件：

- Android：`./gradlew assembleDebug` 与 `testDebugUnitTest` 通过；manifest 无任何权限声明（NFR-01）
- Mac：`swift build` 与 `swift test` 通过
- **契约对齐**：同一组契约用例双端全绿；Android 导出 clips.json → Mac 导入成功，反向亦然（US-12 若滑出则此条顺延）
- Mac 本机实测：复制→面板即时可见；⌘⇧V→搜索→⏎ 复制回；暂停记录生效；开机自启生效（US-01/04/11）
- Android 实测（模拟器或真机）：复制→磁贴打开→自动入库；点条目复制并返回；分享菜单入库；淘汰与置顶正确（US-02/03/05/06/08/10）
- specs 与代码一致（铁律②）

## 验证记录（待回填）

计划：
- 构建产物：APK + .app（或可运行 SPM 产物）+ 双端单测报告
- 契约互通：双向导出/导入各一次，字段与去重结果核对
- Mac 手动矩阵：US-01（轮询/去重/截断/空白忽略/暂停）、US-04（呼出/过滤/键盘/自动粘贴降级）、US-11（开机自启/暂停图标态）
- Android 模拟器截图矩阵：W3/W4/W5 对照线框；磁贴与分享入库以步骤描述 + 截图记录
- 数据核验：`run-as cat clips.json` 抽查去重/淘汰/置顶/useCount 字段
