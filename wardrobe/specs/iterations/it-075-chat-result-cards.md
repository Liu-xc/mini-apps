# it-075 · 顾问对话页工具结果结构化卡片（单品卡 / 列表卡 / 抽屉浏览）

> 状态：**提案待拍板**（2026-09-30，Leo 反馈）
> 来源：「对话页的 UI 能力需要扩展。比如说查单品或者查类别的时候，应该有单品卡片或者类别的列表；模糊结果有多个的，也应该推出一个列表卡片；数量比较多（超过 4~5 个）就折叠起来，点击后通过抽屉交互展开列表浏览。工程和 UI 上面都要优化。」

## 背景与动机

W13 对话页目前对「查衣橱」类工具结果的呈现只有两级：折叠的 `ToolsRow`（「查了衣橱 · 1 次」，展开是 80 字符截断的纯文本）和 assistant 基于工具文本生成的 Markdown 正文。用户问「我有哪些外套」时，结果是一段 `· 名称（品类，颜色，标签）` 逐行文本——没有图片、不可点、不可浏览，与全站「卡片化、可直达详情」的体验断档。

根因在工程侧：`libs/agent` 的 `ToolResult.Ok(text)` 只有纯文本，工具结果拍平成字符串回喂模型与落盘，UI 层拿不到任何结构化数据（现有推荐卡 it-054 是靠 system prompt 文本协议反向解析 + 名称匹配绕出来的，只覆盖「推荐穿搭」一种场景）。要系统性支持「查单品→单品卡、查类别/模糊→列表卡、多条→折叠+抽屉」，需要把「工具结果 → 结构化 payload → UI 卡片 → 会话回放」的通道打通——这正是 Leo 说的「工程和 UI 都要优化」。

## 现状要点（调研结论）

- 4 个工具（`WardrobeTools.kt`：search_items / search_outfits / wear_stats / current_person）全部返回拼接中文文本，不带 id / imageFile。
- `AgentEvent.ToolFinished(call, result)` 已把整个 `ToolResult` 透传到 UI（`ChatViewModel.kt:250` 目前丢弃结构只取前 80 字）——事件通道现成，缺的是 `ToolResult` 本身的 payload 字段。
- 会话落盘 = 每会话一个 JSON（`FileSessionStore`，`List<Message>`），**不进数据包导出**；`Message.createdAt` 已有「缺字段默认值向后兼容」先例。历史回放出卡需要 payload 随消息落盘。
- 可复用件齐备：`PhotoCard`（Coil）、chat 包内 `OutfitItemTile` 形制、`TagRow`、`ModalBottomSheet`（ExportSheet 的 skipPartiallyExpanded + 0.92 屏上限形制，且 ChatScreen 已有嵌 sheet 先例）。
- 线框编号勘定：对话页现为 **W13**（W12 = 会话列表页，W11 = 设置页）。

## 方案

### 工程 · M1 通道（libs/agent + wardrobe）

1. **SDK 扩展**（`libs/agent`，其 specs 同步更新）：
   - `ToolResult.Ok(text, payload: JsonElement? = null)`；`Message` 增 `payload: JsonElement? = null`（挂在 role=tool 消息上）。
   - `AgentRunner` 落盘/回喂处 `Message.toolResult(call.id, result.asText(), result.payload)` 透传；**wire 层（厂商请求体）忽略 payload**，不改变现有 API 请求字节（须回归验证 GLM 不受影响，注意 `Wire.kt encodeDefaults=false` 坑）。
   - 旧会话文件缺 `payload` 字段 → 反序列化为 null，无需迁移；事件只增不改名（ADR-005 合规）。
2. **工具侧**：`search_items` 返回 payload `{"kind":"item-list","query":{…},"total":N,"ids":[…]}`
   —— **只存 Item id 引用，不存数据快照**。卡片渲染时从仓库现取：衣物被改名/换图自动新鲜，被删则显示「已移出衣橱」占位行。文本部分保留不动（模型仍读文本生成正文，ToolsRow 展开兜底）。
3. **ChatViewModel / ChatScreen**：ToolFinished 与历史加载两条路解析 payload；`groupRows` 新增 `RowUi.Result(card)` 行型，按时间序插在工具条与 AI 回复气泡之间；流式约束沿用 it-056（工具完成即出卡，流式半截不闪现）。

### UI · M1 卡片（新文件 `ChatResultCards.kt`，挂 chat 包）

- **单品卡**（命中 1 件）：横向卡 = PhotoCard（mat）+ 名称 + 品类·颜色 + 标签 chips，整卡可点进 W5 详情。
- **列表卡**（命中 ≥2 件）：与 AI 气泡同宽同底（surface + hairline）；header「找到 N 件 · 外套/关键词」+ 条目行（56dp 缩略图 + 名称 + 品类·颜色 + `›`，点击进 W5）。
- **折叠 + 抽屉**（命中 > 5 件）：卡内只显示前 5 行，尾部「查看全部 N 件」按钮 → `ModalBottomSheet`（skipPartiallyExpanded、高度上限 0.92 屏）内 LazyColumn 全量浏览，行点击关抽屉进 W5。
- 0 命中不出卡（现状工具条足够）。

