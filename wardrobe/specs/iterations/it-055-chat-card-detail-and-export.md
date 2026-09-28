# it-055 — 顾问卡片点进单品详情与长图导出

状态：**已实现**；构建、单测与浅/深色走查通过（2026-09-29）
日期：2026-09-29

## 背景与动机

it-054 落地了 W13 顾问穿搭卡片，但卡片是纯展示：单品照片 tile 不可点、整卡没有任何操作。用户在对话里看到推荐后的下一步全断了——

1. **点不进详情**：想点某件单品看大图、标签、穿过它的穿搭，没有入口（W5 单品详情本身存在，只是没接线到对话页）。
2. **导不出长图**：想把「这套/这件 + 描述提示词」导成一张长图，拿去别的 AI 工具生成穿搭效果图——这正是 W6 导出面板在搭配页、穿搭详情、心愿导出三处已有的能力，唯独对话里没有。

而长图能力无需新造：`ExportSheet` 签名已支持 `existingOutfit = null`（搭配页与心愿均以此方式调用），`OutfitImageComposer` 按 items 列表合成照片拼版 + 五维提示词。把现成面板接到对话卡片与单品详情即可闭环。

## 涉及的用户故事

- **US-56（补强）顾问穿搭卡片**：
  - Given 卡片内单品已匹配当前角色衣橱，When 点击该 tile，Then 压栈进入 W5 单品详情，返回后仍在原对话、消息不丢。
  - Given 单品未匹配（模型虚构、衣橱中不存在），Then tile 不响应点击，保留「未在衣橱中匹配」中性提示，不伪装成可点条目。
- **US-58（新增）顾问卡片与单品的长图导出（W13 / W5）**：
  - Given 一套推荐含 ≥1 件已匹配单品，When 点卡片上的「复制长图」，Then 打开 W6 导出面板：`items` = 该套已匹配单品、`existingOutfit = null`、参考照 = 当前角色；复制长图 / 存相册 / 分享 / 只复制文本 / 收藏这套 / 录入成品图与搭配页同权。
  - Given 打开任一单品详情，Then 有导出入口，打开同一面板、`items` = 该单品（复用心愿单件导出先例）。
  - Given 该套含心愿单品，Then 面板沿用「心愿组合 · 仅预览」门禁，不落正式 Outfit。

## 体验方案

### 一、卡片交互（W13）

- **单品 tile 整块可点**：命中区 = 整个 tile（图片 88dp + 品类/名称两行文字），远超 44dp 基线；点击 `nav.navigate(Routes.itemDetail(id))`。未匹配 tile 不挂 clickable，保持灰显与中性提示（DESIGN §2.5：不给不存在的对象假入口）。
- **卡片底部操作行**：一枚次 CTA（透明底 + hairline 描边，图标 Material `ContentCopy` + 文字「复制长图」）→ 打开 ExportSheet。操作行不引入新色彩、不加阴影，卡片维持纸面 hairline 结构（it-052 黑白灰基调，accent 占比 < 10%）。
- **接线**：`MainActivity` 的 `Routes.CHAT` composable 新增 `onOpenItem = { nav.navigate(Routes.itemDetail(it)) }`，经 `ChatScreen` 透传到 `AssistantReply` → `OutfitRecommendationCard` → `OutfitItemTile`；导出面板 state 放在 `ChatScreen` 层（`appVm` 已在该层）。
- **流式安全**：卡片仍按 it-054 规则「结构完整后才出现」，操作行与 tile 随卡片同生，不出现半截卡片可点的窗口。

### 二、单品详情导出（W5）

- `ItemDetailScreen` 顶栏 actions 在「编辑」旁增加导出图标钮（Material `Download`，contentDescription「导出长图」），点击打开 ExportSheet（`items = listOf(item)`，`existingOutfit = null`，参考照 = 当前角色）。
- 面板内行为零改动：单件即单格拼版，提示词、复制、收藏（含心愿门禁）全部沿用现有实现。

### 三、明确不做

- 不改 `OutfitRecommendationParser` 协议与 system prompt；不新增数据模型/持久化字段；不新增第三方依赖。
- 推荐卡不直接跳「穿搭详情」——推荐尚未保存，不存在 outfitId；需要入档的路径是面板里的「收藏这套」。

## 验收标准

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过。
- 点已匹配 tile → 进 W5；系统返回 → 回到原对话，消息与卡片完整。点未匹配 tile → 无导航、无闪烁。
- 推荐卡「复制长图」→ 面板 items 恰为该套已匹配单品；复制出的长图含单品照片拼版 + 提示词；「收藏这套」后在穿搭记录可见。
- W5 导出入口 → 单件长图可用；含心愿单品的组合仍显示「心愿组合 · 仅预览」。
- 浅色/深色完成 W13、W5 截图走查，P0/P1 为 0；触控目标、图标、accent 占比过 DESIGN §2.1/§2.5 对表。
- it-054 既有验收不回归：Markdown 渲染、复制原始 Markdown、流式半截回复不崩溃、Mock 会话正常。

