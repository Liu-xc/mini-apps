# it-059 · W4 录入表单点选化 + 演示人物数据

> 状态：**已完成**（2026-09-29，Leo 体验包反馈，方向由 Leo 直接给出）
> 来源：「这个表单太长了，使用起来有压力，尽量用简单选择就可以完成的方式，把手动输入折叠到二次交互里展开。并且需要给 mock 版本里录入一份人物数据。」

## 背景与动机

W4 添加/编辑衣物表单现状：照片 → 名称（必填手打）→ 品类 chips → **颜色（纯文本框）** → **描述（纯文本框）** → **标签（手输 TagInput）**。七个区块里只有品类是点选，录入一件衣物至少手打三段字，一屏放不下需要滚动——录入压力大，是「拍照 → 三下点选 → 保存」理想路径上的主要阻力。

演示模式（mock）中 Leo/Mia 两个角色均未设置 it-017 形象参考照，参考照导出效果在演示模式下无法体验。

## 方案

### A · 表单点选优先（主区全点选，手打全折叠）

- **主区**（一屏内完成录入）：
  1. 照片（必填，不变）
  2. 名称：输入框保留但**不再必填**——留空保存时自动命名「`颜色+品类`」（如「米色上装」），placeholder 提示「留空自动命名」
  3. 品类 chips（不变）
  4. **颜色 chips（新）**：12 个预设（白/黑/灰/米/卡其/军绿/藏青/蓝/棕/红/黄/粉）单选；已有自由文本色（如「橙红」）以附加选中 chip 展示
  5. **常用标签 chips（新）**：10 个常用标签多选（通勤/休闲/运动/约会/度假/居家/简约/冬/早秋/夏）+ 已有自定义标签 chip；自定义标签输入折叠
- **折叠区**：「＋ 补充细节（描述 · 自定义颜色与标签）」TextButton，AnimatedVisibility（EditorialMotion.smooth 高度过渡）展开：描述文本框（会拼进生图文案）、自定义颜色输入、自定义标签 TagInput
- **保存条件**：`hasPhoto` 即可（名称自动兜底）

### B · 演示人物数据

- PIL 生成「人台（dress form）」风格参考图（黑白灰编辑风、竖版），入 `assets/mock/person-ref.png`
- `wardrobe.json` 的 Leo（p1）置 `refImageFile = "person-ref.png"`——导出面板「附形象参考照」开关在演示模式下直接可体验
- `MockWardrobeData.create()`（JVM 种子）P1 同步；`MOCK_ASSET_REVISION` bump 至 `it-059`（revision 门刷新旧安装的 mock-images）

## 验收标准

- 新增衣物全程点选可完成（拍照 → 品类 → 颜色 → 标签 → 保存），零打字；名称留空自动命名
- 旧数据兼容：已有自由文本颜色/自定义标签在 chips 区以附加 chip 正确回显、可清除；编辑旧衣物保存后字段不丢失
- 折叠区展开/收起为 smooth 高度过渡，遵循减弱动态降级；chips 触控 ≥44dp（it-033 基线）
- 演示模式：Leo 角色导出面板出现「附形象参考照」开关且默认开；长图预览含参考照拼贴
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿

## 影响范围

- **代码**：`ui/wardrobe/ItemEditScreen.kt`（主重构）、`data/mock/MockWardrobeData.kt`、`di/AppContainer.kt`（revision）、`assets/mock/wardrobe.json` + 新图
- **常青 spec**：`02-wireframes.md` W4 注记、`05-design-system.md`（若新增组件条目）、CHANGELOG×2
- **不涉及**：数据模型（颜色/标签仍为自由字符串存储）、保存链路、抠图流程

## 实施记录

- **表单重构**（`ItemEditScreen.kt`）：颜色 13 预设 chips 单选（白/黑/灰/米/卡其/军绿/藏青/蓝/浅蓝/棕/红/黄/粉），自由文本色以附加选中 chip（「橙红 ✕」）回显、点除；常用标签 10 预设 chips 多选 + 已选自定义 chip 回显；名称非必填（`nameTouched` 区分，留空保存 `effectiveName = 颜色+品类`，supportingText「留空将自动命名为『米色上装』」实时预告）；保存 blocker 只剩 `hasPhoto`（bottomBar 原因文案同步简化为「还差一张照片」）。
- **折叠区**：「补充细节」Surface 折叠行（surfaceVariant@34%+hairline），展开 `expandVertically(EditorialMotion.smooth())+fadeIn(120)`、收起对应 shrink+fadeOut(90)（it-051 折叠语言）；内含描述/自定义颜色/自定义标签 TagInput；有内容时折叠行尾显示「已填」。
- **演示人物数据**：PIL 绘制黑白灰人台参考图（768×1024 dress form）→ `assets/mock/person-ref.png`；`wardrobe.json` Leo 置 `refImageFile`；`MockWardrobeData.create()` P1 同步（JVM 种子一致性）；`MOCK_ASSET_REVISION` it-049→it-059（旧安装的 mock-images 刷新门）。
- **spec**：02-wireframes W4 线框与注记更新、05-design-system 新增「it-059 录入表单点选优先」节、CHANGELOG×2。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：**BUILD SUCCESSFUL，83 项单测全绿**（无回归；表单为纯 UI 无新增可测逻辑）。
- AVD 走查（演示模式，逐项 dump 验证）：
  - 新表单主区：名称（无必填标）→ 品类 → 颜色 chips（白色/米色…）→ 标签 chips（通勤/休闲…）→「补充细节」折叠行——布局符合设计。
  - 点选「米色」+ 默认品类 → 名称 supportingText 实时出现「留空将自动命名为『米色上装』」；点选「通勤」标签选中态正常。
  - 折叠行点击展开：「描述（会拼进生图文案）」等三组输入出现。
  - 底部保存 blocker：仅「还差一张照片」（名称空已不再阻塞）。
  - **参考照**：切 Leo 角色 → 导出面板出现「附形象参考照」开关（it-017 条件渲染）；长图预览像素检测：中轴深色像素占 94%（人台剪影拼贴入图）；Mia（未设参考照）不出现开关（it-017 既有行为）。
- 局限：保存链路的自动命名落库未端到端实测（演示模式无真实照片可选），由 `effectiveName` 单一表达式保证；旧衣物编辑回显（自由色/自定义标签附加 chip）走查覆盖「白色」预设路径，多色文本（如「红白拼色」）回显由同一 `extraColor` 分支覆盖。
- 体验包：`wardrobe-0.5.0-demo-20260929-it059.apk`（8765 端口 + 二维码分发）。
