# it-001 · MVP

- **状态**：进行中
- **提案日期**：2026-09-19
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

（实现完成后回填：构建结果、单测结果、模拟器截图清单与结论、遗留问题）

## 遗留 / 后续迭代候选

- 备份导入（it-001 仅提供数据结构与导出能力的一部分）
- Compose UI 自动化测试
- 背景抠图、云同步、桌面端（见 00-overview 范围外清单）
