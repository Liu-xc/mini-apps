# 05 · 设计系统（黑白灰时装编辑风 × 高表现力动效）

## 视觉基调

时装杂志内页的气质：纸白/炭黑/石墨灰、衬线展示标题、大图为主角、克制的排版层级。**照片永远比装饰重要。**（it-052：衣橱应用由淡绿纸感切换为全局黑白灰，照片与成品图保留原色。）

> 素材来源（2026-09-20 起）：应用图标与 8 品类 3D 图标（衣架/T恤/外套/下装/连衣裙/鞋/包/帽子/配饰）取自 [thiings.co](https://www.thiings.co) 免费素材（个人非商业用途，署名见 README）。

## 色彩 Token（浅色 / 深色）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `paper` 背景 | `#F7F7F5` 纸白 | `#111110` 炭黑纸 | 全局背景 |
| `surface` 卡片 | `#FFFFFF` | `#1B231B` | 卡片/弹层 |
| `ink` 主文字 | `#111111` | `#F2F2EF` | 标题/正文 |
| `inkFaint` 次级 | `#6B6B67` | `#A6A6A0` | 序号/说明/占位；两套均保持 ≥3:1 |
| `accent` 图形强调 | `#111111` | `#F2F2EF` | 选中态、图形焦点、主强调 |
| `accentContent` 强调文字/动作 | `#111111` | `#F2F2EF` | 强调文字、链接与动作前景 |
| 主强调实心按钮文字 | `#FFFFFF` | `#111110` | `onPrimary` / `onSecondary`，黑白反差 |
| `hairline` 分隔 | `#D9D9D4` | `#393936` | 细分隔线 |

标签 chip：`paper` 底 + `ink` 字（浅）；深色反之。破坏性操作用系统 error 色。

衣橱不再使用品牌绿或其他装饰色。浅色实心主按钮使用 `primary=#111111` + 白字，深色反向；正文最低 4.5:1，次级文字最低 3:1，实心按钮文字最低 4.5:1（DESIGN.md §2.2）。系统 error 只用于破坏性操作和错误反馈。

## 字体与排版

| 层级 | 字体 | 用法 |
|---|---|---|
| Display | **FontFamily.Serif**（系统 Noto Serif CJK）· 30sp/38sp · semibold | 角色名、页面大标题、单品名 |
| Title | Serif 20sp/28sp · medium | 卡片标题、弹层标题 |
| Section | Sans 15sp/22sp · semibold | 小节标题、分组标题 |
| Body | 系统 Sans 15sp/23sp | 描述、评论、表单输入 |
| Action | Sans 14sp/20sp · medium | 按钮、导航、可操作行 |
| Meta | Sans 11sp/16sp · medium | 日期、数量、品类、状态；中文默认零字距 |

> 衬线用系统字体而非打包 Noto Serif SC：安卓中文机普遍内置 Noto Serif CJK，APK 减重 ~20MB；若真机缺衬线再考虑打包（见 ADR-005）。

## 形状与间距

- 卡片圆角 20dp；弹层顶角 28dp；chip 圆角 8dp（胶囊感弱化，更"印刷"）
- 触控目标 ≥44dp（it-028）：评论删除钮 44dp；媒体卡悬浮角标（卡片「···」菜单）命中区 36dp（DESIGN.md §2.5 例外档），视觉尺寸不变
- 屏幕边距 20dp；槽位之间 hairline 分隔；照片卡宽高比 4:5（衣物的自然比例）
- 顶栏↔内容节奏（it-045）：**顶栏视觉下缘 → 首个内容元素 = 20dp**，全站一致（分段控件/筛选行算内容元素）；三 Tab 自绘标题行距状态栏统一 12dp。空态插画块自带 28dp 内边距，不受此条约束
- 槽位卡内容呈现（it-046）：**常规格（0.6/0.78/0.85）照片 Crop 撑满卡格、无衬纸留边**；帽(1.0)/鞋(2.6) 极端比例裁切会伤物（帽檐/鞋底出界，内容包围盒实测），保留 Fit + **固定浅纸 `#F2F3F5`**（主题无关——旧衬纸随深色变黑即「黑边距」缺陷根因）
- 阴影极轻或无，层次靠留白与 hairline

## 动效清单（统一弹簧基调：EditorialMotion）

> material3 1.4.0 稳定版未公开 Expressive motionScheme（ADR-004），全局动效由 `EditorialMotion` 统一弹簧参数承担：`smooth`（高阻尼丝滑）/`pop`（轻过冲）/`bouncy`（弹跳）。

| # | 动效 | 实现要点 | 触发处 |
|---|---|---|---|
| 1 | 槽位轮播 | ~~HorizontalPager + graphicsLayer 缩放形变~~ → it-003 起为 3×3 迷你格内 HorizontalPager（吸附换衣保留；迷你尺寸下取消缩放/透明形变）；it-047 参数收敛：2 处 `HorizontalPager`（`SlotGrid.kt` 槽位、`OutfitDetailScreen.kt` W7 轮播）显式 `PagerDefaults.flingBehavior(snapAnimationSpec = EditorialMotion.smooth())`、`beyondViewportPageCount = 1`（1.7.0+ 参数名），`SlotGrid.kt` 序号跳页 `animateScrollToPage` 同传 `animationSpec = EditorialMotion.smooth()` | W1、W7 |
| 2 | 🎲 老虎机 | 逐槽位 `animateScrollToPage` 随机目标（it-047 显式 `animationSpec = EditorialMotion.smooth()`），槽间 100ms stagger；落定轻弹 scale 1→1.03→1 已落地（it-047：`snapshotFlow { settledPage }` 触发，`tween(80)` 上行、`EditorialMotion.pop()` 回落；启动 1.5s 内不弹，入场恢复不产生动效） | W1 |
| 3 | 共享元素 | `SharedTransitionLayout` + `Modifier.sharedElement`：卡片照片→W5 大图、穿搭格→W7 成品图，无缝放大 | W1→W5、W8→W7 |
| 4 | 复制成功 | 按钮内容 AnimatedContent morph 成 ✓，同时 Canvas 自绘彩屑粒子（15-20 粒，砖红/墨黑/米白三色，重力下落 600ms） | W6 |
| 5 | 滑动删除 | Material3 `SwipeToDismissBox`，背景显现删除图标，删除后 `animateItem` 淡出回落 | W3 |
| 6 | 切角色 | 数据区 `Crossfade`，角色名 `slideInVertically`+fade | W2 确认后 |
| 7 | 列表入场（it-028 落地） | `StaggeredEntrance`：24ms 错峰 fade+上移 28f，`rememberSaveable` 标志**仅首进播放**（DESIGN.md §3 预算）；删除/重排走 `animateItem()` | W3/W8 |
| 8 | 空状态 | Lottie 动画（衣架/晾衣绳插画，取自 LottieFiles 免费资源，json 放 res/raw） | W1/W3/W8 空态 |
| 9 | 收藏 ☆→★ | scale 心跳 1→1.2→1 + accent 着色 | W6/W7 |
| 10 | 底部弹层 | ModalBottomSheet（M3 弹簧），导出面板内容 staggered 淡入 | W2/W6 |
| 11 | 统计数字 count-up（it-027） | `CountUpText`：Animatable 首进 0→N 起数、档位切换旧值过渡，`EditorialMotion.smooth()`，三格 60ms 错峰 | W9 三大数字 |
| 12 | 卡组飞出/随机抽取（it-047 自研内核，弹簧承 it-046 基准） | `libs/carddeck` 换官方 `AnchoredDraggable` 自研内核（三方 compose-swipeable-cards 依赖与 JitPack 仓库已删）：**单一弹簧源** `DeckStyle.flyOutSpec = spring(0.9, 500)`（≈0.32s 到位、<0.5% 过冲，替代旧库默认 spring(0.6,100) 的 1s 晃尾）——手势落定/程序化 `animateTo`/回中共用，旋转=位移/50 派生无第二弹簧；甩出判定 = 速度 ≥125dp/s 或 100dp 位置阈值（自研 `flingTarget`，官方 computeTarget 在 v=0 只取最近锚点、位置阈值不参与）；`drawRandom` 步距 420→560ms 与飞行同量级（旧 55ms 连发=多张叠飞撕裂；实测 483–607ms 含提交开销）、飞出 300–360ms 首达截停、末张回卷甩出+揭示不再瞬移；‹n/m› 精确 ±1（`committedTarget` 幂等提交门）；W1 序号 n/m 末页回卷即时落位保持。**it-048 修订**：提交信号改到达帧观察（offset 精确到锚 + 三重落定门）+ 按下快进结算 `onDeckDown`——连滑间隔小于飞行动画时长也逐张推进（旧 settledValue 翻转模型吞同向二次甩出，实测「连续滑动滑不动」）；卡组容器**不得 clipToBounds**（W8 甩卡真实飞行需溢出空间，it-031 旧库时代的包裹已删） | W8 卡组、W1 槽位序号 |
| 13 | 减弱动态降级（it-047） | 读 `Settings.Global.ANIMATOR_DURATION_SCALE == 0`（ContentObserver 实时感知，切换无需重启）→ 事实源唯一、分层双入口：carddeck `rememberDeckReduceMotion()`（W8 卡组，`CardDeck` 默认参数接线）与 `EditorialMotion.reduceMotion()`（W1/W7 pager），调用点不散写。W8：手势/程序化落定 `snap(0)` 即时、`drawRandom` 每步即时落位 + 每步一次 Confirm 震（保留步距节奏，最终落定震仍归 app 层）、拖拽保持 1:1（输入非动画）。W1/W7：`EditorialMotion.pagerFling` 降级返回自实现瞬时吸附（就近整页直接落位）——官方 `SnapFlingBehavior` 的 decay+snap 被钉死在 scale=1 的 withContext（foundation 1.8.3 字节码实证），不吃系统缩放，仅改 snapAnimationSpec 不够；`animateScrollToPage`/轻弹 Animatable 走框架 MotionDurationScale 自动降级；对应 DESIGN.md §3「跟随系统『移除动画/减弱动态』无障碍设置整体降级」 | 系统动画缩放=0 时的 W8 卡组 + W1 槽位/序号 + W7 轮播 |

## 触感反馈（it-027 · DESIGN.md §4 基线）

`ui/components/Haptics.kt`：`confirm()`（API 30+ CONFIRM，低版本回退 LONG_PRESS）/ `error()`（REJECT / VIRTUAL_KEY）/ `tick()`（CLOCK_TICK），无声音。接线：复制长图 ✓、存相册 ✓、☆保存这套、🌟存为心愿、穿搭打卡（含再记一次）、去背景成功、角色编辑/新建保存、心愿「收进想买/保存」、心愿购入转正、心愿穿搭升级 = **confirm**（it-027 + it-028）；评论发送、撤销今日打卡、还原原图 = **tick**；未就绪点保存的 toast = **error**。滚动/导航/输入不加触感。

卡组甩卡触感（it-047 登记，DESIGN.md §4 口径）：拖拽越过 100dp 阈值（`DeckStyle.swipeThreshold`）给一次 `GestureThresholdActivate`——仅真实按压期有效、越过一次即解除武装（位移回落到阈值内再 re-arm），`DeckStyle.enableHapticOnThreshold` 默认开；抽中落定一次 **Confirm** 由 app 层接线（kernel 不在正常模式重复触发）——wardrobe 随机一套抽完 `haptics.confirm()`（it-047 新增）、eats 沿用 it-015「随机抽中落定」既有接线；helper `HapticFeedback.performConfirm()` 内置在 carddeck（API 30+ Confirm / 低版本 LongPress 兜底），减弱动态下 `drawRandom` 每步的 Confirm 由 kernel 触发（见动效清单 #13）。滚动/导航不加触感的基线不变，卡组路径与既有接线不叠双震。

## 参考实现（写代码时对照）

- 官方 Compose Animation 文档与 cheat sheet：developer.android.com/develop/ui/compose/animation
- 轮播卡片动效系列：sinasamaki.com/creating-pager-animations-in-jetpack-compose
- 图片轮播 graphicsLayer+lerp：ProAndroidDev《Swipeable Image Carousel with Smooth Animations》(2025-03)
- 真机动画调优示例集：github.com/skydoves/compose-animations
- Material 3 Expressive / motion：m3.material.io/develop/design-system/material-3-expressive

## 组件清单（ui/components/）

`PhotoCard`（4:5 照片卡，支撑轮播形变）、`SlotPager`（品类槽位）、`TagRow`/`TagChipInput`（标签展示与录入）、`CommentTimeline`（评论时间线+输入）、`EmptyState`（Lottie+文案+行动按钮）、`EditorialHeader`（角色名+衬线排版）、`FilterChipsRow`（标签筛选条）、`FadingScrollRow`（横滑筛选行+右缘渐隐，it-036）、`SegmentedToggleRow`（连体分段，it-036）、`fadingBottomEdge`（滚动容器底缘渐隐，it-042）。

## 照片容器「衬纸」（it-011 C5）

| Token | 值 | 用途 |
|---|---|---|
| `photoMat` 浅色 | `surfaceVariant @ 55%` | 单品照片统一浅底圆角容器（W3 网格/W5 大图/W7 单品行），ContentScale.Fit 完整呈现轮廓 |
| `photoMat` 深色 | 同上（暗色 surfaceVariant） | 深色模式同构 |
| `photoMat` 固定浅纸（it-046） | `#F2F3F5` 实色 | **仅 W1 槽位帽/鞋格**（aspect≥0.95 保全衣物的 Fit 例外档）；主题无关不变黑。W1 常规格已改 Crop 撑满、无衬纸（见「形状与间距」it-046 条） |

成品图（用户导入/生图产出）：W7 详情轮播保持 0.86 近原比全幅 Crop（竖图头部余量足）；**W8 卡组 hero 自 it-042 改衬纸 Fit**——原全幅 Crop 在宽盒（≈1.1）里把 0.8 竖图人物头部裁掉，与同页网格缩略两种呈现打架（05 原「成品图一律全幅 Crop」条款作废）；后续接抠图能力时把衬底换透明即可。

## 透明底棋盘格 + 去背景状态（it-016 US-15）

| 元素 | 规格 |
|---|---|
| 透明棋盘格 | 12dp 方格双色 `#F2F3F5` / `#E1E3E8`，drawBehind 垫于预览图下，圆角与照片容器一致（20dp clip） |
| 去背景按钮 | OutlinedButton 全宽 44dp，AutoFixHigh 18dp 图标 + 「去背景 · 一键透明底」；推理中禁用态：18dp CircularProgressIndicator + 「正在去背景…」 |
| 成功横条 | secondaryContainer@55% 圆角 12dp：CheckCircle(primary 18dp) + 「已去背景 · 透明底」+ 尾部 TextButton「还原」 |
| 弹簧基调 | 棋盘格显隐随预览图 crossfade（220ms，与 PhotoCard 一致），无新增动效 |

## 触控目标基线（it-033）

所有**可点**的图标 / chips / 翻页 / 删除控件，触控区 **≥44dp，推荐 48dp**。图标的视觉尺寸允许小于触控区：视觉保持原规格，差值用最小交互尺寸机制补足（显式 `Modifier.size(48.dp)` 热区、外层点击盒、或组件自带的最小交互尺寸），不靠放大图标本体凑数。

- **依据**：2026-09-24 走查 uiautomator bounds 实测（420dp 密度）——W3 标题行图标/卡 ⋮ ≈18dp、W4 品类 chips 19dp、W5 标签 chips 15dp、W8 分页 15dp、W5/W7 评论 × 14dp，均低于 44dp 下限。
- **落点**：W3 📊/🌟 = 48dp、卡 ⋮ = 48dp；W4 品类 chips = 48dp、行距 ≥8dp；W5 标签 chips = 44dp；W8 分页胶囊左右半边 = 48dp；评论删除 × = 48dp；W1 名称条 ✕ = 28×44dp（窄格名称列预算优先，高度达标即可）。
- 与「形状与间距」的 it-028 44dp 条目一脉相承：it-033 把推荐值提到 48dp；W3 卡 ⋮ 自此按 48dp 落地，DESIGN.md §2.5 给媒体卡悬浮角标的 36dp 例外档对它不再适用（例外档保留给其余仍受版面约束的媒体卡角标）。截断基线（名称先缩字号→两行→省略、卡片标题 maxLines/minLines=2）见 spec 02 的 it-033 注记。

## 筛选行渐隐与分段控件（it-036）

- **横向筛选行右缘渐隐（全站规范）**：所有横向可滑的筛选/chips 行（W3 品类、W8 标签、W10 品类）右缘叠 **24–32dp**（落地 28dp）`Brush.horizontalGradient(透明 → 页面底色 paper)` 渐隐，暗示右侧还有内容可滑；**仅内容超出一屏时显示、滑到尽头自动隐去**（一屏放下不画，避免误导）。渐隐止于滚动容器右缘，行尾固定按钮（W3「筛选」）排在渐隐之外，不叠按钮底板。共享实现：`ui/components/ScrollFade.kt :: FadingScrollRow`。只加边缘渐隐，chips 本体形制/热区归 it-033 基线管。
- **分段控件全站统一为 W9 连体规格**：等分 N 段、`SegmentedButtonDefaults.itemShape` 相连圆角、M3 默认选中填充（选中段容器填充 + 勾选图标），同高同圆角；共享实现 `ui/components/SegmentedToggle.kt :: SegmentedToggleRow`——W9 顶栏「今年/累计」与 W10「想买单品/心愿穿搭」两页共用同一 composable。新页面做二/三档切换一律复用本组件，不再另起「两个 FilterChip 并排」的伪分段形制。

## it-037～039 交互与颜色收尾

- **底部导航选中态**：选中项使用 pill + 强调图标，文字始终使用中性色；与 W2「浅底 + 使用中」一样避免重复叠加状态符号。
- **固定操作入口**：W3 全宽底部 CTA 与滚动内容分区布局，卡片不进入其点击区域；操作热区至少 48dp。
- **名称与标签滚动**：W1 窄槽名称一行省略且保持完整 a11y 名称；共享 TagInput 两个横向行在可继续滚动时使用 `FadingScrollRow` 右缘 28dp 渐隐。
- **对比度语义色**：`EditorialColors.accent` 与 `accentContent` 均为中性黑/白，Material `primary`/`secondary` 用于实心动作底色。浅色固定 `primary=#111111`、按钮字白色；深色使用 `#F2F2EF` 并配炭黑纸/深色容器。次级文字 token 调为浅色 `#6B6B67`、深色 `#A6A6A0`。

## it-042 第五轮走查修复（2026-09-25）

- **滚动容器底缘渐隐（C8，与筛选行渐隐同语言的纵向版）**：内容可继续向下滚动时，容器底缘叠 **28dp** `Brush.verticalGradient(透明 → 页面底色 paper)`，滚到底/一屏放下即隐；判定 lambda 在 draw 期求值（滚动只触发重绘，不引发组合帧重组）。落点：W1 槽位滚动列、W3 衣橱卡网格。共享实现 `ui/components/ScrollFade.kt :: Modifier.fadingBottomEdge`。
- **拉丁小字字距（C7）**：`labelSmall` 全局默认 `letterSpacing=0.sp`，中文标签不拉开；短拉丁标签需要编辑风字距时局部 `copy(letterSpacing ≈ 0.08em)`，域名、日期与连续句子保持归零。
- **状态文字分读（C1）**：W2「使用中」与角色名之间固定 **8dp** 间距（it-037 删 ✓ 后不再有自然分隔，状态词不得与名称粘连成词）。
- **完整名称可见兜底（C4）**：W1 槽位照片区长按经统一 toast 通道显示当前单品完整名称（单行省略与 a11y contentDescription 之外的可见路径）；单击/滑动/角标/✕ 行为不回退。

## it-043～044 新三面审美收口（2026-09-25）

来源：[新三面走查报告](../../../reports/2026-09-25-wardrobe-newui-audit/)（W11/W12/W5，P0×0 / P1×7 / P2×14）落地 O1–O7。

- **主 CTA 实心化（C5）**：页面唯一主动作（W5「去背景 · 一键透明底」）一律实心填充 `primary(#111111) + 白字`，深色模式反向，禁止全宽描边幽灵态承载主操作；描边形制保留给次要/入口动作（如 W11「开始对话」）。
- **关键指引不降级（C1）**：空态引导、颜色/材质描述、工具结果正文等「用户必须读」的文本用 `ink`（14.6:1）；`inkFaint`（3.9:1，达标 ≥3:1）只服务次要说明与元信息（时间戳、计数口径、占位）。
- **状态条容器统一（C7）**：W5 补抠状态条（候选/已抠）与 W12 错误条同用「圆角容器 + hairline 边」形制；**棋盘格 = 透明底** 语义以状态条下 `bodySmall` 注记明示（候选态专属）；候选大图**点按进入放大核对**（固定 2× + 拖动平移对话框），保留/还原前可查边缘。
- **字体声部分工（C10 定案）**：`displaySmall/headline/titleLarge`（衬线）= 页面标题、实体品名、卡片标题；`titleMedium`（无衬线粗）= 小节标题（「这件衣服穿过这些穿搭」「用量/关于」等）——两套声部为**既定分工**，同屏并存不算违规；新增小节标题沿用 titleMedium，不新造字体角色。
- **用量口径（C6 附带）**：token 计数一律标注「累计」（`累计 N tokens`），输入/输出拆分为副行；count-up 过渡保留。
- **标签 chips 间距（C9）**：`TagRow`/`TagInput` 横向行距统一 **10dp**（原 6dp 视觉过挤）；触控仍走 it-033 ≥44dp 基线。
- **表单右缘对齐（C9）**：W11 下拉行内容（标签+caret）右缘与输入框右缘同线（TextButton `contentPadding` 水平归零）；其余成行控件右缘对齐同一条 20dp 栅格线。
- **W5 图区内边距（C9 部分）**：外框三态统一 20dp 栅格；`mat` 衬纸内留白与候选态棋盘格 edge-to-edge 属**语义差异**（衬纸承图 / 棋盘示透明），不强行统一——本条为设计决定，后续走查不再报。

## it-049 透明衣物与穿搭编辑

- **去背景默认化**：W4 新选照片自动推理；推理态保持原图仍可见，成功候选用棋盘格明确标注透明，提供「还原」回退；失败保持原图、可重试。W5 已录入衣物仍使用常驻补抠状态条。
- **拼贴衣物融底**：透明衣物以 `ContentScale.Fit` 直接叠在整张人体淡底上；单件不再包独立不透明 Surface，避免每件衣服出现白色方块。虚线空槽维持当前 ink 文字/边线对比，新增点击/键盘可访问语义。
- **W7 编辑反馈**：编辑态点衣物替换、点虚线槽补齐，编辑中给明确说明；顶栏 48dp 保存/取消命中区沿用全站触控基线，退出不保存草稿。
- **成品图完整性**：W7 成品图用 `PhotoCard(mat=true, Fit)`，淡衬纸承接图片比例差异，不裁切主体；W8 卡组缩略已有独立 Fit 规则（it-042）。

## it-051 W6 导出生图表单（2026-09-28）

- **层级**：长图预览是内容主角；「画面设定」是唯一使用轻 `surfaceVariant` + 1dp `hairline` 的任务卡；「补充信息」与「生成文案」用 `titleMedium`（Sans）作小节标题，输入字段以常驻 label + `labelSmall` 元信息说明，不让 placeholder 独担语义。
- **画面设定行**：场景首行的 label/value 用 `titleSmall`，其余行使用 `labelLarge`/`bodySmall`；未设置值用 `inkFaint`，已设置值用 `accentContent`。每行最小 52dp、chips 最小 44dp，行内 ChevronRight 明示可编辑性。
- **展开**：仅当前编辑行以内联 `AnimatedVisibility` 展开；展开/收起为 `EditorialMotion.smooth` 的高度过渡加短淡入淡出，不使用 bouncy，不另开弹层。场景空值首次自动展开不是入场庆祝；减弱动态遵循 Compose 动画缩放降级。
- **色彩**：卡片使用极低占比中性提示（场景行 `primary@5.5%`），实底强调仍只属于底部「复制长图」；输入容器 `surfaceVariant@34%`、描边 `hairline`，聚焦才用中性 accent，遵守 DESIGN.md 的 <10% accent 覆盖约束。

## it-050 顶层顾问（2026-09-28）

- **W12 列表**：底部 Tab 用 Material `SmartToy` 图标与文字「顾问」，每项为 20dp Surface 卡，标题走现有 serif title、摘要/时间走 Sans 次级文本；整卡触控区 ≥44dp。
- **只读状态**：未配置 Key 时不以 error 红制造失败感，使用 `surface + hairline` 说明条及「去配置」恢复动作；空态仍须具备图形、说明与行动按钮。
- **缓存状态**：W13 的「已使用测试缓存」只用 `labelSmall + inkFaint`，不当作成功 toast 或动画，不抢消息内容主次。

## it-052 全局黑白灰与排版定稿（2026-09-28）

本节覆盖早期视觉注记中关于绿色、淡绿纸感和全局宽字距的旧值；旧值保留在历史迭代记录中，但实现与当前页面以本节为准。

### 中性灰阶 token

| Token | 浅色 | 深色 | 用途 |
|---|---:|---:|---|
| `paper` | `#F7F7F5` | `#111110` | 全局背景 |
| `surface` | `#FFFFFF` | `#1B1B1A` | 卡片、弹层、输入容器 |
| `ink` | `#111111` | `#F2F2EF` | 标题、正文、主图标 |
| `inkFaint` | `#6B6B67` | `#A6A6A0` | 元信息、占位、辅助说明 |
| `accent` | `#111111` | `#F2F2EF` | 选中态、图形焦点、主强调 |
| `accentContent` | `#111111` | `#F2F2EF` | 强调文字、链接、动作前景 |
| `hairline` | `#D9D9D4` | `#393936` | 分隔线、细边框 |

Material `primary` 在浅色为 `#111111`（`onPrimary=#FFFFFF`），深色为 `#F2F2EF`（`onPrimary=#111110`）。error 红只服务破坏性操作与错误反馈；照片、成品图、透明素材和用户头像内容不做全局灰度化。

### 排版角色

- Display：系统 Noto Serif CJK，30sp/38sp，semibold；仅用于页面标题、角色名、单品/穿搭实体名。
- Title：Serif 20sp/28sp，medium；用于卡片标题和弹层标题。
- Section：Sans 15sp/22sp，semibold；用于小节标题和分组标题。
- Body：Sans 15sp/23sp；用于描述、评论、表单输入。
- Action：Sans 14sp/20sp，medium；用于按钮、导航和可操作行。
- Meta：Sans 11sp/16sp，medium；用于日期、数量、品类和状态；中文默认 `letterSpacing=0`。
- 短拉丁标签需要编辑风字距时局部使用约 `0.08em`；中文句子、域名、日期和 Prompt 保持零字距。标题和实体名最多两行；n/m、日期和统计数字使用稳定的等宽数字特性。

### 组件与反馈

- 主 CTA 浅色为黑底白字，深色为白底黑字；次 CTA 为透明底 + hairline 描边。
- 选中 Chip/Tab 使用黑白反差，不用品牌色铺底；导航文字仍保持中性色。
- 心愿品类占位、年度回顾分布条、数据包状态符号、复制纸屑和导出长图装饰统一为灰阶。
- 沿用现有 EditorialMotion、减弱动态和 Confirm 触感，不新增色彩闪烁或音效。
