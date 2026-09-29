# it-070 穿搭视图切换 · 主题设置 · 右滑动画修复

- 状态：**已实施**（2026-09-29；Leo 确认「实施」）
- 提出日期：2026-09-29
- 涉及应用：wardrobe（+ 可能的 libs/carddeck / eats 回归）

## 背景与动机

用户（2026-09-29）基于 W7 穿搭详情截图提出三件事：

1. **成品图 ↔ 单品布局切换**：W7 详情页当前是 if/else —— 有成品图就只显示成品图（`OutfitDetailScreen.kt` L224），人体拼贴（`BodyCollage`）完全不可达；用户想在两种视图间主动切换查看。
2. **主题设置**：应用目前无任何主题偏好，`WardrobeTheme(darkTheme = isSystemInDarkTheme())` 永远跟随系统（`MainActivity.kt` L76），需要「跟随系统 / 亮色 / 暗色」三选一的设置项。
3. **穿搭卡片向右滑动画错**：W8 卡组（`libs/carddeck`）右滑路径存在数学上的非单调——左滑为纯平移+旋转（飞行 1.5w），右滑被 `TUCK_FRACTION = 0.45` 夹住且位移公式 `f(u)` 在 u≈65dp 处导数为零后**反向回退**，观感=卡片不跟手、往回缩、几乎无旋转（`CardDeck.kt` L308–347 placement）。待设备复现确认后修复。

## 涉及的用户故事

- **US-49**（成品图完整呈现）：追加 it-070 条款——W7 支持成品图/单品布局切换视图。
- **US-47**（卡组切换丝滑）：右滑动画修复回归以 UC 为验收底线（125dp/s、单一弹簧、减弱动态降级、wardrobe+eats 双端）。
- **新增 US-61 外观主题设置（W11）**：用户可在设置页选择 跟随系统 / 亮色 / 暗色，选择持久化、立即生效。

## 提案内容

### 1. W7 成品图 / 单品布局切换（US-49 扩展）

- 主视觉区上方（或图片区右上）加 `SegmentedToggleRow` 两段：**成品图 / 单品布局**（复用 `ui/components/SegmentedToggle.kt`，不新造组件）。
- 仅当 `effectImages.isNotEmpty()` 时显示切换（无成品图时本来就是拼贴，默认视图不变）。
- 成品图视图 = 现有 `HorizontalPager + PhotoCard`（页码、删除、effectStale 标注全部保留）；单品布局视图 = 现有 `BodyCollage`（空槽点击进编辑态等 US-48 交互保留）。
- 选中态 `rememberSaveable` 会话内保留，不落盘；默认「成品图」。
- 录入成品图 chip、调整单品入口在两视图下均可达。
- 范围：**仅 W7 详情页**；W8 卡组卡保持「成品图优先、无则拼贴」的现状（卡组手势与视图切换会打架，不做）。

### 2. 主题设置（新增 US-61）

- `PrefsStore`（DataStore `"wardrobe_prefs"`）新增 `theme_mode` 键：`system | light | dark`，默认 `system`。
- `MainActivity.setContent` 收集该 flow，传入 `WardrobeTheme(darkTheme = …)`；三态映射：system → `isSystemInDarkTheme()`，light → false，dark → true。选择后**立即生效**（Compose 重组，不重建 Activity）。
- 修掉 4 处绕过主题的直读：`WardrobeRecapScreen.kt` L359/L645、`DataPackageSection.kt` L117 的 `isSystemInDarkTheme()`——改为读同一个 CompositionLocal（`LocalAppDarkTheme`，由 WardrobeTheme 提供），保证局部预览/长图配色与全局一致。
- 设置页（W11）在「用量」与「关于」之间新增**「外观」卡**：三选一行（跟随系统/亮色/暗色），选中态持久化。状态持有参照 `SettingsViewModel` 既有模式。
- 不做：darkroom / eats 的主题设置（各自应用另行立项）；不写 ADR（纯偏好项，非不可逆技术决策——若确认要记可补）。

### 3. 右滑动画修复（US-47 回归）

- **先复现定性**：`tools/emu.sh` 起 `wardrobe_test`，走查 W8 卡组右滑（含 n≤3 小牌堆、末张回卷、‹n/m› 程序化 previous），确认病灶是 carddeck placement 还是 W7 pager（后者是官方 fling，嫌疑低）。
- 按复现结果修 `libs/carddeck/CardDeck.kt` placement/anchor 数学，目标：右滑与左滑同样 1:1 跟手、方向单调、旋转由位移派生且量级对称、飞出终点一致（不保留 0.45w 与 1.5w 的不对称——除非 it-048 裁剪约束要求，修复前回读 it-048/it-065 的 clip/边界约束再定）。
- 小牌堆（n≤3）右滑若只能回中，给干净的回中动画，不允许瞬移。
- 若改到 `libs/carddeck`：同步 `libs/carddeck/specs/`，**eats 构建/测试回归**（it-047/it-048 先例，提交 scope 为 `wardrobe+libs/carddeck+eats`）。

