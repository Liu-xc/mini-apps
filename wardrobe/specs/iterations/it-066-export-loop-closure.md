# it-066 · 导出回程锚点、长图放大核对与空态行动按钮

状态：**已实施**（2026-09-29；AVD 走查部分项待复验，见验证记录）

## 背景与动机

来源：2026-09-29 全量 review（常青 spec + W1–W13 全部界面源码静态走查）。核心闭环「录入 → 组合 → 导出 → 切去生图 Agent → 回录成品图 → 沉淀」的去程顺畅，本轮修三个 P1：

1. **回程断裂**：US-09「录入成品图」的入口只在 W6 导出面板（打开期间）与 W7 穿搭详情（已保存的穿搭）。用户复制长图后关闭面板去生图，回到 App 时 W1 没有任何「录入成品图」入口——核心闭环的回程无锚点。数据层其实已支持（`importEffectImage(outfit=null)` 按当前组合自动创建穿搭并挂图，[AppViewModel](../../app/src/main/java/com/leo/wardrobe/ui/AppViewModel.kt)），缺的只是 UI 入口。
2. **长图预览不可核对**：it-017「已知限制」与 it-042 C5 两次挂账。现状预览高 42% 屏 + ContentScale.Fit，长图（典型 1024×~3500）显示宽度仅约 28% 屏宽，Prompt 与拼贴细节不可读——用户实际在「盲发」素材给生图 Agent。W5 已有放大核对的交互先例，W6 一直未跟进。
3. **空态违基线**：DESIGN.md §5.8 要求空态必须有插画/图形 + 行动按钮。W8 两个空态（「还没有穿搭记录」「该标签下没有穿搭」）均无行动按钮；W3 筛选空态也没有「清除筛选」。

## 涉及的用户故事

- **US-09 录入成品图（增补）**：入口从「导出面板 / 穿搭详情」扩为「导出面板 / 穿搭详情 / 搭配页回程提示条」——数据层语义不变（未收藏直接录入 = 自动创建穿搭）。
- **US-07 导出生图素材**：预览可核对是本 US 验收面的补强（复制前能看到长图实际内容）。
- **US-10 浏览穿搭记录**：两个空态补行动按钮（去搭配 / 清除筛选）。
- **US-03 品类分组浏览**：W3 筛选空态补「清除筛选」。
- 关联：US-58（顾问卡「复制长图」同走 W6 面板，预览放大同样受益，不需单独接线）。

## 验收标准

### W1 搭配页（回程锚点）

- 本会话内成功「复制长图」后，回到 W1（面板已关闭）时顶栏下方出现轻量提示条：「刚复制过这套 · 生图回来录入成品图 ›」（it-036 混入心愿提示条同款形制，Material 图标、无 emoji）。
- 点提示条直接拉起相册选择器，选择后走 `importEffectImage(null, 复制时的组合, uri)` 自动建穿搭并挂成品图；录入成功 toast 后提示条消失。
- 提示条仅会话级（VM 内存，不持久化；进程重启自然消失）；再次复制覆盖旧值。
- **愿望件护栏**：复制的组合含愿望件（isWishSlot）时不显示提示条——心愿组合的回程仍走心愿/搭配页，不自动建正式穿搭（与 W6「收藏这套」置灰同一语义）。
- 提示条不与「混入心愿」提示条同屏叠加（互斥或纵向堆叠择一，实现时以简单为先）。

### W6 导出面板（预览放大核对）

- 点按长图预览 → **原位全幅展开**：表单区（画面设定折叠行、补充信息、生成文案、收藏/录入行）收起，预览占满滚动区可用高度、宽度铺满、自身纵向可滚全览；再点按（或收起角标）恢复原布局。
- 展开态下底部固定动作栏（复制长图｜存相册｜分享｜只复制文本）保持可见可达。
- 展开态有明确退出控件（角标，热区按 it-028 媒体卡角标例外 ≥36dp）。
- **不使用 Dialog 叠加在 sheet 之上**——DESIGN.md §2.5 禁弹窗套弹窗，故选原位展开方案。
- 展开/收起动画为 **220ms crossfade + 0.985 微缩放**（it-058 C4 Tab 切换同语言，常规内容切换预算；实施修订：原提案写 EditorialMotion.smooth 高度过渡，落地取模式切换更稳的 crossfade 口径），跟随系统「移除动画」降级。
- it-017「已知限制」与 it-042 C5「预览放大查看留待后续迭代」挂账**销账**。

### W8 穿搭记录（空态行动按钮）

- 「还没有穿搭记录」空态：新增实底按钮「去搭配一套」→ 切换到搭配 Tab（接线先例：it-031 C10 W9「去打卡」onGoRecords）。
- 「该标签下没有穿搭」空态：新增按钮「清除筛选」→ `filterTag = null`。
- 两处按钮触控 ≥44dp。

### W3 衣橱（筛选空态）

