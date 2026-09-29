# it-071 全应用滚动性能优化

- 状态：**已实施**（2026-09-30，P0–P3 全做，用户拍板「P0–P3 全做」）
- 提出日期：2026-09-29
- 涉及应用：wardrobe（P3 含构建链路与依赖变更，ADR-028）

## 背景与动机

用户（2026-09-29）装了 demo debug APK（v0.5.0.110）后实报：**列表页（W3 衣橱网格）滚动有性能问题**，要求关注整个应用的性能问题并优化。

**诊断证据（2026-09-29，emulator-5558，数值只作前后对照的相对指标）：**

1. **基线复测**：进 W3 等入场动画结束后 gfxinfo 复位，6 次交替快速 fling（300ms 上下对扫）→ **93 帧、92 janky（98.9%）**，p50=69ms / p90=121ms / p95=150ms，Slow UI thread=68、Missed Vsync=48。**空闲对照：静止 3s 渲染 0 帧**——无后台刷帧，卡顿纯滚动驱动。
2. **framestats 相位分解（59 帧）**：Compose 组合+布局+录制中位 **≈3.8ms/帧（本身不超标）**；大头在 RenderThread 等待（中位 27ms）与 GPU 执行（中位 19ms）——模拟器软件渲染放大 GPU 项，**真机需复测**；intended→input 中位 24.9ms 说明帧在排队（前帧拖累）。诊断计数器 `Slow UI thread=68` 与最差帧中 input 段 40–106ms 说明 UI 线程也在被组合期工作间歇拖慢。
3. **代码诊断（W3 直读 + 全应用横扫，代理审计已回）**，按置信度：

**W3 列表页直接病因**

- **A. `imageFileOf` 组合期 `File.exists()`**（`AppViewModel.kt:53`）：每个 ItemCard 重组/首次组合都在主线程 stat 磁盘；滚动中每张新进视口的卡都来一次。
- **B. `TrimAlphaTransformation` 全像素扫描**（`TrimAlpha.kt`）：每次冷解码分配 `IntArray(w×h)` + 行/列两趟 O(w×h) 扫描——**图片完全不透明时也全额执行**（快速短路缺失）；变换的存在还迫使 Coil 走软件解码路径。
- **C. W3 网格组合偏重**：`sorted` 排序在 content lambda 内每次重组重跑（`WardrobeScreen.kt:335`）；每卡叠加 sharedPhoto、pressScale、combinedClickable、⋮ 菜单、无 `contentType`。
- **D. 入场动画与首滚叠加**：`StaggeredEntrance` 前 900ms 内 index×24ms 错峰动画与用户第一波滚动重叠；记录页因 E 全量组合而**全卡同时入场**。

**全应用结构问题（审计高置信）**

- **E. RecordsScreen 非懒网格**（`RecordsScreen.kt:86-89, 249-257`）：`LazyVerticalGrid(userScrollEnabled=false, height=(n/2*300).dp)` 嵌在 `Column.verticalScroll` 里——**视口=全数据集，懒加载失效**，全部穿搭卡（PhotoCard 或至多 6 图的 BodyCollage）一次性组合常驻，页面滚动在拖一棵已全量组合的树。
- **F. 组合期动画/滚动读**：`CountUp.kt:29-34` 每帧重组文本（W9 三格 + W11 每行用量，进页即触发）；`ScrollFade.kt:58-67` fade 判定读在组合期且 Brush 每帧分配（横滑筛选行——W3/W8/W10/Tags）；`SlotGrid.kt:230,308` `currentPage` 读进每个已组合页（翻页中途全页重组 + sharedPhoto key 翻转）。
- **G. OutfitScreen 屏级派生**（`:182-203`）：分类/单品映射/两次全量穿搭扫描未 remember，且每次槽位 settle 都走 DataStore 回流重组整屏（`:163-165`）。
- **H. 列表行 elevation 阴影**（Wishlist `:419,550`、Records `:194,333`）：`shadowElevation=2.dp` 每行每帧图层/阴影开销——05 设计系统本就「阴影极轻或无，层次靠留白与 hairline」，删除有据。
- **I. 组合期 I/O/重活**：`ItemDetailScreen.kt:112` `looksCutoutPhoto` 主线程采样解码 + 像素数组（进详情页时）；`RecordsScreen.kt:185` 组合期写 `mutableState`；`BodyCollage.kt:204` ImageRequest 每次组合新建（身份变化可重启加载）；`ChatListScreen` SimpleDateFormat 每行新建。
- **J. 构建路径缺位**（`app/build.gradle.kts:50-54`）：release `isMinifyEnabled=false`、无 baseline profile、无 signingConfig——**当前唯一可分发路径就是 debug APK**，用户实报的真机数据里含 debug 构建税。

