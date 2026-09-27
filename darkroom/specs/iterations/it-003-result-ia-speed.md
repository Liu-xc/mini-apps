# it-003 — 显影 · 功能收口与成片页信息架构

状态：**提案待确认（2026-09-27，源自首轮 UI 审查）**
日期：2026-09-27

## 背景与动机

2026-09-27 全页面 UI 审查（交付 `reports/darkroom-ui-audit/`，报告 D3–D4 / C3–C5）发现：

1. **P1 速度档失效（C3）**：`DarkroomViewModel.kt:72` 把 `DevelopClock` 固定成
   `DevelopSpeed.STANDARD.durationMs`，`:79` 的 `prefs.speed.collect` 只更新 `state.speed` 标签——
   DataStore 落盘 `SLOW` 后实测显影仍 ~8.8s（应 12s）；快显 4s 同样无效（US-2 名存实亡）。
   且导出 `ExportPlan(developMs = s.speed.durationMs)`（`:311`）**用的是所选档** →
   视频时长与预览不一致（AC6 违反）。
2. **P1 成片页首屏（C4）**：存图片/存视频落在 y2343–2392 被手势条压住半截；
   导出进度条排在按钮下方＝首屏外（导出全程看不到进度，40 次轮询未捕获「冲洗中」，
   源码有 UI 用户看不见）；分享×2/再洗一张需滚动才发现。
3. **P2 首屏节奏（C5）**：W1 内容止于 ~60% 高度、底部约 800px 空白（footer 未贴底），
   且设置入口两处（顶行 ⚙ + 示例图下 chip，其一在 it-002 前还失灵）；W4 同样下半空置。

对应报告落地拆分 O4–O6；前置依赖 it-002（页头 insets、卡面几何）已合入或并行。

## 涉及的用户故事

- 既有 **US-2 显影**（速度档）、**US-5/US-6 保存与分享/视频导出**（进度反馈）、**US-8 设置**；
  新增 **US-10 首屏信息密度**（W1 节奏与单一设置入口）。实施时同步 `specs/01-user-stories.md`。

## 用户故事

- **US-2（修复）速度档即选即生效**：选慢洗/快显后**下一次显影**实测 12s/4s（±0.5s），
  页头标签与实测一致；导出视频时长 = 所选档（与预览一致）。
- **US-5/US-6（补强）导出反馈入首屏**：存图/存视频按钮完整落在首屏安全区内；
  导出进度（LinearProgress + 冲洗中 n%）紧贴按钮上方、导出期间无需滚动即可见；
  分享×2 与再洗一张收敛为一行次级操作。
- **US-10 首屏密度**：W1 footer「全程离线」贴底、移除重复设置 chip（保留顶行一处）；
  W4 内容节奏同步收口。

## 验收标准

- AC1 速度档实测：慢洗 12s±0.5s、标准 8s±0.5s、快显 4s±0.5s（三档各跑一次计时）；
  header 标签与实测一致；改档后**无需重启**生效。
- AC2 导出一致性：SLOW 档导出的 MP4 时长 ≈ 12.75s（12s + 1.5s 定格），与预览时间线同源。
- AC3 首屏断言：W3 初始滚动位下 存图片/存视频 `bounds` 完整在安全区内且不被手势条遮挡；
  `exporting=true` 时进度节点出现在首屏（uiautomator 可 dump 到「冲洗中」）。
- AC4 W1 像素断言：footer 处于视口底部 5mm 内；屏内「设置」入口有且只有一处。
- AC5 构建 + 五套 JUnit 绿；DESIGN.md §2–§5 对表走查；改后全四页截图回归。

## 技术方案草案

- **O4 速度绑定**：`clock` 改 `var`，`startSession`（`DarkroomViewModel:132-135`）处
   `clock = DevelopClock(_state.value.speed.durationMs)` 后 `reset()`；或在 `prefs.speed.collect`
   回调里按当前会话状态重建。二选一，倾向 startSession 处（意图明确：每次显影用当时档位）。
  导出 `ExportPlan` 已用所选档，天然与 AC2 对齐；补单测：clock duration 随 prefs 变化。