- 「该筛选下没有衣物」空态新增「清除筛选」按钮 → 同时清 `categoryTab` 与 `filterTag`。
- 主空态（「衣橱还空着」）维持现状——右下角 FAB 常驻即行动入口（it-063 语义），不重复加按钮。

### 全局质量

- 沿用 it-052 黑白灰 token、EditorialMotion 与触感基线；不新增色彩、音效或无意义动效。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true` 全绿。
- Android 14 演示包走查 W1（复制→关面板→提示条→录入）/ W6（展开/收起/滚动全览）/ W8 / W3（空态按钮），截图与 UI dump 回填「验证记录」。

## 实施方案

1. `AppViewModel.kt`：新增会话级 `lastExportedItems: StateFlow<List<String>?>`（真实单品 id，复制成功时写入、录入成品图成功后清空；不持久化）。
2. `ExportSheet.kt`：复制成功回调处 `vm.markExported(items 真实件)`；预览 Box 增加 `expandedPreview` 状态——展开态外层内容列去 `verticalScroll`（内容只剩标题+预览），预览改 `weight(1f)` + 自身纵向滚动 + 收起角标；收起态维持现结构，`AnimatedVisibility` 过渡表单区。
3. `OutfitScreen.kt`：顶栏下方渲染回程提示条（`lastExportedItems` 非空时显示；含愿望件的复制不写入该状态）；点按 `rememberPhotoPicker` → `vm.importEffectImage(null, ids, uri)`。
4. `RecordsScreen.kt`：两处 `EmptyState` 传 `actionLabel/onAction`；新增 `onGoOutfit` 回调，`MainActivity.HomeTabs` 接 `tab = Tab.OUTFIT`（同 onGoRecords 先例）。
5. `WardrobeScreen.kt`：筛选空态 `EmptyState` 传「清除筛选」action。
6. spec 同步 + 记录：`02-wireframes.md`（W1 提示条、W6 预览展开交互与 it-017 限制销账、W8/W3 空态注记）、`01-user-stories.md`（US-09 入口增补；US-03/US-10 空态行动按钮入验收）、必要时 `05-design-system.md` 预览展开组件行为小注；`CHANGELOG.md` 记一行。

## 影响范围

- **代码**：`ui/outfit/ExportSheet.kt`、`ui/outfit/OutfitScreen.kt`、`ui/records/RecordsScreen.kt`、`ui/wardrobe/WardrobeScreen.kt`、`ui/AppViewModel.kt`、`MainActivity.kt`（仅回调接线）。
- **常青 spec**：`01-user-stories.md`、`02-wireframes.md`、（如需）`05-design-system.md`。
- **不涉及**：数据模型与存储格式、抠图域、顾问域、carddeck、导航路由结构。
- **本迭代明确不含**（review 其余项去向，防丢）：W7 有成品图后可否调整单品（需产品决策，拟 it-067）；空组合按钮 enabled、切 Tab 丢筛选状态、W8 列表结构、日期语义统一、触控/图标/文案一致性细项（拟 it-068 打包）。

## 验证记录

**构建与测试（2026-09-29）**

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：**BUILD SUCCESSFUL**，89 个单测全过（0 failure），演示包 APK 产出并装入 AVD。

**AVD 走查（Android 14 演示包，emulator-5556，证据存 `reports/2026-09-29-it066/`）**

- ✅ **W1 回程提示条（核心）**：复制长图成功后回到 W1，提示条「刚复制过这套 · 生图回来录入成品图 ›」实机渲染（PhotoCamera + chevron、任务卡形制），与复制成功 toast 同框（截图 `02-w1-banner-toast.png`）——`markExported` 写入与条件渲染链路实证。
- ✅ **W6 预览展开（核心）**：点按预览 → 表单收起、长图宽度铺满、Prompt 文字完整可读（it-017/it-042 C5 挂账点销账）、预览自身纵向可滚全览、底部动作栏保持可见可达（截图 `03-expanded-readable.png`）。
- ✅ **W6 收起**：右上角标（印刷点语言）点击生效，恢复 42% Fit 全貌 + 表单 + 展开角标（截图 `01-w6-sheet-badge.png` / `04-collapsed-restore.png`）；uiautomator dump 证 a11y 文案「穿搭长图（点按放大查看）」双态切换。
- ⏳ **W1 提示条点按录入后消失、W8/W3 空态按钮**：待复验——本轮 AVD 被并发会话（darkroom 走查）高频抢占焦点（`dumpsys window` 证 mCurrentFocus 反复跳 darkroom），系统相册选取与空数据态未取得干净证据。代码路径为条件渲染（`clearExportedMark` 随录入成功 / `EmptyState(actionLabel)`），风险低，复验在 AVD 空闲时补。

**经验沉淀**：LESSONS.md 新增 1 条（screencap 旧帧识别：帧内状态栏时钟对不上即旧帧，连拍不救，改用 dumpsys/uiautomator 定状态）。
