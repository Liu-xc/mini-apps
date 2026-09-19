# it-001 · MVP

- **状态**：已完成
- **提案日期**：2026-09-20
- **范围**：首个可用版本，覆盖全部核心故事（US-01 ~ US-08）

## 背景与动机

全新应用的首个迭代，目标见 [../00-overview.md](../00-overview.md)：录入食堂（堂食/外卖/自做）→ 记一笔 → 转盘决策 → 地图/列表/详情回看。设计基线为 [../02-wireframes.md](../02-wireframes.md)（提案稿，确认后定稿）。

技术方案：与 wardrobe 同栈（安卓 Compose + JSON 原子存储），地图引入 osmdroid（ADR-002），见 [../06-decisions.md](../06-decisions.md)。

## 用户故事

US-01 ~ US-08 全部（见 [../01-user-stories.md](../01-user-stories.md)）；NFR-01/02/04 本迭代达标，NFR-03 深色模式做基础适配。

## 实施步骤

1. ✅ specs 骨架 + 本提案
2. ✅ Gradle 脚手架（构建配置对齐 wardrobe 基线），`./gradlew assembleDebug` 通过
3. ✅ 主题与通用组件（DesignTokens / RatingStars / TagChipInput / PhotoStrip / EmptyState / LinkChips）
4. ✅ 数据层：实体 + Repository + JSON 原子存储 + 图片压缩（+ JVM 单测）
5. ✅ 列表页 W3 + 表单 W4（Photo Picker、标签、评分、链接粘贴与来源识别）
6. ✅ 地图页 W2（osmdroid 集成、类型 marker、底部摘要卡、未上地图入口）+ W4 位置长按选点
7. ✅ 详情页 W5 + 记一笔弹层 W6（Visit 时间线、派生统计、链接跳转；W1 结果卡链接直达）
8. ✅ 决策页 W1：WheelCanvas 转盘动画 + BuildCandidates 过滤 + SpinWheel 权重 + 「就吃这个」落账
9. ✅ 动效打磨（转盘 / 结果卡 / 摘要卡 / 列表入场 / 空态）
10. ✅ 模拟器验证（截图对照线框）+ 演示数据脚本 `eats/tools/demo-data.sh`
11. ✅ 收尾：验证记录回填、CHANGELOG、APK 交付

## 验收标准（汇总）

见 01-user-stories.md 各 US 的 Given/When/Then；整体出口条件：
- `./gradlew assembleDebug` 与 `./gradlew test` 通过
- 模拟器逐页截图与 W1–W6 线框语义一致，US-01/03/04/05/06/07/08 交互实测通过
- specs 与代码一致

## 验证记录

**构建与单测**（2026-09-20，macOS / JDK17 / AGP 8.7.3 / Kotlin 2.1.21）：

- `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL（app-debug.apk ~20MB，`eats/app/build/outputs/apk/debug/`）
- `./gradlew test` ✅ 30 个 JVM 单测全部通过、0 失败
  - JsonFileStoreTest（4）：roundtrip / 损坏回退 bak / 首存补 bak / 空目录
  - EatsRepositoryImplTest（7）：链接去重归一化 / 级联删除（Visit+照片）/ 单删 Visit / 照片差集删除 / 悬空引用清洗 / id 补齐 / StateFlow 广播
  - BuildCandidatesTest（5）：类型过滤 / 忌口排除 / 最近排除开关 / 边界（恰好 N 天）
  - SpinWheelTest（5）：权重公式 / 分布（从未吃过胜率 >93%）/ plan 参数区间 / 全候选覆盖 / 空候选异常
  - LinkSourceTest（5）+ QueriesTest（4）：来源识别（美团/点评/其他/大小写）/ 派生统计

**模拟器交互实测**（emulator，8 家演示食堂 + 11 条 Visit，`tools/demo-data.sh` 注入）：

| US | 验收点 | 结果 |
|---|---|---|
| US-01 | FAB→W4 表单：名称/类型/菜系/评分/标签/照片/笔记/链接粘贴（美团链接自动识别 chip「美团·milktea」）/地图长按选点（人民广场实测坐标回填「已选点 (31.2308, 121.4700)」）| ✅ |
| US-02 | 编辑回填 / 删除二次确认 | ✅ |
| US-03 | W6 记一笔（评分/花费/感想/照片/时间默认现在）→ 落账后详情「上次 今天 · 共 4 次」即时更新 | ✅ |
| US-04 | 地图瓦片正常（高德）、marker 按类型着色、「⌖ 2 条未上地图」入口、点 marker 滑出摘要卡 | ✅ |
| US-05 | 列表默认按最近一次吃倒序、排序菜单（最近/评分/次数/名称）、类型+标签筛选、搜索 | ✅ |
| US-06 | 详情：派生统计（上次/次数/均分 4.3）、地址+在地图上看（聚焦 17 级+摘要卡联动）、照片条、Visit 时间线倒序可删 | ✅ |
| US-07 | 转盘：类型/忌口/排除最近过滤 → 加权抽取 → 结果卡（就吃这个→W6 落账 / 重转排除刚中项）全链路 | ✅ |
| US-08 | W4 粘贴识别、W5 链接 chip 跳转（ACTION_VIEW）、W1 结果卡链接直达 | ✅（跳转拉起外部 App 需真机复验） |

**截图对照线框**（`specs/iterations/assets-it-001/`，8 张）：w1-spin / w1-result / w2-map / w3-list / w4-edit / w4-picker / w5-detail / w6-log-visit，逐页与 02-wireframes 语义一致（转盘 8 扇区三色着色、摘要卡、结果卡布局均核对）。

**实现过程中的关键发现（已回写 spec）**：

1. **ADR-002 修订**：OSM 官方瓦片在国内网络完全不可达（实测连接超时），切换为高德公开栅格瓦片（免 Key、中文注记、GCJ-02 自洽口径见 ADR 后果）。
2. **BottomSheet 内 AndroidView 手势失效**（实测）：选点弹层中的 osmdroid MapView 收不到任何触摸；长按检测改放包装 FrameLayout 的 `dispatchTouchEvent` 层（纯 View 体系），经 `MapView.projection` 换算经纬度。记录于 LocationPickerSheet 头注释。
3. **osmdroid 生命周期**：弹层中途打开时宿主已处于 RESUMED，`LifecycleEventObserver` 等不到事件，需立即补 `onResume()` 否则瓦片线程不启动。

**遗留问题**：

- 链接跳转（ACTION_VIEW 拉起美团/点评 App）在模拟器无目标 App，仅验证了 Intent 发出无崩溃；需真机复验 App Links 行为。
- 地址文本为手填（ADR-005 无逆地理编码）；转盘结果卡在候选较多时需要小滚，可优化为结果卡出现时自动滚动定位。

## 遗留 / 后续迭代候选

- 统计页（消费趋势 / 类型分布 / 高频食堂）
- 备份导出/导入（zip）
- POI 检索选点、逆地理编码（需在线服务，另行 ADR）
- Compose UI 自动化测试
