# it-045 · 顶栏↔内容间距全局统一（20dp）+ 演示数据默认开构建开关

- **状态**：已实现，验收通过
- **来源**：Leo 2026-09-25 直接反馈——「有些页面的间距控制的不均匀，尤其是和顶部导航栏之间」；同轮要求「使用 mock 数据给我打包体验」
- **关联**：05-design-system「形状与间距」新增顶栏节奏条目；it-039「顶栏与内容边距遵循 20dp 屏幕节奏」验收（本次全站补齐）；it-015 演示模式

## 背景与动机

走查盘点（代码 + uiautomator dump 交叉实证）发现「顶栏下缘 → 首个内容元素」的间距
全站共存 **0 / 4 / 6 / 8 / 10 / 20dp 六种取值**，同构页面之间肉眼可辨地不齐：

- 二级白底页（同一套「M3 白顶栏沉浸」体系）：W4 添加 / W5 详情 / W7 穿搭详情 / W11 设置均为 **0dp**（内容紧贴顶栏）；W9 回顾主表 **4dp**；W10 心愿分段 **6dp**；W12 对话 **8dp**；仅 W9 闲置清单 **20dp**。
- 三个 Tab 页自绘顶栏自身就不齐：W1 搭配标题距状态栏 **8dp**，W3 衣橱 / W8 记录为 **12dp**；顶栏下间距 W1=10dp、W3=6dp、W8=6dp（无标签时 14dp，同页两套值）。
- 规范侧缺口：DESIGN.md §2.4 与 spec 05「形状与间距」只定义了横向「屏幕边距 20dp」，**从未定义顶栏下方内容起始间距**，导致各屏各写各的字面值（全项目无 spacing token）。

另（同轮打包需求）：演示模式（it-015）默认关，且入口是隐藏手势（衣橱标题 3 秒内连点 5 次），
Leo 拿到的正式包默认进的是空数据。需要一个**默认即演示数据**的构建开关，出「体验包」；
退出通道（设置 → 数据模式 → 退出演示模式）本就全构建可见，切换自由不受影响。

## 用户故事

- **US-45a**：作为用户，我在任意页面看到顶栏与内容之间的留白节奏一致，不出现有的页面贴死、有的页面宽松。
- **US-45b**：作为用户，我安装体验包后开箱即见丰富演示数据，无需知道隐藏手势；想回真实数据时可在设置里退出演示模式。

## 验收标准

- 全站统一：**顶栏（M3 TopAppBar 或自绘标题行）视觉下缘 → 首个内容元素 = 20dp**；
  三个 Tab 自绘顶栏距状态栏统一 **12dp**（原 W1 为 8dp）。
- 逐屏落点（改动前 → 后）：
  - W1 搭配：标题行 top 8→12；滚动列 top 6→16（行 bottom 4 + 16 = 20）
  - W3 衣橱：品类行 top 2→14（行 bottom 6 + 14 = 20）
  - W8 记录：标签行加 top 14；卡组列 vertical 8 → top 14 / bottom 8（无标签时 6 + 14 = 20）
  - W4 添加 / W5 详情 / W7 穿搭详情 / W11 设置：内容列 0 → 20
  - W9 回顾主表：首 Spacer 4 → 20；闲置清单本就 20 不动
  - W10 心愿：分段行 vertical 6 → top 20 / bottom 6
  - W12 对话：消息列 contentPadding top 8 → 20
- 空态插画块（自带 28dp 内边距）不动；顶栏与内容之间的分段控件/筛选行视为「首个内容元素」。
- 构建开关：`-PdemoDefault=true` 出的包 `BuildConfig.DEMO_DEFAULT=true`，新装/无偏好即进演示模式；
  不带该参数的常规构建行为完全不变（默认 false）。
- 演示模式既有通道不回退：隐藏 5 连点手势、设置页退出按钮、AI 离线 FakeChatModel、真实 wardrobe.json 零触碰。
- 更新 `05-design-system.md`「形状与间距」；构建 + 单测 + 模拟器量化复验（dump 逐屏断言 20dp）回填本文件。

## 影响范围

`ui/outfit/OutfitScreen.kt` · `ui/wardrobe/WardrobeScreen.kt` · `ui/records/RecordsScreen.kt` ·
`ui/settings/SettingsScreen.kt` · `ui/detail/ItemDetailScreen.kt` · `ui/records/OutfitDetailScreen.kt` ·
`ui/wardrobe/ItemEditScreen.kt` · `ui/recap/WardrobeRecapScreen.kt` · `ui/wishlist/WishlistScreen.kt` ·
`ui/chat/ChatScreen.kt` · `data/mock/DemoMode.kt` · `app/build.gradle.kts` ·
`specs/05-design-system.md`。

## 验证记录

**2026-09-25 · 编译/单测/模拟器量化复验全部通过**

- `compileDebugKotlin` / `assembleDebug -PdemoDefault=true` / `test` 全绿（chat 改动后重跑一轮 exit 0）。
- 模拟器 `wardrobe_test`（1080×2400，420dpi，density 2.625）装 debug 体验包，uiautomator dump 逐屏量化
  （「顶栏视觉下缘 → 首个内容元素」px ÷ 2.625）：

| 屏 | 断言口径 | 实测 | 结果 |
|---|---|---|---|
| W1 搭配 | 标题行子节点下缘 287 → 滚动列首元素 339（行底 4+16） | 52px = **19.8dp** | ✅ |
| W3 衣橱 | 标题行下缘 286 → 品类行 339（行底 6+14） | 53px = **20.2dp** | ✅ |
| W8 记录 | 标题行下缘 287 → 标签行 339（行底 6+14） | 52px = **19.8dp** | ✅ |
| W4 编辑 | 顶栏底 296 → 首内容 349 | 53px = **20.2dp** | ✅ |
| W5 详情 | 顶栏底 296 → 首内容 349 | 53px = **20.2dp** | ✅ |
| W7 穿搭详情 | 顶栏底 296 → 主视觉 349 | 53px = **20.2dp** | ✅ |
| W9 回顾 | 顶栏底 296 → HeroBand 349 | 53px = **20.2dp** | ✅ |
| W10 心愿 | 顶栏底 296 → 分段行 349 | 53px = **20.2dp** | ✅ |
| W11 设置 | 顶栏底 296 → 首卡 349 | 53px = **20.2dp** | ✅ |
| W12 对话 | 容器级 padding 后消息列 y1=349（长历史、滚动任意位置均生效） | 53px = **20.2dp** | ✅ |

- W1 标题行 top 8→12 复核：三 Tab 标题子节点 y1 均为 160（改前 W1 为 150）。
- **W12 施工修正**：初版改 `contentPadding.top=20` 后 dump 实测消息仍顶到栏底（y=296=0dp）——
  reverseLayout 下 contentPadding 是滚动内衬，长历史时初始即越过、衬垫不可见；改
  `Modifier.padding(top=20.dp)` 容器级内衬后复测 y1=349 恒定。右上 48dp「空盒」节点系
  工具 chip ▼ 钮触控热区外扩（截图证实不可见），非间距问题。
- 演示默认开：`run-as rm shared_prefs/demo_mode.xml` 后冷启 → 设置页显示「演示模式 · 内置数据不落盘」
  +「退出演示模式」按钮，三 Tab 演示数据齐全（-PdemoDefault=true 注入生效）；不带参数的常规构建
  `DEMO_DEFAULT=false`，行为与 it-015 一致。
- 回归：Tab 切换、进详情/编辑/回顾/心愿/对话、返回栈均正常，无崩溃；W12 对话历史与工具 chip 渲染正常。