### 范围切分

- **M2（默认同批）**：`search_outfits` 走同一通道出穿搭列表卡（成品图/标题/标签，同一折叠+抽屉规则），组件直接复用。
- **不做（本迭代划界）**：assistant 正文任意位置内联单品卡（文本协议解析成本高，it-054 已覆盖推荐场景）；`wear_stats` 统计图表化（候选下迭代）。

## 用户故事（US-62，W13）

**US-62a 单品与类别查询出卡**：作为用户，我问「我有哪些外套」「那件蓝色外套是什么」时，回复旁直接出现带图的卡片，而不是纯文字列表。
**US-62b 模糊多结果出列表卡**：模糊查询命中多件（如「蓝色的」）时，出现列表卡片逐行展示，每行可点进衣物详情。
**US-62c 多结果折叠与抽屉浏览**：命中超过 5 件时，列表卡折叠为前 5 件 + 「查看全部 N 件」，点击以抽屉（底部弹层）全量浏览。

## 验收标准

1. 问品类（「我有哪些外套」）：命中 ≤5 出全量列表卡；>5 出前 5 + 尾部「查看全部 N 件」。
2. 模糊词命中多件：列表卡逐行展示；精确命中 1 件：单品卡，点击进 W5 详情。
3. 抽屉可滚动浏览全部命中，行点击关闭抽屉并进入 W5；返回对话页卡片状态不丢。
4. 杀进程重进会话：历史卡片完整回放（payload 已随会话文件落盘，无需重新查询）。
5. 卡片引用的衣物被删除：对应行显示「已移出衣橱」占位，不崩溃、不空白。
6. 旧版本会话文件（无 payload 字段）正常打开，无卡片亦无报错；未配置 Key 的只读历史同样出卡。
7. 厂商请求体不含 payload 字段（wire 回归）；SDK 单测：payload 透传 / 落盘回放 / 旧文件兼容；app 单测：payload 解析、groupRows 新行型、删除占位、>5 折叠阈值。
8. 演示模式模拟器走查截图（三形态 + 抽屉）回填本文件验证记录。

## 影响范围

- `libs/agent`：ToolRegistry / Message / AgentRunner / FileSessionStore + **其 specs/ 与 ADR 同步**（依赖方向合规：应用 → libs）。
- `wardrobe/app`：`ui/chat/`（WardrobeTools、ChatViewModel、ChatScreen、新 ChatResultCards.kt）、domain 仓库读取接口。
- 常青 spec：01-user-stories（US-62）、02-wireframes（W13 结果卡与抽屉交互路径）、03-data-model（agent-sessions 消息 payload 字段说明，仍不导出）、05-design-system（结果卡视觉 token）、06-decisions（ADR：工具结果结构化通道，payload 只存 id 引用不存快照）。
- 不影响：数据包格式、其他应用、wire 协议线上行为。

## 待拍板

1. 折叠阈值：默认 **5**（Leo 原话「超过 4 个 5 个」），可改 4。
2. 抽屉内布局：默认**行式列表**（与卡内行同形制，浏览语义强）；备选 2 列网格（信息密度高）。
3. M2（穿搭查询列表卡）默认同批落地；也可拆到下迭代减负。

## 实施记录

- **libs/agent（SDK 通道，ADR-006）**：`ToolResult.Ok(text, payload: JsonElement? = null)`；`Message` 增 `payload`（role=tool 携带，null 落盘省略、缺字段读 null）；`AgentRunner` 工具轮 `Message.toolResult(call.id, text, payload)` 透传落盘；wire 层 `toWire()` 不映射（厂商请求体零变化，`PayloadTest` 断言锁定）。
- **WardrobeTools.kt**：`search_items` 附 `{kind:"items",total,category,keyword,ids[]}`（ids 全量——文本仍 take(20) 喂模型，payload 不截断供抽屉）；`search_outfits` 附 `{kind:"outfits",total,keyword,ids[]}`（ids 按 updatedAt 降序全量）。`wear_stats`/`current_person` 不附（无卡片需求）。
- **ChatResultCards.kt（新）**：`ToolResultCard` sealed 模型（Items/Outfits，只存 id+条件）+ `parseToolResultCard()`（不认识的结构一律 null，UI 退回纯文本工具条）；`queryLabel()/headerLabel()`（「找到 8 件 · 外套 · “蓝”」）；三形态卡（`SingleItemBody` 单品横卡 / `ListCardBody` 列表卡 + `RESULT_INLINE_MAX=5` 折叠 + 「另有 N 件已移出衣橱」计数 / 全失效单行占位）；`ResultBrowserSheet` 抽屉（skipPartiallyExpanded + 0.92 屏上限，行点击关抽屉进 W5/W7）。组件复用 PhotoCard/TagRow，视觉与推荐卡同族（paper+hairline 16dp）。
- **ChatViewModel.kt**：`chatOutfits` StateFlow（穿搭卡数据源，与 recommendationItems 同构）；`ToolNotice.card` live 解析（工具完成即出卡）。
- **ChatScreen.kt**：`RowUi.Tools` 增 `cards`（groupRows 从 tool 消息 payload 解析，历史回放与 live 同一入口）；live notices 区与历史 Tools 行后渲染 `ToolResultCards`；`browserCard` 抽屉状态挂载；新参数 `onOpenOutfit`。
- **MainActivity.kt**：CHAT 路由接线 `onOpenOutfit → Routes.outfitDetail(id)`（W7）。
- 测试：SDK `PayloadTest` 5 例（透传/落盘回放/旧文件兼容/null 省略/wire 隔离）；app `ToolResultCardTest` 6 例（解析/容错/标签拼装/工具契约——search_items 25 件时 payload ids 全量不截断）。
- spec 同步：01（US-62a/b/c）、02（W13 描述 + it-075 线框节）、03（agent-sessions payload 字段）、05（it-075 组件节）、06（ADR-029）；libs/agent specs（00 架构一行 + ADR-006）；根 CHANGELOG。