## 影响范围

- **代码**：`ui/chat/ChatMarkdown.kt`（tile 点击 + 操作行）、`ui/chat/ChatScreen.kt`（回调透传 + ExportSheet 挂载）、`MainActivity.kt`（CHAT 路由接线）、`ui/detail/ItemDetailScreen.kt`（导出入口 + ExportSheet 挂载）、`ui/chat/OutfitRecommendationParser.kt`（匹配纯函数）。
- **测试**：`OutfitRecommendationParserTest` 新增匹配纯函数 2 例。
- **常青 spec**：`01-user-stories.md`（US-56 补强 + 新增 US-58）、`02-wireframes.md`（W13 卡片操作行与 tile 可点注记、W5 复制长图入口）、根 `CHANGELOG.md` 与 `specs/CHANGELOG.md` 同步。
- **不涉及**：`03-data-model.md`、`04-architecture.md`、`05-design-system.md`（复用既有按钮/面板样式，无新 token）、`06-decisions.md`、存储 schema、AgentRunner、`OutfitRecommendationParser` 卡片协议、依赖变更。

## 实施记录

- `OutfitRecommendationParser.kt`：新增 `matchRecommendationItems(recommendation, wardrobeItems)` 纯函数（名称规范化匹配、未匹配返回 null 对），`normalizeName` 从 `ChatMarkdown.kt` 移入；UI 与单测共用同一真源。
- `ChatMarkdown.kt`：`AssistantReply` / `OutfitRecommendationCard` 增 `onOpenItem` / `onExport` 回调；已匹配 tile 整块 `clickable` 进 W5、未匹配 tile 不挂点击；卡片底部新增次 CTA「复制长图」（ContentCopy + 描边按钮，≥1 件匹配且注入回调才显示）。
- `ChatScreen.kt`：透传 `onOpenItem`；`exportItems` state + 挂载 `ExportSheet`（`existingOutfit=null`、参考照取 `currentPerson`）；流式中回复 `onExport=null`，导出入口只挂完整消息；`appVm` 未注入时不渲染导出按钮（无死按钮）。
- `MainActivity.kt`：`Routes.CHAT` 接线 `onOpenItem = { nav.navigate(Routes.itemDetail(it)) }`。
- `ItemDetailScreen.kt`：穿过这些穿搭与评论之间新增全宽「复制长图」描边按钮（与 W7 同词同构；提案中的顶栏图标钮改为内容区按钮，跟随 W6/W7 词汇与点击习惯）→ `ExportSheet(items = listOf(item))`。
- 测试：`OutfitRecommendationParserTest` 新增 2 例（规范化命中/未匹配保留 null、顺序保持与同名取首件）。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，77 项单测全绿（75 + 2 新增）；`-PdemoDefault=true` 打包 `DEMO_DEFAULT=true`。
- Android 14 AVD 走查（演示模式 + 注入只读演示会话，全程离线）：
  - **浅色**：W13 卡片出现「复制长图」；点已匹配 tile（米色打褶长裤）压栈进 W5、系统返回回对话消息不丢；W5「复制长图」打开单件面板（长图预览仅 1 格 + 五维提示词 + 复制/存相册/分享/只复制文本）；卡片「复制长图」打开整套面板（5 件已匹配单品入拼版）；注入「帽子：复古不存在草帽」后卡片灰显 +「未在当前衣橱匹配」提示，点击不导航。
  - **深色**：W13 卡片（tile/未匹配态/导出按钮）、W5 导出按钮、W6 面板（含「已存相册 ✓」状态）均按墨绿纸对值正常渲染。
- `git diff --check`：通过；走查 P0/P1：0。
- **局限**：走查期间显影（darkroom）会话共用同一 AVD 并发操作，个别点击需重试才命中（非功能问题）；流式回复的「导出入口不出现」以代码走查为准（`onExport=null`），未做流式实测。
- 截图：[浅色卡片](../../../reports/2026-09-29-it055/it055-card-light.png)、[tile 进详情](../../../reports/2026-09-29-it055/it055-tile-to-detail-light.png)、[单件面板](../../../reports/2026-09-29-it055/it055-item-export-sheet-light.png)、[整套面板](../../../reports/2026-09-29-it055/it055-card-export-sheet-light.png)、[未匹配不可点](../../../reports/2026-09-29-it055/it055-unmatched-no-nav.png)、[深色卡片](../../../reports/2026-09-29-it055/it055-card-dark.png)、[深色面板](../../../reports/2026-09-29-it055/it055-sheet-dark.png)。