## 验收标准

1. W7 有成品图时出现切换开关；切到单品布局可见完整 BodyCollage 且空槽可编辑、切回成品图 pager 页码/删除/标注完好；无成品图时不出现开关。
2. 设置页「外观」三选一：选亮色在系统深色下立即变浅、选暗色立即变深、选跟随系统随 `cmd uimode night` 实时切换；重启后保持；recap/长图取色与全局一致。
3. W8 右滑：拖拽 1:1 跟手不回退、越阈值真实飞出（含旋转）与左滑对称；未过阈值干净回中；n≤3 不瞬移；US-47 全部 UC 复测通过，wardrobe + eats `assembleDebug`/`test` 全绿。
4. 弱动态（reduce-motion）下切换/落定即时降级不回归。
5. 视觉走查截图回填本文件「验证记录」。

## 影响范围

- `app/.../ui/records/OutfitDetailScreen.kt`（切换开关 + 视图重组）
- `app/.../ui/settings/SettingsScreen.kt`、`SettingsViewModel.kt`（外观卡）
- `app/.../data/prefs/PrefsStore.kt`（theme_mode）、`MainActivity.kt`、`ui/theme/WardrobeTheme.kt`（三态 + Local）
- `app/.../ui/recap/WardrobeRecapScreen.kt`、`DataPackageSection.kt`（直读改 Local）
- `libs/carddeck/.../CardDeck.kt`（右滑修复，视复现而定）+ `libs/carddeck/specs/`
- specs 常青同步：`01-user-stories.md`（US-49 条款 + 新 US-61 + US-47 若验收变化）、`02-wireframes.md`（W7 切换、W11 外观卡）、`05-design-system.md`（动效清单若改卡组右滑）
- CHANGELOG、本文件验证记录

## 验证记录

**构建与测试（2026-09-29）**

- wardrobe：`./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true` → **BUILD SUCCESSFUL，91 测全过（0 失败）**。
- eats：`:app:assembleDebug` → BUILD SUCCESSFUL（carddeck 为双端共享内核，it-047/048 先例回归）。

**AVD 走查（wardrobe_test / emulator-5558）**

- ✅ **W7 视图切换（验收 1）**：有成品图的穿搭出现「成品图 / 单品布局」两段切换；切「单品布局」可见完整人体拼贴、空槽点击进编辑态（US-48 交互不变）；切回成品图轮播页码/删除/it-068「成品图为调整前组合」标注完好；无成品图穿搭不出现开关；默认「成品图」、会话内（rememberSaveable）记住选择。
- ✅ **主题三态（验收 2）**：设置页「外观」卡即点即生效——系统深色下选「亮色」截图亮度均值 ≈222.3、选「暗色」≈54.7（像素采样实证变浅/变深）；「跟随系统」经 `cmd uimode night yes/no` 双向实时切换（两方向截图核对，设置页保持 ✓跟随系统）；`theme_mode` prefs 持久化、默认 system，杀进程重开保持。recap/数据包长图取色改读 `LocalAppDarkTheme`（与全局同一暗色判定，源码评审 3 处直读已收敛）。
- ✅ **右滑两段式归入（验收 3 · 动画形态）**：PIL 逐帧核对——拖拽段右缘随手指单调递增（无旧版中途倒车），松手越阈值后单向下潜平滑归入牌堆第 1 层终点、上一张从左侧滑入盖过顶卡（z 抬高一层），提交瞬间零跳变。
- ✅ **冷启动首滑提交（验收 3 · 本会话新增修复）**：插桩日志实证旧缺陷——fling 终帧落锚（412.19998）后 `performFling END` 即止，观察者无任何触发，提交丢失直到二次按下被 `onDeckDown` 补上；根因为 snapshotFlow 只对计算块内读过的状态重触发，三重门在 collect 体外读、`flingInProgress` 翻 false 唤醒不了流。门状态改经 `Triple(offset, staticGate, flingGate)` 读进计算块后：冷启动首滑 1/8→8/8 即时提交（commit 紧随 `performFling END`），后续右滑/慢拖/左滑 8/8→7/8→7/8→8/8 逐张推进无吞提交。插桩已全部移除（grep 0 残留），复位 `animator_duration_scale=1`。
- ⏳ **未专项复测**：n≤3 小牌堆禁锚/末张回卷（端点与 flingTarget 未动，it-047/048 已覆盖）、reduce-motion 降级（该分支零改动）、真机（留顺手复验）。
- **spec 同步**：01-user-stories（US-47/US-49 条款 + 新 US-61）、02-wireframes（it-070 节：W7 切换/W11 外观卡/W8 右滑注记）、05-design-system（动效 #12 it-070 修订）、`libs/carddeck/specs/00-overview.md`（提交管线门读入块内 + it-070 修订节）均已同步；06-decisions 无 ADR（主题为纯偏好项、右滑为缺陷修复，非不可逆决策）。