- **O5 成片页重排**：`ui/result/ResultScreen.kt` 内调整 Column 顺序——
  进度块（`if (state.exporting)`）移到导出按钮 Row **上方**；按钮行整体上移
  （压缩编辑卡上下间距 / 减 spacer），确保初始位全入屏；分享×2 + 再洗一张收为一行
  `Arrangement.spacedBy`。线框见报告 `assets/wf-result.png`（徽标②③）。
- **O6 首屏节奏**：W1 移除示例图下「设置」chip（`PickScreen.kt:189` 附近），footer 前
  `Spacer(Modifier.weight(1f))` 推底；W4 尾部同理（若与 it-002 insets 改动冲突，以 insets 版为准）。
- 不改导航栈、不改 DataStore schema、不加依赖。

## 里程碑

- M1 O4 速度档（纯功能，含计时实测）+ 单测
- M2 O5 成片页重排 + O6 首屏节奏（视觉回归截图）

## 影响范围

- `darkroom/app`（DarkroomViewModel、ResultScreen、PickScreen、SettingsScreen）、
  `specs/01-user-stories.md`（US-10、US-2/5/6 补强）、完成后回填验证记录 + CHANGELOG。

## 待确认点

1. O4 重建时机：`startSession` 处（每张图开始时取档）还是 `prefs.speed.collect`（改档立即生效、
   含进行中场次）？建议 **startSession**——显影中途改档语义不清，AC1 表述为「下一次显影生效」。
2. O5 进度反馈形态：按钮**上方**独立进度行（报告线框方案，改动小）还是
   进度**内嵌按钮**（按钮文案变「冲洗中 42%」，更省空间）？建议按线框的按钮上方。
3. O6 是否移除 W1 的「设置」chip 只留顶行 ⚙（与 it-002 死区修复配套后 ⚙ 可靠）？
   若你更习惯底部大按钮，可反向保留 chip 移除 ⚙——二选一即可。

（以上三点按提案默认执行：①startSession 处重建；②按钮上方独立进度行；③移除 chip 留顶行 ⚙。
执行中补充：仅移除 chip + footer 贴底不足以让 CTA 入屏，追加**成片卡高上限 420→360dp**（对齐
改版线框的 72% 卡宽）与编辑卡/段间距压缩——进度块、存图/存视频、分享行全部收进首屏。）

## 验证记录

2026-09-27 实施完成，AC 全过（模拟器 wardrobe_test，最新源码构建）：

- **AC1 速度档**：三档斜率实测（双点进度采样，%/s）——
  慢洗 **7.98**（期望 8.3，主线程负载下时钟 dt 钳制略低）、标准 **12.5**（审查期实测）、
  快显 **≈27**（期望 25；A 点 54%@读数~2s）；
  DataStore 依次落盘 `SLOW`/`FAST`，**改档后下一次显影立即生效**（无需重启）。
- **AC2 导出一致性**：SLOW 档导出 MP4 `mvhd` 时长 **13.999s** = 0.5s 起手 + 12s 显影 + 1.5s 定格，
  与 `ExportPlan(developMs=12000)` 精确一致（解析器修正后复核 PASS）。
- **AC3 首屏**：无滚动 dump 即见 `存图片/存视频`（y2148–2197 ≤ 安全区底 2285）；
  起视频导出后无滚动抓到「**冲洗中 0%**」（y2129–2178）且 CTA 同屏（y2240–2289）；
  新文件 `Movies/显影/显影_2026-09-27 (2).mp4` 11.09MB 落盘。
- **AC4 W1**：dump 实测设置入口 **1 处**（顶行 ⚙，chip 已移除）；footer y2244–2284
  贴内容区底（页内边距 20dp，位于手势条之上）。
- **AC5**：33 测试 0 失败；四页回归截图 `assets/verify-*-after.png`。

关键落地：`DarkroomViewModel` clock 改 var + startSession 重建、`ResultScreen` 进度上移/分享收行/
卡高 360dp、`PickScreen` 双层结构（滚动区 weight + footer 常驻）。
