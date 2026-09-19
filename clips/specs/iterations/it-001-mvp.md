# it-001 · 双端 MVP

- **状态**：提案（待用户确认，未开工）
- **提案日期**：2026-09-20
- **范围**：Mac + Android 双端可用闭环——捕获 → 历史库 → 呼出检索 → 复制回，含置顶/淘汰/类型徽章/设置/备份

## 背景与动机

新应用首个迭代，目标见 [../00-overview.md](../00-overview.md)：系统剪贴板只留最后一条，在 Mac（菜单栏常驻、全局热键）与 Android（磁贴 + 打开即捕获）各做一个轻量本地历史库。

两个技术基线首次落地：Compose Multiplatform 双端共享（ADR-001）；剪贴板捕获的平台差异设计（Mac 轮询 vs Android onResume，ADR-003）。存储沿用 wardrobe 验证过的 JSON 原子模式（ADR-004）。

## 用户故事

US-01 ~ US-12 全量（见 [../01-user-stories.md](../01-user-stories.md)）；NFR-01/02/04 达标，NFR-03/05 基础达标。

**可滑出**（周期吃紧时顺延至 it-002，不阻塞交付）：US-12 备份导入导出；Mac「自动粘贴」（降级路径=仅复制，天然可滑）。

## 实施步骤

1. ✅ specs 固化（本提案，含 00–06 常青文档）
2. CMP 脚手架：`composeApp` 三 source set；`assembleDebug` 与 `run` 双端空跑通过；版本锁定回填 ADR-001
3. domain + data：实体 / ClipRepository / JsonClipStore（去重、淘汰、截断、TypeHint 启发式）+ JVM 单测
4. 共享 UI：主题与组件 + 列表/搜索/类型过滤/置顶分组 + 设置页（W5）
5. Android 端：onResume 捕获、磁贴、分享入库、复制返回、滑动删除（W3/W4）
6. Mac 端：托盘、轮询捕获、⌘⇧V 热键、快速面板键盘导航（W1/W2）
7. Mac 打磨：自动粘贴（osascript + 降级）、暂停记录图标态
8. 备份导出/导入 + 设置完备（可滑）
9. 动效与深色模式对照 [../05-design-system.md](../05-design-system.md) 清单过一遍
10. 双端验证（矩阵见下）→ 验证记录回填 → CHANGELOG → dmg/APK 交付

## 验收标准

各 US 的 Given/When/Then 见 [../01-user-stories.md](../01-user-stories.md)。整体出口条件：

- `./gradlew :composeApp:assembleDebug` 与 `:composeApp:desktopTest` 通过；JVM 单测覆盖去重/淘汰/截断/启发式/存储
- Mac 本机实测：复制→托盘面板即时可见；⌘⇧V→搜索→⏎ 复制回可用；暂停记录生效（US-01/04/11）
- Android 实测（模拟器或真机）：复制→磁贴打开→自动入库；点条目复制并返回；分享菜单入库；淘汰与置顶正确（US-02/03/05/06/08/10）
- AndroidManifest 无任何权限声明（构建产物核验，NFR-01）
- specs 与代码一致（铁律②）

## 验证记录（待回填）

计划：
- 构建产物：APK + dmg + 单测报告
- Mac 手动矩阵：US-01（含去重/截断/空白忽略/暂停）逐条、US-04（呼出/过滤/键盘/降级）、托盘菜单
- Android 模拟器截图矩阵：W3/W4/W5 对照线框；磁贴与分享入库以步骤描述 + 截图记录
- 数据核验：`run-as cat clips.json` 抽查去重/淘汰/置顶/useCount 字段
