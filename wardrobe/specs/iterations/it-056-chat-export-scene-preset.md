# it-056 — 导出面板携带顾问推荐的场景参数

状态：**已实现**；构建、单测与模拟器走查通过（2026-09-29）
日期：2026-09-29

## 背景与动机

it-055 把「复制长图」接到了对话推荐卡，但只携带了 `items`：Agent 按 system prompt 以「## 第 X 套 · **场景**」开头作答，并在「适合/理由」里写明场合与季节（如「第一套 · 通勤」「适合：办公室 / 早秋」），这些信息在卡片层就被丢弃——打开 W6 导出面板时，「画面设定」五维仍恢复**上次全局记忆**（甚至为空），用户必须把 Agent 刚说过的场景重新选一遍，推荐语境与导出表单是断的。

## 涉及的用户故事

- **US-58（补强）顾问卡片长图导出**：
  - Given 推荐标题或说明文本命中「画面设定」五维的预设选项（如 场景·办公室、季节·早秋），When 点「复制长图」打开面板，Then 对应维度已预选，用户可直接确认或一键改掉；未命中的维度保持上次记忆。
  - Given 推荐文本命中不了任何预设选项，Then 面板行为与 it-055 完全一致（不预选、不注入自由文本），用户仍可在文案区手改。

## 体验方案

- **提取规则**（纯函数 `extractRecommendationSelections`）：对 `title + detailMarkdown` 全文，按 `PromptPresets.dimensions` 顺序对每维选项做**包含匹配**，每维取首个命中选项（选项序已长词在前，`早春` 先于 `春`、`全身+环境远景` 先于 `全身照`，天然最长优先）。匹配不区分维度权重——标题与「适合/理由」都可能携带场景词，全文匹配信息量最大；预选只是初值，面板内 chip 一键可改/可取消，误命中（如否定句「不适合海边」）可由用户直接纠正，不做 NLP 级语义判断。
- **接线**：`onExport` 回调从 `(List<Item>)` 升为 `(OutfitRecommendation, List<Item>)`；`ChatScreen` 打开面板时解析推荐文本得预选 map；`ExportSheet` 新增可选参数 `presetSelections: Map<String, String> = emptyMap()`，初始化时以持久化记忆为底、预选覆盖同 key（it-012 的 ready 竞态修复逻辑保持不变，合并在同一处）。
- **持久化语义**：预选后面板照常把 `selections` 写回 `exportSelections` 记忆——预选即「用户所见所选」，与手动点选等价；下次在搭配页打开面板沿用该值，再从对话打开另一套推荐时新预选再次覆盖。不做「预选不落记忆」的特判，避免为一条路径引入第二种记忆语义。
- **明确不做**：不改 system prompt 与卡片协议（场景词已天然在标题里）；不给五维增加预设外自由文本选项（「音乐节」等未命中场景由既有「文案（实时生成，可编辑）」兜底，预设外自由维度列为后续候选）；不触碰 `promptBuilder`/`OutfitImageComposer`。

## 验收标准

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过；新增提取函数单测 ≥4 例（多维命中/最长优先/无命中空 map/标题与理由分散命中）。
- 演示会话注入含「· 通勤」「适合：办公室 / 早秋」的推荐 → 点「复制长图」→ 面板 场景=办公室、季节=早秋 已预选；长图文案的维度行随之生成，无需手选。
- 搭配页、穿搭详情、心愿、单品详情四处既有 ExportSheet 调用不传 `presetSelections`，行为零变化。
- 未命中预设的场景词（如「音乐节」）不注入任何自由文本，面板行为同 it-055。

## 影响范围

- **代码**：`ui/chat/OutfitRecommendationParser.kt`（新增纯函数）、`ui/chat/ChatMarkdown.kt`（onExport 签名）、`ui/chat/ChatScreen.kt`（解析与 state）、`ui/outfit/ExportSheet.kt`（presetSelections 参数与合并）。
- **测试**：`OutfitRecommendationParserTest` 新增提取用例。
- **常青 spec**：`01-user-stories.md`（US-58 补强两条 Given/Then）、`02-wireframes.md`（W13 it-055 交互注记补一句预选）、根 `CHANGELOG.md` 与 `specs/CHANGELOG.md`。
- **不涉及**：数据模型、存储 schema、system prompt、卡片协议、依赖、`OutfitImageComposer`。

## 实施记录

- `OutfitRecommendationParser.kt`：新增 `extractRecommendationSelections(recommendation)` 纯函数——对 `title + detailMarkdown` 按 `PromptPresets.dimensions` 顺序做选项包含匹配，每维取首个命中（选项序长词在前，最长优先）。
- `ChatMarkdown.kt`：`AssistantReply` / `OutfitRecommendationCard` 的 `onExport` 签名从 `(List<Item>)` 升为 `(OutfitRecommendation, List<Item>)`，卡片不再丢掉推荐语境。
- `ChatScreen.kt`：state 由 `exportItems` 改为 `exportRequest = items to extractRecommendationSelections(recommendation)`；`AiBubble` 参数类型同步。
- `ExportSheet.kt`：新增 `presetSelections: Map<String, String> = emptyMap()` 参数；it-012 的记忆恢复 `LaunchedEffect` 中以 `savedSelections + presetSelections` 合并（预选覆盖同 key、其余维度保留上次记忆；ready 竞态防护不变）。四处既有调用不传该参数，行为零变化。
- 测试：`OutfitRecommendationParserTest` 新增 4 例（标题+理由命中且不取半截选项 / 最长选项优先 / 无命中空 map / 标题理由分散命中合并）。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，81 项单测全绿（77 + 4 新增）；APK 安装 Android 14 AVD。
- 端到端走查（演示模式 + 演示缓存会话「## 第一套 · 通勤 / 适合：办公室 / 早秋」，全程离线）：
  - 顾问会话推荐卡 → 点「复制长图」→ 导出面板「画面设定」：**场景=办公室、季节=早秋** 已预选（accent 色常驻显示），氛围/光线保持「未设置」（「通勤」是「通勤简约」半截，不误选）。
  - 关面板回 W1 搭配页「保存这套」打开同一面板：场景=办公室、季节=早秋 沿用——预选按设计写回 `exportSelections` 记忆，与手动选择等价，该路径未传 `presetSelections` 走纯记忆恢复。
- **局限**：走查期间显影（darkroom）会话共用同一 AVD 并发抢占前台（两次把 wardrobe 切到后台），回归验证经重拉前台完成；深色模式未单独截图（预选逻辑与主题无关，五维行样式沿用 it-051 既有选中态）。
- 截图：[面板预选（对话入口）](../../../reports/2026-09-29-it056/it056-sheet-preset.png)、[搭配页记忆沿用](../../../reports/2026-09-29-it056/it056-memory-oufit-tab.png)。
