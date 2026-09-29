# it-062 · 顾问顶栏同色 + 会话列表预览 Markdown 扁平化

> 状态：**实施中**（2026-09-29，Leo 体验包两点反馈）
> 来源：①「顶部穿搭顾问那一行的背景色不对，和背景割裂了，它是白色的」；②「外面的这个消息列表页透出的这一部分文字，也需要使用 markdown 渲染器进行渲染」。

## 诊断

### 问题 1 · W12 顶栏白条与纸面割裂

全局背景是根 `Surface(background)` = `Paper #F7F7F5`，而 M3 `TopAppBar` 默认 `containerColor = colorScheme.surface` = `SurfaceLight #FFFFFF`。ChatListScreen（顾问 tab 页）与 ChatScreen（对话页）都用默认 TopAppBar → 纯白条贴在米灰纸面上，肉眼可见割裂。

it-034 A 曾有意让「白底二级页」白顶栏沉浸状态栏，但顾问两页的内容底实际是纸面色而非白色，白条属于惯性默认值而非设计意图。

### 问题 2 · 会话列表预览透出 Markdown 原文

`ChatSessionIndex.refresh()` 把 AI 最后一条消息 `replace('\n', ' ').take(60)` 直接存进 `preview`，`SessionCard` 原样 `Text` 显示——`## 第一套 · 秋冬通勤 - 上装：…` 的标记符号全部裸露。对话页正文走 `MarkdownText` 渲染，列表预览没有走同一套解析。

## 方案

### 修 1 · 顶栏纸面同色

- `ChatListScreen` / `ChatScreen` 的 `TopAppBar` 显式 `containerColor = ec.paper`，标题行与页面背景同色；CHAT 路由状态栏沉浸行为不变（纸面色铺进状态栏，与搭配/记录/衣橱 tab 一致）。

### 修 2 · 预览走 Markdown 解析（扁平化渲染）

- `ChatMarkdown.kt` 新增 `markdownPreviewText()`：与 `MarkdownText` 同源的解析口径——逐行剥块级标记（`#`/`-`/`1.`/`>`/水平线），行内复用 `cleanInlineMarkdown`（`**`/`` ` ``/链接→纯文本），列表项行以 ` · ` 连接、普通行以空格连接，压成单行纯文本。函数幂等：干净文本再进一次不变。
- 兼容旧缓存：历史 `preview` 已把换行压成空格，`- ` 残留在句中——`markdownPreviewText` 末段把句中 `" - "` 折叠为 `" · "`，老会话无需迁移即显示干净。
- 接线点只有显示侧 `SessionCard`（`remember` 缓存）。**不在 `ChatSessionIndex` 生成侧接线**：data 层禁止 import ui（specs/04 依赖只能从上到下），索引继续存原始摘要文本，渲染统一由 UI 层完成——新旧数据走同一条显示路径，无需数据迁移。
- 预览保持 2 行灰色小字形态不变（列表摘要位不做标题/列表结构化排版，只去标记）。

## 验收标准

- 顾问 tab 列表页与对话页：顶栏背景与页面纸面色一致，无白色横条；深色模式同样同色。
- 列表预览不再出现 `##`、`- `、`**` 等原始标记；`## 第一套 · 秋冬通勤\n- 上装：x` 类回复预览显示为「第一套 · 秋冬通勤 · 上装：x …」。
- 旧数据（索引里已存的带标记 preview）不迁移即显示干净。
- `./gradlew :app:testDebugUnitTest` 全绿（新增 `markdownPreviewText` 单测 + ChatSessionIndex 预览断言更新）。

## 实施记录

- `ChatListScreen.kt` / `ChatScreen.kt`：`TopAppBar` 显式 `colors = TopAppBarDefaults.topAppBarColors(containerColor = ec.paper)`，两页顶栏与页面同底（深浅色均随 token 走）。
- `ChatMarkdown.kt`：新增 `markdownPreviewText()` + 4 个预览专用正则（`PREVIEW_HR/HEADING/ITEM/QUOTE/COLLAPSED_ITEM`）；行内清洗复用 `OutfitRecommendationParser.cleanInlineMarkdown`。
- `ChatListScreen.kt :: SessionCard`：`remember(session.preview) { markdownPreviewText(...) }` 显示侧接线；`ChatSessionIndex` 与存储格式不动（data 不 import ui，specs/04 依赖规则）。
- 测试：新增 `MarkdownPreviewTextTest` 6 例（标记剥除/行内清理/旧缓存折叠/引用有序水平线/幂等/空串）。
- spec 同步：`02-wireframes.md` W12/W13、`05-design-system.md` 色彩 Token 节补「顶栏底色」规则 + it-054 节补 W12 预览扁平化。

## 验证记录

- 单测：`MarkdownPreviewTextTest` 6/6 绿、`ChatSessionIndexTest` 回归通过（2026-09-29，本机 Gradle）。
- 模拟器（AVD wardrobe_test，演示模式）：向 `mock-agent-sessions/index.json` 种入带 `##`/`-`/`**`/链接标记的旧格式会话 ×4 ——
  - 列表页截图：顶栏「穿搭顾问」行、状态栏、页面背景同为纸面色连续过渡，无白色横条；预览渲染为「第二套 · 周末休闲 · 上装：燕麦色圆领卫衣 · …」，无任何原始标记（含旧缓存数据）。
  - 对话页截图：顶栏（返回 + 标题）与页面同色融合，状态栏同色。
  - 截图 CDN：列表页 https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/25025d3a-88d0-4f5b-9953-6f2d5d82e738/it062-list3.png 、对话页 https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/25025d3a-88d0-4f5b-9953-6f2d5d82e738/it062-chat.png （链接含签名有时效）。
  - 验证后已恢复真实模式 `agent-sessions/index.json` 原内容；mock 目录种子保留（演示环境无害）。
