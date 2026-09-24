# it-036 · 组件一致性收尾（chips/分段/导出弹层/心愿占位/文案格式）

- **状态**：已实施（2026-09-24）
- **来源**：[2026-09-24 UI 走查报告](../../../reports/2026-09-24-wardrobe-ui-audit/衣橱UI走查报告-2026-09-24.pdf) · it-036 提案
- **关联**：US-36

## 背景与动机

走查 C8/C10/C11/C12：chips 同排三种形制且行尾硬裁无渐隐；W6 导出弹层文案区被
动作栏拦腰裁切、拼贴标签硬截、两个输入框两套标签语言；W10 无图心愿卡占位空洞、
域名与价格中文同行混排；分段控件回顾页与心愿页两套规格；「随机翻一套/随机一套」
与日期 09/24、2026/09/24 两套格式；混入心愿开启后愿望件不在当前页时无线索。

## 用户故事

- **US-36a**：作为用户，全 App 的筛选 chips、分段控件长得一样，行尾有渐隐提示
  可滑动。
- **US-36b**：作为用户，导出弹层的文案完整可读不被裁切，长图标签不截断。
- **US-36c**：作为用户，心愿页无图卡不再空洞，文案与日期格式全 App 统一；
  开启混入心愿后我能立刻知道愿望件已附加。

## 验收标准

- W3/W8/W10 筛选 chips 行尾渐隐遮罩；W10 分段改与 W9 同规格连体控件。
- W6：文案区块与动作栏之间安全间距（内容可滚至完整）+ 底部渐隐；
  拼贴标签 ellipsis + 内边距；两输入框统一占位符式。
- W10：无图心愿卡左侧品类色块占位；域名弱化到次级行。
- 「随机翻一套」→「随机一套」；评论日期与顶栏统一 `YYYY/MM/DD`。
- 混入心愿开启且愿望件未在当前页时，顶栏开关旁/受影响槽位有 accent 提示。
- `./gradlew assembleDebug testDebugUnitTest` 通过。

## 影响范围

`ui/wardrobe/WardrobeScreen.kt`、`ui/records/RecordsScreen.kt`、
`ui/wishlist/WishlistScreen.kt`、`ui/recap/WardrobeRecapScreen.kt`（分段）、
`ui/outfit/OutfitScreen.kt`、导出弹层相关（OutfitScreen 内 W6 面板/预览标签）、
`ui/components/CommentTimeline.kt`；spec 02 增交互注记、spec 05 增触控基线。

## 验证记录（2026-09-24）

- `./gradlew compileDebugKotlin` PASS；`./gradlew testDebugUnitTest assembleDebug`
  独立复跑 BUILD SUCCESSFUL，单测 62 项 0 失败。
- 装机实测（emulator-5554 演示模式，dump/像素/截图三通道）：
  - W6 导出弹层：滚到底文案框完整可见（bounds 证）、两输入框统一占位符式、
    长图标签 ellipsis + 8dp 内边距；内容列底距 40dp（=24dp 渐隐净空 + 16dp 动作栏余量，
    见偏差说明）；
  - W3/W8/W10 筛选行右缘 28dp 渐隐（像素扫描），仅可滑时出现、到尽头隐去，筛选钮在渐隐外；
  - W9「今年/累计」原样抽出为 `SegmentedToggleRow`，W9 与 W10 两页共用同款，
    W10 双 FilterChip 伪分段废止；
  - W10 八品类色块占位（前景按亮度取字色）+ 域名下移独立弱化行；
  - W1 混入心愿提示：accent 小条 + Material Star（无 emoji），会话内一次、5s 自动收起、
    组合含愿望件即隐、角色无未购愿望件不提示（wishSlotItems 守卫）；
  - W8 顶栏「随机一套」（与 W1 统一）；W7 评论日期 `2026/09/24`（dump 全页无 MM/dd）；
  - W9 两处演示说明改 #55605A/onSurfaceVariant（与 it-034 同族取色，代码级验证）。
- 关键取舍（详见实施偏差）：W6 底距未按字面「动作栏高+16dp」（防 170dp 空洞，实测安全）；
  分段保留 M3 默认选中样式（共享 composable 保两页同款，线框「去勾选」变体未采纳）；
  W5 样衣网格缩略日期保留 MM/dd 短式（线框 W8① 明示角标短式，本轮只统一评论日期）。