## 验证记录

### 单测（2026-09-30，本机 Gradle）

- libs/agent `PayloadTest` 5/5 绿（payload 透传 / FileSessionStore 落盘读回 / 旧文件缺字段 null / null 省略字段完整往返 / wire 请求体无 payload 且其余字段形态不变）；SDK 既有测试全绿。
- wardrobe app `ToolResultCardTest` 6/6 绿（items/outfits 解析、非法结构七连全 null 不抛、total 缺失回退、条件摘要拼接、**工具契约**：25 件命中时 payload ids 全量不截断）；全量 `:app:testDebugUnitTest` **99/99 绿**。

### 模拟器走查（AVD emulator-5554，demoDefault 包，未配 Key 只读态）

种 6 个会话（带 payload 的 tool 消息 + 正常 assistant 回复）到 `mock-agent-sessions/`，重启后逐会话打开：

| 形态 | 会话 | 结果 |
|---|---|---|
| 单品横卡 | 皮夹克单卡（1 件） | ✓ 图 + 名称 + 「外套 · 深棕色」+ 4 标签 chips，整卡可点 |
| 列表卡 + 移除计数 | 蓝色外套（3 真 + 1 伪 id） | ✓「找到 4 件 · 外套 · “蓝”」3 行 + 「另有 1 件已移出衣橱」 |
| 折叠 + 抽屉 | 全部外套（6 件） | ✓ 内联前 5 + 「查看全部 6 件」→ 抽屉一次展开 6 行全量；行点击关抽屉直达 W5 详情（实测暖象牙色绗缝马甲） |
| 穿搭列表卡 | 穿搭列表（8 套） | ✓「找到 8 套穿搭 · 穿搭」组合名「A + B + …」+ 标签摘要，5 行内联 + 「查看全部 8 套」 |
| 全删占位 | 已删占位（2 伪 id） | ✓ 单行「这 2 件已移出衣橱」，无空白卡无崩溃 |
| 旧格式兼容 | 旧格式回放（无 payload） | ✓ 正常打开，无卡片无报错，仅工具条 |

- 附带实测：跨角色 id 失配（Leo 会话里 Mia 的衣物）同样落「已移出衣橱」计数——与删除同路径，符合「仓库是唯一 SSOT」。
- 截图（CDN，链接含签名有时效）：[单品横卡](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/776a3bbb-4f5d-4fc2-8f80-a9c02599de95/03-single.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1790740509&Signature=6NfjOL1oaRPD8gEKaZK3Z3+Nw5c=) · [列表卡+移除计数](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/776a3bbb-4f5d-4fc2-8f80-a9c02599de95/04-list.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1790740519&Signature=nBsBscWQNVMmxuBJ6GLOcVYoNww=) · [折叠卡](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/776a3bbb-4f5d-4fc2-8f80-a9c02599de95/05-fold.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1790740495&Signature=0qfNuljopirThCgQPNwQdyG9Ms0=) · [浏览抽屉](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/776a3bbb-4f5d-4fc2-8f80-a9c02599de95/06-drawer.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1790740498&Signature=/PuzhbwiQ4sup8GTKKhDMyezMBk=) · [穿搭列表卡](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/776a3bbb-4f5d-4fc2-8f80-a9c02599de95/08-outfits.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1790740522&Signature=QGoINztpDwTA/MnNBysnxHbeMA0=)。
- live 路径（真实对话中工具完成即出卡）与历史回放共用 `parseToolResultCard` 同一入口，payload 契约由 `ToolResultCardTest` 锁定；配 Key 的真机 live 全链路留给 Leo 体验包验收。