## 涉及的用户故事

- **NFR-01**（冷启动 < 2s；**列表滑动 60fps**）——本迭代的验收锚。
- **US-54**（黑白灰视觉一致性）、**US-55**（可读且有层级的中文排版）——视觉不回归锚。
- 不新增/不修改用户故事；无界面与交互语义变更（H 的阴影删除属设计系统既有口径的对齐，见 05 修订）。

## 提案内容

### P0 · W3 列表页直接修复（用户实报）

1. **去掉组合期 `File.exists()`**：`imageFileOf` 改纯路径传递（`FileMediaStore.file` 本就是纯构造）；`PhotoCard` 的 ImageRequest 给 `.error(同 placeholder)`，缺失文件与加载失败一样落灰底占位——null 语义不变、stat 归零。
2. **TrimAlpha 快速短路**：先采样 4 角 + 4 边中点（如各 8×8 块），全部不透明 → 视为非透明素材直接返回原图（真照片/不透明 WebP 零扫描零大分配）；仅角落见 alpha 才走既有全扫描。不透明素材的裁剪语义不变（本来就是原样返回）。
3. **W3 组合瘦身**：`sorted` 提入 `remember(filtered)`；网格 item 补 `contentType`；排序移出 content lambda。

### P1 · 结构修复（全应用，审计高置信）

4. **Records 网格真懒化**：网格自身成为页面滚动容器（deck/筛选行做 header 项，或改单容器 LazyGrid 布局），删除「固定高度 + userScrollEnabled=false + 外层 verticalScroll」的反模式；卡数增长不再线性增加组合量。
5. **CountUp 收窄重组域**：数字文本独立成只读 `anim.value` 的最小 composable + `remember` 格式化 lambda；count-up 期间不再拉爆整行。
6. **FadingScrollRow 判定入 draw + Brush 缓存**：`fadeActive` 移进 draw lambda（`fadingBottomEdge` 既有先例），渐变 Brush remember 复用；横滑只触发重绘不触发组合帧。
7. **SlotGrid currentPage 隔离**：页码/名称条读取隔离进独立小 composable（或 draw 期读），翻页中途不再全页重组、sharedPhoto key 不翻转。
8. **OutfitScreen 屏级派生 remember 化**：`remember(slotSel, data)` / `derivedStateOf` 包裹分类映射与两次穿搭扫描，settle 回流不再整屏重组。
9. **列表行去 elevation**（Wishlist/Records）：删 `shadowElevation=2.dp`，层次回到 05 既有的 hairline/留白语言。

### P2 · 组合期 I/O / 次要清理（defensive）

10. `looksCutoutPhoto` 移出主线程（进页后 `LaunchedEffect` + IO，结果回填 state）。
11. `RecordsScreen.kt:185` `deck = controller` 组合期写改 `SideEffect`；`BodyCollage` ImageRequest remember；`ChatListScreen` 日期格式 remember。
12. 其余审计小项（Wishlist 未 remember 派生、BubbleIn 重放、infinite transition 门控）按「改动小、无视觉风险」顺手收，改动大的留档不做。

### P3 · 构建路径（需单独拍板，含 ADR）

13. release 打开 minify + 资源收缩、补 signingConfig；接 **baseline profile**（`androidx.baselineprofile` 插件 + `profileinstaller`）——依赖变更，**须记 ADR**；分发改 release APK（重出二维码）。
14. 不做：Coil 全局 ImageLoader 定制（默认配置够用，先看 P0/P1 效果）；不改 220ms crossfade（it-058 视觉规格）；不动 demo 素材尺寸。

