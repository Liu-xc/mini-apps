# it-054 · 顾问穿搭卡片与 Markdown 回复渲染

- **日期**：2026-09-28
- **状态**：已实现；构建、单测与浅/深色视觉走查通过
- **来源**：顾问页面截图反馈：模型已经给出搭配思路，但必须把衣橱已有单品组织成可识别的穿搭卡片；模型文本也不能继续以原始 Markdown 符号直出。
- **类型**：W13 顾问输出结构与呈现升级

## 背景与动机

当前 W13 将 assistant 回复作为一段普通 `Text` 展示。模型虽然能调用衣橱工具并返回单品名称，但用户看到的是连续长段落，无法快速确认「这套穿搭由哪些已有衣服组成」，也无法像截图中的示例那样分辨多套方案。

本迭代把模型回复约束为稳定的 Markdown 结构，并在本地将其中引用的已有单品解析为穿搭卡片：卡片由真实衣橱单品的照片与名称组成，理由与场景说明继续以 Markdown 正文呈现。模型只负责推荐与解释，卡片里的图片和单品归属由本地衣橱 SSOT 解析，避免把模型臆造的衣物显示成用户已有物品。

## 涉及的用户故事

### US-56 顾问穿搭卡片（W13）

作为用户，我询问「配一套通勤装」时，希望看到基于自己衣橱已有单品组成的可浏览穿搭卡片，而不是一段难以扫描的单品长句。

- Given 模型根据衣橱工具返回 1–3 套方案，Then 每套方案包含稳定的标题、场景/风格、单品清单与搭配理由。
- Given 单品名称来自当前角色的衣橱，Then 卡片优先显示真实单品照片、品类和名称；图片由本地 `imageFile` 解析，不使用模型提供的外部图片地址。
- Given 模型文本中出现无法与当前衣橱精确匹配的名称，Then 不把它伪装成已有衣物；保留为正文中的建议并显示「未在衣橱中匹配」的中性提示。
- Given 回复包含多套方案，Then 卡片之间有清晰的序号、标题和间距，用户可以在一屏内快速比较，不改变现有复制、追问、重新生成动作。
- Given 流式输出尚未完成，Then 文本可以渐进渲染；穿搭卡片在结构足够完整后出现，不因半截 Markdown 导致崩溃或显示原始语法。

### US-57 顾问 Markdown 渲染（W13）

作为用户，我希望模型输出像编辑后的内容一样可读，标题、粗体、列表和引用有明确层级，而不是看到 `**`、`-` 等 Markdown 源码。

- Given assistant 回复包含常用 Markdown，Then 至少正确渲染一级/二级标题、粗体、斜体、无序列表、有序列表、引用和段落换行。
- Given 回复包含穿搭卡片约定结构，Then 卡片被提取为结构化 UI，其余说明继续按 Markdown 渲染，不能重复显示同一段原始语法。
- Given 用户点击「复制」，Then 复制模型原始 Markdown 文本，保证内容可迁移，不复制 UI 拼接后的不可编辑文本。
- Given 浅色/深色主题，Then Markdown 正文、标题、列表标记和卡片均遵循 it-052 的黑白灰 token；不引入新的彩色强调。

## 输出协议

在 system prompt 中约束模型使用以下 Markdown 形态；具体单品名称必须原样引用 `search_items` 返回的名称，不得编造衣橱中不存在的名称：

```markdown
## 第一套 · 通勤

**适合**：办公室 / 晴天

- 上装：黑色罗纹高领衫
- 外套：藏蓝色防雨派克外套
- 下装：米色打褶长裤
- 鞋：棕色切尔西短靴
- 包：浅棕色帆布托特包

**理由**：高领衫与长裤形成干净的通勤底子……
```

- 标题 `##` 开始一个方案块；最多输出 3 个方案。
- `品类：单品名` 行用于本地匹配卡片；品类沿用 `WardrobeCategory.label`。
- 「理由」「适合」等说明属于 Markdown 正文，不写入数据模型。
- 模型仍可调用只读工具；本迭代不增加任何写入工具，不改变会话持久化格式。

## 方案与验收标准

### 方案

1. 新增轻量 Markdown 渲染组件，覆盖顾问实际协议所需的块级与行内语法；不把原始 Markdown 直接交给 `Text`。
2. 新增纯 Kotlin 的回复解析器：从 Markdown 中提取方案标题、品类/单品行和说明范围；解析器只输出 UI 所需的值对象，不修改衣橱数据。
3. W13 assistant 气泡改为「Markdown 正文 + 可选穿搭卡片」组合：卡片使用已有 `Item.imageFile`，照片保持原色，缺图使用品类中性占位。
4. 更新 system prompt 与演示/Mock 回复，让真实模型和离线演示都遵守同一协议；保留原始文本用于复制。
5. 补充解析器 JVM 单测，并在模拟器验证「配一套通勤装」的 Markdown、卡片、深色模式和流式中间态。

### 验收标准

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过。
- W13 至少展示一套由真实衣橱单品照片组成的卡片；不存在把模型虚构单品当作本地照片的情况。
- 标题、粗体、列表、引用等 Markdown 不显示原始符号；复制仍复制原始 Markdown。
- W12/W13 现有会话列表、历史消息、工具查询折叠条、错误重试、追问和重新生成不回归。
- 浅色/深色均完成截图走查，P0/P1 为 0。

## 影响范围

- **代码**：`ui/chat/ChatScreen.kt`、新增 `ui/chat/ChatMarkdown.kt` 与 `ui/chat/OutfitRecommendationParser.kt`；必要时调整 `ChatViewModel.kt` 的 system prompt 和演示回复。
- **测试**：新增回复解析器单测；更新 Mock Chat 回复断言。
- **常青 spec**：`specs/01-user-stories.md` 增加 US-56/US-57；`specs/02-wireframes.md` 补 W13 卡片结构；`specs/04-architecture.md` 记录 UI 层解析边界；`specs/05-design-system.md` 记录 Markdown/卡片排版规则；`specs/CHANGELOG.md` 与根 `CHANGELOG.md` 记录迭代。
- **不涉及**：`specs/03-data-model.md`、持久化 schema、衣橱实体与写入接口；本迭代不新增第三方依赖，除非实现评估证明现有 Compose 能力不足并另行记录 ADR。

## 实施记录

- 新增 `OutfitRecommendationParser` 纯 Kotlin 解析器与 JVM 单测。
- 新增 `ChatMarkdown`：标题、粗体、斜体、行内代码、链接、无序/有序列表、引用和段落换行；assistant 回复改为 Markdown 正文 + 结构化穿搭卡片。
- 卡片通过当前角色 `recommendationItems` 精确匹配真实 `Item`，使用本地照片；未匹配建议保留文字并显示中性提示。
- 更新 system prompt 与演示回复，约束 `## 第一套 · 场景`、`- 品类：单品名` 协议。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`，68 actionable tasks，解析器与既有 JVM 单测通过。
- `./gradlew -PdemoDefault=true :app:installDebug`：`BUILD SUCCESSFUL`，演示 APK 安装至 Android 14 AVD。
- 模拟器注入本地只读演示会话（不发起网络请求），验证当前衣橱 5/5 单品照片进入穿搭卡片；Markdown 标题、粗体、列表、引用不显示原始语法；浅色/深色均通过。
- 截图：[浅色 W13](../../reports/2026-09-28-it054/it054-chat-light.png)、[深色 W13](../../reports/2026-09-28-it054/it054-chat-dark.png)。
- `git diff --check`：通过；本次走查 P0/P1：0。
