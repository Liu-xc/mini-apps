# 05 · 设计系统（时装编辑风 × 高表现力动效）

## 视觉基调

时装杂志内页的气质：淡绿纸感底色、墨绿衬线大标题、大图为主角、克制的排版层级、唯一强调色。**照片永远比装饰重要。**（R6：应用户反馈由砖红换清新绿系）

> 素材来源（2026-09-20 起）：应用图标与 8 品类 3D 图标（衣架/T恤/外套/下装/连衣裙/鞋/包/帽子/配饰）取自 [thiings.co](https://www.thiings.co) 免费素材（个人非商业用途，署名见 README）。

## 色彩 Token（浅色 / 深色）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `paper` 背景 | `#F5F9F3` 淡绿白 | `#10150F` 墨绿纸 | 全局背景 |
| `surface` 卡片 | `#FFFFFF` | `#1B231B` | 卡片/弹层 |
| `ink` 主文字 | `#1D2620` | `#EAF2E8` | 标题/正文 |
| `inkFaint` 次级 | `#808D82` | `#94A294` | 序号/说明/占位 |
| `accent` 强调 | `#429E68` 青杉绿 | `#74C790` | 选中态/主按钮/FAB |
| `hairline` 分隔 | `#E3EBE0` | `#273127` | 细分隔线 |

标签 chip：`paper` 底 + `ink` 字（浅）；深色反之。破坏性操作用系统 error 色。

## 字体与排版

| 层级 | 字体 | 用法 |
|---|---|---|
| Display | **FontFamily.Serif**（系统 Noto Serif CJK）· 28-34sp · semibold | 角色名、页面大标题、单品名 |
| Title | Serif 20sp | 卡片标题 |
| Body | 系统 Sans 15sp | 描述、评论 |
| Label | Sans 11sp · letterSpacing 0.2em · 大写拉丁 | 「WHITE · 04」式小标签、品类名 |

> 衬线用系统字体而非打包 Noto Serif SC：安卓中文机普遍内置 Noto Serif CJK，APK 减重 ~20MB；若真机缺衬线再考虑打包（见 ADR-005）。

## 形状与间距

- 卡片圆角 20dp；弹层顶角 28dp；chip 圆角 8dp（胶囊感弱化，更"印刷"）
- 触控目标 ≥44dp（it-028）：评论删除钮 44dp；媒体卡悬浮角标（卡片「···」菜单）命中区 36dp（DESIGN.md §2.5 例外档），视觉尺寸不变
- 屏幕边距 20dp；槽位之间 hairline 分隔；照片卡宽高比 4:5（衣物的自然比例）
- 阴影极轻或无，层次靠留白与 hairline

## 动效清单（统一弹簧基调：EditorialMotion）

> material3 1.4.0 稳定版未公开 Expressive motionScheme（ADR-004），全局动效由 `EditorialMotion` 统一弹簧参数承担：`smooth`（高阻尼丝滑）/`pop`（轻过冲）/`bouncy`（弹跳）。

| # | 动效 | 实现要点 | 触发处 |
|---|---|---|---|
| 1 | 槽位轮播 | ~~HorizontalPager + graphicsLayer 缩放形变~~ → it-003 起为 3×3 迷你格内 HorizontalPager（吸附换衣保留；迷你尺寸下取消缩放/透明形变） | W1 |
| 2 | 🎲 老虎机 | 逐槽位 `animateScrollToPage` 随机目标，槽间 100ms stagger，落定 spring 轻弹（scale 1→1.03→1） | W1 |
| 3 | 共享元素 | `SharedTransitionLayout` + `Modifier.sharedElement`：卡片照片→W5 大图、穿搭格→W7 成品图，无缝放大 | W1→W5、W8→W7 |
| 4 | 复制成功 | 按钮内容 AnimatedContent morph 成 ✓，同时 Canvas 自绘彩屑粒子（15-20 粒，砖红/墨黑/米白三色，重力下落 600ms） | W6 |
| 5 | 滑动删除 | Material3 `SwipeToDismissBox`，背景显现删除图标，删除后 `animateItem` 淡出回落 | W3 |
| 6 | 切角色 | 数据区 `Crossfade`，角色名 `slideInVertically`+fade | W2 确认后 |
| 7 | 列表入场（it-028 落地） | `StaggeredEntrance`：24ms 错峰 fade+上移 28f，`rememberSaveable` 标志**仅首进播放**（DESIGN.md §3 预算）；删除/重排走 `animateItem()` | W3/W8 |
| 8 | 空状态 | Lottie 动画（衣架/晾衣绳插画，取自 LottieFiles 免费资源，json 放 res/raw） | W1/W3/W8 空态 |
| 9 | 收藏 ☆→★ | scale 心跳 1→1.2→1 + accent 着色 | W6/W7 |
| 10 | 底部弹层 | ModalBottomSheet（M3 弹簧），导出面板内容 staggered 淡入 | W2/W6 |
| 11 | 统计数字 count-up（it-027） | `CountUpText`：Animatable 首进 0→N 起数、档位切换旧值过渡，`EditorialMotion.smooth()`，三格 60ms 错峰 | W9 三大数字 |

## 触感反馈（it-027 · DESIGN.md §4 基线）

`ui/components/Haptics.kt`：`confirm()`（API 30+ CONFIRM，低版本回退 LONG_PRESS）/ `error()`（REJECT / VIRTUAL_KEY）/ `tick()`（CLOCK_TICK），无声音。接线：复制长图 ✓、存相册 ✓、☆保存这套、🌟存为心愿、穿搭打卡（含再记一次）、去背景成功、角色编辑/新建保存、心愿「收进想买/保存」、心愿购入转正、心愿穿搭升级 = **confirm**（it-027 + it-028）；评论发送、撤销今日打卡、还原原图 = **tick**；未就绪点保存的 toast = **error**。滚动/导航/输入不加触感。

## 参考实现（写代码时对照）

- 官方 Compose Animation 文档与 cheat sheet：developer.android.com/develop/ui/compose/animation
- 轮播卡片动效系列：sinasamaki.com/creating-pager-animations-in-jetpack-compose
- 图片轮播 graphicsLayer+lerp：ProAndroidDev《Swipeable Image Carousel with Smooth Animations》(2025-03)
- 真机动画调优示例集：github.com/skydoves/compose-animations
- Material 3 Expressive / motion：m3.material.io/develop/design-system/material-3-expressive

## 组件清单（ui/components/）

`PhotoCard`（4:5 照片卡，支撑轮播形变）、`SlotPager`（品类槽位）、`TagRow`/`TagChipInput`（标签展示与录入）、`CommentTimeline`（评论时间线+输入）、`EmptyState`（Lottie+文案+行动按钮）、`EditorialHeader`（角色名+衬线排版）、`FilterChipsRow`（标签筛选条）。

## 照片容器「衬纸」（it-011 C5）

| Token | 值 | 用途 |
|---|---|---|
| `photoMat` 浅色 | `surfaceVariant @ 55%` | 单品照片统一浅底圆角容器（W1 格位/W3 网格/W5 大图/W7 单品行），ContentScale.Fit 完整呈现轮廓 |
| `photoMat` 深色 | 同上（暗色 surfaceVariant） | 深色模式同构 |

成品图（用户导入/生图产出）保持全幅 Crop 不套衬纸；后续接抠图能力时把衬底换透明即可。

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