## 验收标准

1. **同一 fling 协议前后对照**（emulator 相对指标）：janky% 与 p90 显著下降（目标：janky 98.9% → ≤60%，p90 下降 ≥40%；模拟器 GPU 项失真，不设绝对 60fps 硬指标）。
2. **真机复验（最终判据）**：重打 APK 装机，Leo 实滑 W3/W9/记录页确认卡顿消失或明显缓解。
3. 视觉走查无回归：W3/W8/W9/W10/W11 截图对照 US-54/55；删除行阴影后列表观感仍符合 05「留白+hairline」口径。
4. TrimAlpha：透明素材（demo 透明单品）裁剪呈现与 it-061 一致；不透明照片零变化。
5. wardrobe 91 单测全绿；Records 改动后卡数 >30 的列表滚动行为正常（进出滚动、点击、入场）。
6. （若 P3 获批）release APK 可安装、baseline profile 生效有构建证据；ADR 入 06-decisions。

## 影响范围

- P0：`AppViewModel.kt`（imageFileOf）、`ui/components/PhotoCard.kt`（error 占位）、`ui/components/TrimAlpha.kt`、`ui/wardrobe/WardrobeScreen.kt`
- P1：`ui/records/RecordsScreen.kt`、`ui/components/CountUp.kt`、`ui/components/ScrollFade.kt`、`ui/components/SlotGrid.kt`、`ui/outfit/OutfitScreen.kt`、`ui/wishlist/WishlistScreen.kt`
- P2：`ui/detail/ItemDetailScreen.kt`、`ui/components/BodyCollage.kt`、`ui/chat/ChatListScreen.kt`（若含）
- P3（若获批）：`app/build.gradle.kts`、`gradle/libs.versions.toml`、新 baselineprofile 模块、`specs/06-decisions.md` ADR
- specs 常青同步：`05-design-system.md`（行阴影删除注记 + 若动 ScrollFade 组件清单口径）、`04-architecture.md`（仅 P3 构建链路若成形）；01/02 不动（无用户故事/线框变更）
- CHANGELOG、本文件验证记录

## 分级建议

**本迭代默认做 P0+P1+P2**（纯代码修复，无依赖变更、无 ADR）；**P3 单独确认**——它改构建链路且引入新依赖，做的话一并入本迭代并补 ADR，不做则先用代码修复重打 debug APK 复验。

> 用户拍板（2026-09-30 AskUserQuestion）：**P0–P3 全做**。

## 实施与偏差

**已实施**：P0 全部（imageFileOf 纯路径化 + PhotoCard error 占位、TrimAlpha 不透明快速短路、W3 sorted hoist + contentType）；P1 除第 7 项外全部（Records 单容器 LazyGrid 真懒化 + SideEffect deck 赋值、CountUp 叶子化、ScrollFade 判定入 draw + Brush remember、OutfitScreen 屏级派生 remember 化、Wishlist/心愿穿搭行删 `shadowElevation=2.dp`）；P2 主体（BodyCollage byCat/ImageRequest remember、ChatList SimpleDateFormat remember、Records header span 项）；P3 全部（R8 minify+shrink、debug-keystore 签名、`profileable`、profileinstaller、`:baselineprofile` 模块与生成、ADR-028）。

**偏差与留档（有意不做）**：

- **SlotGrid `currentPage` 隔离（P1-7）未做**——复核后读点已收窄在页码条/名称条自身，翻页全页重组风险低于初判，收益/改动比不划算；留待真机数据支持再动。
- **`looksCutoutPhoto` 保持同步（P2-10 未异步化）**——≤128px 采样解码仅进详情页一次（约 2–5ms），不在滚动路径；改异步会让「去背景/重新抠图」按钮条先闪错态再回正，得不偿失。
- **Records header 入网格后首行下移 ~6dp**——header span 项与原 Column 间距的累计差，视觉走查接受（仍在 05 留白口径内），记录在案。
- **Records 胶囊/卡组 deck 阴影保留**——审计初稿把它们记为「列表行阴影」有误：胶囊与 deck 是单例焦点元素（05 明确保留），只删了 Wishlist/心愿穿搭行的行级 2dp。
- **BubbleIn/TypingDots 重放、EmptyState/casino infinite transition 门控、AppContainer 主线程解包**——改动大或有视觉风险，留档不做（P2 第 12 条口径）。

