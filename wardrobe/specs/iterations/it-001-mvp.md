# it-001 · MVP

- **状态**：已完成（模拟器验证通过，待真机验收）
- **提案日期**：2026-09-19
- **完成日期**：2026-09-19
- **范围**：首个可用版本，覆盖全部核心故事（US-01 ~ US-14）

## 背景与动机

全新应用的首个迭代。目标见 [00-overview.md](../00-overview.md)：录入衣物 → 滑动组合 → 复制素材给生图 Agent → 成品图回录沉淀，多角色 + 标签评论。设计基线为已与用户确认的线框（[02-wireframes.md](../02-wireframes.md)）。

## 用户故事

US-01 ~ US-14 全部（见 [01-user-stories.md](../01-user-stories.md)），NFR-01/02/04 本迭代达标，NFR-03 深色模式做基础适配。

## 实施步骤

1. ✅ 仓库骨架 + specs 文档固化（本目录）
2. Gradle 脚手架，`./gradlew assembleDebug` 通过
3. 设计系统：Theme/Typography/MotionScheme + 通用组件
4. 数据层：实体 + Repository + JSON 原子存储 + 图片管理（+ JVM 单测）
5. 角色体系：默认角色、切换、管理、三 Tab 过滤
6. 衣橱管理：W3 列表 + W4 表单 + Photo Picker
7. 搭配页 W1：槽位轮播 + 老虎机 + 组合记忆
8. 导出：合成图 + 文案 + 复制/分享 + W6 面板
9. 穿搭体系：收藏 + W8 + W7 + 成品图录入 + W5 双向关联
10. 标注体系：TagChip + 评论时间线 + 标签进文案与筛选
11. 动画打磨：共享元素 / 彩屑 / staggered / Lottie
12. 模拟器验证（截图对照线框）+ 演示数据
13. 收尾：验证记录回填、CHANGELOG、APK 交付

## 验收标准（汇总）

见 01-user-stories.md 各 US 的 Given/When/Then；整体出口条件：
- `./gradlew assembleDebug` 与 `./gradlew test` 通过
- 模拟器逐页截图与 W1–W8 线框语义一致，US-04/05/07/09/11/12/13 交互实测通过
- specs 与代码一致

## 验证记录

**环境**：macOS 命令行构建（Gradle 8.9 / AGP 8.7.3 / Kotlin 2.1.21 / material3 1.4.0 / compileSdk 35）+ Android 14 (API 34) arm64 模拟器（Pixel 6 画像），演示数据（10 件单品色块图、2 套穿搭、3 条评论）经 run-as 注入。

**构建与测试**
- `./gradlew assembleDebug` ✅（APK ≈ 24.7MB，`app/build/outputs/apk/debug/app-debug.apk`）
- `./gradlew testDebugUnitTest` ✅ 15/15（JsonFileStore 3 + Repository 不变量 8 + Prompt 4）

**模拟器实测矩阵**（截图见 [it-001-assets/](it-001-assets/)）

| 项 | 结果 | 证据 |
|---|---|---|
| W1 搭配页布局/轮播/序号/底部栏 | ✅ | 01_outfit_w1.png |
| 槽位滑动换装（上装 1/2→2/2，白衬衫→黑T） | ✅ | adb 滑动前后 dump |
| W2 角色面板（列表/当前标记/新建/管理） | ✅ | 09_person_sheet_w2.png |
| W3 衣橱（分组计数/标签/筛选条/FAB） | ✅ | 10_wardrobe_w3.png |
| W6 导出：合成图（2×4 格+品类标注） | ✅ | 05_export_w6.png |
| W6 自动文案（8 单品+风格标签可编辑） | ✅ | UI dump 全文核对 |
| 复制按钮 morph「已复制 ✓」 | ✅ | dump 确认按钮文案变化 |
| ☆收藏 → wardrobe.json 落盘（第 3 套，8 单品 3 标签） | ✅ | run-as cat 数据核对 |
| W8 记录网格（成品图优先/2×2 拼贴占位/排序） | ✅ | 04/14 截图 |
| W7 穿搭详情（成品图/标签/单品列表/评论） | ✅ | 13_outfit_detail_w7.png |
| W5 衣物详情反查（黑T → 09/20 穿搭） | ✅ | dump 确认 |
| 评论提交（0→1，时间线显示） | ✅ | 12_comment_added.png |
| 组合记忆（滑动→重启→保持 2/2） | ✅ | 修复后实测 |
| 深色模式基础适配 | ✅ | 15_dark_w1.png |

**过程中发现并修复的问题**
1. **组合记忆竞态（bugfix）**：DataStore 首值未就绪时 pager 以 page0 初始化，翻页持久化先把 page0 写回，覆盖了记忆。修复：恢复（等待首值 → `scrollToPage`）在持久化收集之前顺序执行。
2. Kotlin 块注释嵌套陷阱：注释文本含 `images/*.webp`，`/*` 开启嵌套注释导致「Unclosed comment」——glob 写法已从注释中清除。
3. material3 1.4.0 稳定版 Expressive API 仍为 internal（ADR-004 已改判），空状态动画由 Lottie 改为自绘 Canvas 衣架摇摆（观感等价、零资产依赖）。

**遗留（真机验收项 / 后续迭代）**
- [ ] 真机：Photo Picker 实际选图导入（W4）、成品图录入（W6/W7）、剪贴板图片粘贴到生图 Agent、分享面板
- [ ] 真机：返回键关闭 ModalBottomSheet（模拟器 keyevent 未生效，遮罩点击可关）
- [ ] 观感：W8 同行两卡因日期/标签行数差异仍可能轻微不齐（拼贴高度已按 0.86 对齐）
- [ ] 共享元素过渡（W1→W5）在慢速模拟器上未目验动画过程，仅确认导航正确
- [ ] Compose UI 自动化测试（本次为手动/adb 驱动）
- [ ] 备份导入功能（导出结构已定，UI 未做）

## 遗留 / 后续迭代候选

- 备份导入（it-001 仅提供数据结构与导出能力的一部分）
- Compose UI 自动化测试
- 背景抠图、云同步、桌面端（见 00-overview 范围外清单）
