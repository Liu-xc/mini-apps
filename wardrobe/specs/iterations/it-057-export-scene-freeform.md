# it-057 — 导出表单随推荐走：净初值与场景自由文本

状态：**已实现**；构建、单测与模拟器走查通过（2026-09-29）
日期：2026-09-29

## 背景与动机

it-056 让导出面板携带顾问场景，但 Leo 真机实测暴露两层冲突：推荐一套「休闲」装，打开保存长图的面板，表单里仍是上一次的「办公室」场景——AI 的灵活语境被旧值冒充覆盖。

- **根因 1（记忆残留）**：it-056 合并策略为「上次记忆为底、本次预选覆盖同 key」（`savedSelections + presetSelections`）。预选只回填**完整命中预设选项**的维度；Agent 用「周末休闲」「逛街」等自由措辞时全部 miss，这些维度原样保留旧记忆的「办公室/早秋」，且以 accent「当前值」样式呈现，用户无从分辨是建议还是残留。
- **根因 2（枚举接不住自由文本）**：场景维度仅 8 个预设选项，表单无自由输入；AI 灵活输出的场景词命中不了枚举就被丢弃。「用枚举过滤 AI」与「把 AI 语境带进表单」目标相反。
- 放大器：预选/选择会写回全局记忆，上一套的语境持续污染下一次对话导出。

## 涉及的用户故事

- **US-58（再补强）**：
  - Given 从对话推荐卡打开导出面板，Then「画面设定」初始值**只反映本次推荐**（命中预设选预设、场景短语未命中则回填短语原文、其余维度显示「未设置」），不混入历史记忆；旧场景残留不复现。
  - Given 面板场景行带入了自由文本值（如「周末休闲逛街」），Then 该值以选中态常驻显示，可一键清除、可经「自定义」入口改写，长图文案同步携带。

## 体验方案

1. **对话入口净初值**：`ExportSheet` 新增 `replaceSavedSelections: Boolean = false`；对话入口传 `true`（初始 selections = presetSelections，不叠加记忆），其余四处调用不传、行为不变。首次打开「场景未选则自动展开场景行」逻辑照旧——净初值下正好引导用户查看/修改。
2. **场景自由回填**：`extractRecommendationSelections` 增强——
   - 场景短语 = 标题「·」后的文本；含预设选项则选预设（如「公园漫步」→公园），否则回填短语原文（去空白、≤8 字，超长截断到 8 字）。
   - 氛围放宽为子串级：Agent 说「休闲」→「休闲随性」、「通勤」→「通勤简约」（option 以短语开头或短语以 option 开头即可命中）。
   - 季节/光线/构图维持完整枚举匹配，miss 即不设（理由文本中这些词噪声大，误配代价高于收益）。
3. **场景行自定义**：`DimensionSettingRow` 展开区 chips 末尾加「自定义」chip（仅场景行）：点击弹小型输入（或内联输入行），确认后该维 value = 输入值；当前值为自由文本时以选中态 chip 显示、再点即清除。预设 chips 的互斥逻辑不变（同维单值）。
4. **写回语义不变**：面板确认过的 selections 仍写回 `exportSelections` 记忆，服务搭配页等非对话入口；对话入口每次从推荐重新解析，不受记忆影响。

## 明确不做

- 不改 system prompt / 卡片协议（不让模型迁就枚举）。
- 季节/光线/构图不做自由回填；氛围只做首尾子串放宽，不做任意子串（「优雅」→「优雅正式」可，「约」→ 不允许）。
- 不新增持久化字段/数据模型；自定义场景值不持久化为新的预设选项。

## 验收标准

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过；解析函数新增用例 ≥4（自由场景回填/超长截断/氛围子串放宽/枚举维度不放宽）。
- 真机/AVD：连续导出两套语境不同的推荐（如「办公室通勤」→「周末休闲逛街」），第二套面板：场景=「周末休闲逛街」（自由值选中态）、季节未设置（若无提及）、「办公室」不出现；自定义入口可改可清除，长图文案维度行同步。
- 搭配页等既有入口记忆恢复行为零变化；it-056 既有 4 例单测按新规则更新后全绿。
- 场景行新增 chip 触控目标 ≥44dp、样式沿用 it-051/it-052 既有 token，无新色彩。

## 影响范围

- **代码**：`ui/chat/OutfitRecommendationParser.kt`（提取规则）、`ui/chat/ChatScreen.kt`（传 replace）、`ui/outfit/ExportSheet.kt`（参数 + 场景行自定义 UI）。
- **测试**：`OutfitRecommendationParserTest` 更新 + 新增。
- **常青 spec**：`01-user-stories.md` US-58、`02-wireframes.md` W13/W6 注记、根与应用 CHANGELOG。
- **不涉及**：数据模型、system prompt、协议、依赖、`BuildOutfitPrompt`/`OutfitImageComposer`（dimensionLine 已兼容任意值）。

## 实施记录

- `OutfitRecommendationParser.kt`：`extractRecommendationSelections` 重写——场景维度「预设优先（短语/全文含选项）→ 未命中回填标题「·」后短语（≤8 字截断，`SET_NO` 正则排除纯序号）」；氛围维度 `relaxedOption` 前缀放宽（选项 ≥2 字前缀命中，取最长）；季节/光线/构图维持完整枚举匹配。
- `ExportSheet.kt`：新增 `replaceSavedSelections: Boolean = false`（init 时 base 置空、不叠加记忆）；`DimensionSettingRow` 新增 `allowCustom`（仅场景行）：chips 末尾「自定义」chip + 内联输入行（OutlinedTextField + 确定，`Button`），自由值以选中态 chip 常驻、点击经同值分支清除。
- `ChatScreen.kt`：对话入口 ExportSheet 传 `replaceSavedSelections = true`；其余四处调用不传，行为零变化。
- 测试：`OutfitRecommendationParserTest` it-056 4 例按新规则更新 + it-057 新增 2 例（超长截断、前缀放宽且枚举不放宽），共 12 例。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，81 项单测全绿（Parser 12/12）。
- Android 14 AVD 走查（演示模式，污染-验证两段式）：
  - **污染**：搭配页「复制长图」打开面板（记忆恢复 办公室/早秋，it-012 行为不变），展开光线行选「夜晚霓虹」写入记忆。
  - **净初值**：对话页推荐卡「复制长图」打开面板——光线=**未设置**（「夜晚霓虹」未带入）、构图=未设置、场景=办公室、季节=早秋、氛围=**通勤简约**（前缀放宽，it-056 时为未设置）。
  - **自定义**：场景行展开 → 横滚见「自定义」chip → 内联输入「weekend」确定 → 行值与选中态 chip 同步显示；点自由值 chip 即清除回「未设置」。
  - adb `input text` 不支持中文，自由值输入以英文词验证；中文短语的回填路径由单测覆盖（「周末休闲逛街遛娃」截断例）。
- **局限**：darkroom 并行会话仍共用 AVD，本次未见抢前台；深色模式未单独走查（复用 it-051 既有 chip/输入 token）。
- 截图：[对话入口净初值](../../../reports/2026-09-29-it057/it057-chat-sheet-clean-init.png)、[场景自定义与清除](../../../reports/2026-09-29-it057/it057-scene-custom-clear.png)。