## 验证记录（2026-09-30，emulator-5558）

**测量协议（前后完全一致）**：force-stop/冷启 → 点衣橱 tab (678,2274) → 等 2.5s 入场 → `dumpsys gfxinfo reset` → 3 组交替 fling（`540 1800→540 400 300ms` / `540 500→540 1900 300ms`）→ 取 gfxinfo 汇总；每次导航后重 dump uiautomator 核对页面。模拟器软件渲染，数值只作**同协议相对对照**。

**gfxinfo 汇总**：

| 运行 | 帧数 | janky% | p50 | p90 | p95 | MissedVsync | Slow UI thread |
|---|---|---|---|---|---|---|---|
| 基线（it-070 代码，debug） | 93 | 98.92 | 69 | 121 | 150 | 48 | 68 |
| 改后 debug #1 | 71 | 95.77 | 77 | 121 | 129 | 36 | 59 |
| 改后 debug #2 | 64 | 98.44 | 73 | 105 | 109 | 33 | 51 |
| 改后 release+AOT #1 | 68 | 97.06 | 48 | 89 | 93 | 30 | 23 |
| 改后 release+AOT #2 | 77 | 94.81 | 65 | 113 | 125 | 43 | 35 |

**framestats 相位中位（ms/帧）**：

| 运行 | UI（input→sync） | compose/record | RenderThread（issue→completed） | 总（vsync→completed） |
|---|---|---|---|---|
| 基线 debug | 8.19 | 3.81 | 48.35 | 91.1 |
| 改后 debug | 7.92 | 3.80 | 42.55 | 83.9 |
| 改后 release+AOT | **0.76** | **0.46** | 34.42 | 73.8 |

**读数（诚实结论）**：

- **CPU/线程侧全面变好**：Slow UI thread 中位 68→29（release+AOT，−57%）、p95 150→93~125、UI 线程相位 8.2→0.8ms（R8+AOT+基线 profile 点亮后）、compose/record 3.8→0.5ms。
- **janky% 仍在 ~95%**：RenderThread 单相 34–48ms（软件渲染 GPU 项）主导总时长——**模拟器已顶到自身上限**，验收标准 1 的「≤60% / p90 −40%」在模拟器上达不到，这不是代码侧剩余问题。
- **基线 profile 生效步骤**：安装 release 后必须 `adb shell cmd package bg-dexopt-job com.leo.wardrobe` 并确认 `dumpsys package dexopt` 为 `speed-profile/bg-dexopt`；未做 AOT 时首跑反而更差（p90=150）。
- **验收判定**：标准 3（视觉）/ 4（TrimAlpha，透明单品抠图正常渲染）/ 5（91 单测绿 + 记录页滚动走查）/ 6（release 可装、profile 25,098 条、ADR-028）**已达成**；标准 1 在模拟器上**以相位/SlowUI 证据部分达成，janky% 未达标**；标准 2 **真机复验为最终判据，待 Leo**。

**Release R8 冒烟（全绿，无 FATAL/NoClassDefFound/NoSuchMethod）**：搭配页（序列化槽位）→ 衣橱网格 → 单品详情（抠图条状态正确）→ 心愿（分组行无行阴影、种草 FAB）→ 设置（厂商/模型枚举下拉、外观三选）→ 记录页（header 入网格、deck 1/8、随机一套）→ 穿搭详情（成品图/单品布局分段、这套包含）→ 顾问空态 → 衣橱回顾（CountUp 36/8/0 渲染）。logcat 扫描仅系统侧噪音。

**视觉走查截图**（/tmp/it071_shot_*.png，US-54/55 锚）：W3 网格、W9 心愿行、记录页 header 间距、回顾页计数、主题三选均无回归。
