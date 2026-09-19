# it-001 · MVP

- **状态**：待确认（提案）
- **提案日期**：2026-09-20
- **范围**：首个可用版本，覆盖全部核心故事（US-01 ~ US-07）

## 背景与动机

全新应用的首个迭代，目标见 [../00-overview.md](../00-overview.md)：录入食堂（堂食/外卖/自做）→ 记一笔 → 转盘决策 → 地图/列表/详情回看。设计基线为 [../02-wireframes.md](../02-wireframes.md)（提案稿，确认后定稿）。

技术方案：与 wardrobe 同栈（安卓 Compose + JSON 原子存储），地图引入 osmdroid（ADR-002），见 [../06-decisions.md](../06-decisions.md)。

## 用户故事

US-01 ~ US-07 全部（见 [../01-user-stories.md](../01-user-stories.md)）；NFR-01/02/04 本迭代达标，NFR-03 深色模式做基础适配。

## 实施步骤

1. ✅ specs 骨架 + 本提案（用户确认后状态转「进行中」）
2. Gradle 脚手架（构建配置对齐 wardrobe 基线），`./gradlew assembleDebug` 通过
3. 主题与通用组件（DesignTokens / RatingStars / TagChipInput / PhotoStrip / EmptyState）
4. 数据层：实体 + Repository + JSON 原子存储 + 图片压缩（+ JVM 单测）
5. 列表页 W3 + 表单 W4（Photo Picker、标签、评分）
6. 地图页 W2（osmdroid 集成、类型 marker、底部摘要卡、未上地图入口）+ W4 位置长按选点
7. 详情页 W5 + 记一笔弹层 W6（Visit 时间线、派生统计）
8. 决策页 W1：WheelCanvas 转盘动画 + BuildCandidates 过滤 + SpinWheel 权重 + 「就吃这个」落账
9. 动效打磨（转盘 / 结果卡 / 摘要卡 / 列表入场 / 空态）
10. 模拟器验证（截图对照线框）+ 演示数据脚本 `eats/tools/`
11. 收尾：验证记录回填、CHANGELOG、APK 交付

## 验收标准（汇总）

见 01-user-stories.md 各 US 的 Given/When/Then；整体出口条件：
- `./gradlew assembleDebug` 与 `./gradlew test` 通过
- 模拟器逐页截图与 W1–W6 线框语义一致，US-01/03/04/05/06/07 交互实测通过
- specs 与代码一致

## 验证记录

（实现完成后回填：构建结果、单测结果、模拟器截图清单与结论、遗留问题）

## 遗留 / 后续迭代候选

- 统计页（消费趋势 / 类型分布 / 高频食堂）
- 备份导出/导入（zip）
- POI 检索选点、逆地理编码（需在线服务，另行 ADR）
- Compose UI 自动化测试
