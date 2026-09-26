# it-047 · 卡组自研内核（官方 API）+ W1 pager 调参 + 切换次因实测收敛

- **状态**：已实现，设备自测通过（真机 60fps 量化为遗留项，见验证记录）
- **来源**：Leo 2026-09-26 反馈「现在衣橱应用的卡片切换动画不行、交互体验也不丝滑。有没有开源的高口碑方案可以接入的？」→ 13-agent 调研工作流（现状体检 / 四路生态扫描 / 7 候选深挖 / 双视角对抗验证，全部数据 `gh api`/字节码实测）→ 路径 B 选定 → 本提案经 3 视角评审 + 对抗复核修订（1 blocker / 7 major / 10 minor 全部回改）
- **关联**：US-47；05-design-system「动效清单」#1/#2/#12 与「触感反馈」；DESIGN.md §3/§4；libs/carddeck specs（选型节重写 + ADR，推翻「不自研手势动画」旧决策）；eats SpinScreen 与 eats 常青 spec 连带

## 背景与动机

**结论先行：Compose 生态不存在「高口碑可直接接入」的卡组库，换库路线不成立；官方 API 自研在各维度胜出（对抗验证 refuted=true）。**

### 1. 生态调研（2026-09-26 全部实测）

| 候选 | ★ | 状态 | 判定 |
|---|---|---|---|
| 官方 HorizontalPager / AnchoredDraggable | androidx 6099 | 活跃 | ✅ 唯一高口碑选项 |
| LazyCardStack | 91 | 停更 26 个月、无 release | 程序化 API 贴合，但仅渲染 2 张卡、无手势回翻、无速度 fling |
| 现库 compose-swipeable-cards 1.1.4 | 104 | 停更 15.7 个月 | 兼容满分，缺陷是结构性的（见 2） |
| LazySwipeCards | 67 | 停更 19 个月 | 唯一全门槛达标，但 issue #4（verticalScroll 手势冲突）正压 W8 接入点；#8/#9 维护者有回复但问题仍 open、#10 无人回应 |
| compose-tinder-card | 188 | 已归档 | 手势/回弹/阈值全硬编码，痛点恰在不可调面 |
| twyper | 109 | 停更 4 年、无 LICENSE | 零动画注入面，迁移即手感倒退 |
| SwipingCards | 114 | 最活跃（2026-07） | minSdk 33 + 无程序化 API + 弹簧写死（0.7/80）比现状更差 |

- ≥500★ 的 Compose 卡组库**不存在**（高星全是 View 系老库）；2026 年两份独立外部调研（nag 2026-08、PolyTrainer 2026-07）落点均为「手写 100–150 行」。
- 该品类没有任何库有媒体/社区级口碑；现库 104★ 已是前列，其动画改进 PR #5 标题即「improve swipe-out animation」，挂 11.4 个月无人合。

### 2. 现库结构性缺陷（it-046 参数注入修不掉的部分）

1. **手势滑出疑似瞬间消失、无飞出动画**（源码推演 + 上游 PR #5 佐证）——it-046 注入的 `spring(0.9,500)` 只覆盖程序化路径（‹n/m›、drawRandom），手势路径未触及；接入前需真机确认此条。
2. **双动画体系**：飞出用可注入弹簧，堆叠晋升硬编码 `tween()` 300ms——同一张卡被两套节奏驱动。
3. **松手判定纯位置阈值**，无速度参与（甩不动/甩不掉只能调阈值）；rotation 走双重弹簧叠加（违 DESIGN.md「弹簧不叠加」）。
4. **eats 从未注入动画**，仍吃库默认 `spring(0.6,100)`（≈1s 落定、9% 过冲），问题比 wardrobe 更重。
5. 上游 15.7 个月零提交——继续用 = 暗中自养 fork，且受旧结构约束。

### 3. 「不丝滑」是三来源叠加（换库只能覆盖来源一）

- **来源一 W8 卡组**（wardrobe RecordsScreen + eats SpinScreen）：库结构缺陷，见 2。
- **来源二 W1/W7 pager 切换**：全仓实码为 **2 处 `HorizontalPager` 调用**（`SlotGrid.kt` L131 槽位换衣、`OutfitDetailScreen.kt` L139 W7 效果图轮播，snap/fling 全默认）+ **2 处 `animateScrollToPage` 未传 spec**（`OutfitScreen.kt` L247 老虎机、`SlotGrid.kt` L272 序号跳页），且无 `beyondViewportPageCount` 预取——与卡组库完全正交，换任何库都不解决。
- **来源三切换次因（待实测确认）**：`RecordsScreen.kt` 组合期写状态（声明 L70-71、写入 L167-168）+ L103/L195 组合期读取，**重组传播范围无实测证据**（评审实读：`currentIndex` 仅 L195 一处读取且位于深层 lambda，`isDrawing` 每轮仅首尾翻转 2 次，LazyVerticalGrid items 与卡组状态无读取关系）——先量化再定改法，避免改错位置；BodyCollage 单卡最多 ~7 路 Coil 并发解码（Coil 链本身干净，非主因）。

### 4. 自研底座可行性（对抗验证实测）

- `AnchoredDraggable` 在钉死的 foundation 1.8.3 中**零 experimental、@Stable、无需 OptIn**；`anchoredDraggableFlingBehavior(state, Density, positionalThreshold, snapAnimationSpec)` + `DecayAnimationSpec` + `animateTo/snapTo/getLastVelocity` 全公开——**速度驱动 fling 与单一 spec 源原生可用**，恰好消灭现库与全部候选库共有的缺陷类。（注意：部分 State 构造器与 `confirmValueChange` 在 1.8.3 已 Deprecated，只用非废弃重载。）
- `AnchoredDraggableNode extends DragGestureNode` 自带 touch-slop 门控（字节码实证），结构上避开 LazySwipeCards issue #4 类 verticalScroll 冲突（嵌套滚动仲裁仍需 spike 实测）。
- **爆炸半径小**：`CardDeck.kt` 现仅 143 行；controller 契约（`next/previous/restart/drawRandom/current/currentIndex/isDrawing/onSwipe`）保持不变 → wardrobe/eats 各 1 个调用点近零改动、构建脚本零改动。
- 隐藏红利：顺带收口类型泄漏——**3 处全限定引用**（`RecordsScreen.kt` L152/L158、`SpinScreen.kt` L327）+ `CardDeck.kt` 6 条 `com.spartapps` import + `carddeck build.gradle.kts` L31 `api(libs.swipeable.cards)` 全部随依赖删除收口为 carddeck 自有参数类。

## 用户故事

- **US-47a**：作为用户，卡组手势甩出跟手（1:1 拖拽、touch-slop 门控），过阈值/速度甩出**有连贯飞出动画**（不再瞬间消失），未过阈值回中干净无回摆，动画中途可被打断接管；随机抽取保持老虎机节奏；抽中落定有一次 Confirm 震。
- **US-47b**：作为用户，W1 槽位换衣、序号翻页与 W7 效果图轮播的吸附、程序化跳页有统一的弹簧节奏（EditorialMotion），老虎机落定轻弹有过渡，切换全程无闪跳。
- **US-47c**：作为用户（与开发者），全部切换动效走**单一弹簧 spec 源**（手势 settle = 程序化 animateTo），按 EditorialMotion 三件套映射；系统开启「减弱动态」时切换整体降级为即时落位（保留触感）；drawRandom 期间重组范围经实测收敛；双端真机 60fps 可量化达标。

## 前置（半天 spike，结论决定是否全面开工）

在真机上验证两问：① `RecordsScreen` L86 `.verticalScroll` 根 Column 内 AnchoredDraggable 嵌套滚动仲裁——**「通」= 竖滑不误触甩卡、横甩不带动外层滚动、松手卡位正确，三问全过**；② 真机复现现库「手势滑出瞬间消失」（R1），确认修复对象存在。任一不过 → 先出对策再开工，对策写回本文件。

## 验收标准

**W8 卡组自研内核（libs/carddeck）**

1. 手势甩出全路径有动画：拖拽 1:1 跟手；甩出判定含**速度分量**（AnchoredDraggable 官方基准 125dp/s 起调）；甩出飞行、未过阈值回中均为真实动画（可执行定义：**飞出/回中全程 ≥200ms 且录屏逐帧可见 ≥5 帧位移**）——**禁止任何 `setCurrentIndex` 式瞬移分支**（含末张回卷：甩出+揭示）。
2. **单一 spec 源**：手势 settle 与程序化 `animateTo` 走同一弹簧源（违反即验收不过）；弹簧按 EditorialMotion 三件套映射并记入 05（smooth=跟手/常规、pop=落定选中、bouncy=仅随机落定），旋转分量独立、不与位移弹簧叠加。
3. `drawRandom` 节奏基准不回退：步数 `4 + Random.nextInt(size)`、步距 420ms 起 ×1.18 封顶 560ms、收尾 560ms 等落定、`size==1` 不甩、每步真实甩出、**无半空摘除飞行卡**。（数字为代码实证值，与 05 #12、carddeck 00-overview 一致；it-046 验收原文的 260/×1.24/460/收尾480 为提案期旧值，本次一并回填订正 it-046。）
4. 契约不变：`circular` 永不枯竭（回卷用动态 anchors `updateAnchors`，不用已废弃的 `confirmValueChange` 否决）；`onSwipe` 甩出**起始**同步触发；`next/previous/restart/‹n/m›` 行为与现状一致。
5. 触感**二选定死**：抽中落定一次 `HapticFeedbackType.Confirm`（API <30 `LongPress` 兜底）；甩卡过阈值给一次 `GestureThresholdActivate`（不做须在 05 写明理由，二者必居其一）；滚动/导航不加震、无音效；05「触感反馈」小节登记卡组甩出触感，确认与既有接线不重复双震。
6. 类型与依赖收口：删除 `com.spartapps.swipeablecards` 全部痕迹——versions.toml L27/L66 两行、`carddeck build.gradle.kts` L31、`CardDeck.kt` 6 条 import、3 处全限定引用（改 carddeck 自研参数类，实现期定名）；**两个 `settings.gradle.kts`（wardrobe L24 / eats L24）与 `libs/carddeck/settings.gradle.kts` 移除 jitpack.io 仓库声明与注释**（全仓仅此库走 JitPack）；删后跑 `--offline assembleDebug` 确认无 JitPack 解析残留。
7. eats `SpinScreen` 同步迁移到新内核并**首次获得可注入动画**（现吃库默认 0.6/100）。
8. **可中断性（真机逐条录屏验收）**：①飞行/回中途中再次按下卡片，从当前帧位姿接管继续拖，不跳变不撕裂；②回中途中反向甩出按新手势速度+位移重新判定；③`drawRandom` 进行中卡面点击进 W7 与再次手势的行为**显式定义（忽略或中断，二选一写死）**并与实现一致；④settle 进行中调用程序化 `next/previous` 不产生双重动画。

**W1/W7 pager 调参（正交，无论 W8 结果如何都做）**

9. 2 处 `HorizontalPager`（`SlotGrid.kt` L131、`OutfitDetailScreen.kt` L139）显式注入 snap/fling spec、2 处 `animateScrollToPage`（`OutfitScreen.kt` L247、`SlotGrid.kt` L272）传 animationSpec——**全部收敛进 `Motion.kt`**（Motion.kt 为必改文件，非「如需」）；按 05 #1/#2：迷你格无缩放形变、老虎机槽间 100ms stagger + 落定轻弹 1→1.03→1；`beyondViewportPageCount=1` 防入屏白块（注意：1.7.0 起参数名已是 `beyondViewportPageCount`，官方文档 prose 残留旧名 `beyondBoundsPageCount` 勿抄）。末页回卷保持 it-046 既定「即时落位」不回退。

**次因收敛（先测后改）**

10. ①用 Layout Inspector recomposition counters 实测 `drawRandom` 各步重组范围，**口径**：RecordsScreen 外层 Column 与 LazyVerticalGrid 的 item scope 重组次数；**白名单**：仅 n/m 计数 Text 局部重组允许（且其读取下沉为局部状态）；以实测结果定义并达成目标，改法不得预设；②BodyCollage 并发解码给量化对比（工具与口径实现期写入验证记录：解码并发上限前后值 + 该段掉帧数前后对比）。

**动效合规（DESIGN.md 红线）**

11. **「移除动画/减弱动态」降级（blocker 回改）**：系统动画缩放关闭时，`drawRandom` 每步即时落位（保留每步与最终落定的 Confirm 震）、手势 settle/W1 pager 吸附瞬时到位、拖拽保持 1:1（拖拽是输入不是动画）；降级开关收口在 EditorialMotion/carddeck **单一入口**（如 `DeckStyle.reduceMotion`），不在调用点散写；05 动效清单补一行降级规则；验证 = 开发者选项开「移除动画」后录屏走查上述三条。
12. **不回退走查**：DeckStyle/新参数类双主题走查无新增纯黑/纯白元素（DESIGN 反例 5）；共享元素路径不被破坏（W1 槽位照片→W5、W8 卡面/网格→W7，05 #3）；W8 分页胶囊左右 48dp 热区（it-033）不动。

**工程与文档**

13. `assembleDebug` + `test` 绿；eats `compileDebugKotlin` 通过；**真机 60fps 量化达标——口径**：指定真机型号（记入验证记录）上「连续甩卡 10 次」与「drawRandom 10 轮」两段 gfxinfo，janky frames ≤5% 且无 >32ms 帧（模拟器 gfxinfo 失真不作依据，it-046 遗留一并收口）；**双端回归核对清单**（逐项勾选）：W8 翻卡/随机/筛选/末张回卷、‹n/m› 计数、共享元素、eats 抽卡+彩屑恰好单发+计数、W1 槽位换衣/序号翻页/W7 轮播。
14. 常青 spec 全量同步：wardrobe `01`（补 US-47）· `04`（L94 依赖行移除 swipeable-cards/JitPack 表述）· `05`（#12 改写为自研内核基准、#1/#2 补参数落点、「触感反馈」登记甩卡触感、补降级规则一行）· `06`（新 ADR：推翻 carddeck「不自研手势动画」，记录 AnchoredDraggable 依据）；**eats** `04`（L85「库自带飞出动画」改写、L101 JitPack 句删除）· `05`（动效清单 #1 视参数外显同步）· `06`（新 ADR supersede ADR-011「不自研手势动画」）；`libs/carddeck/specs/00-overview`（选型节重写 + **订正**「堆叠数无安全入口」误记——实为 State 构造首参与 remember 辅助函数签名不一致）；根 `README.md` L22「carddeck = 对 compose-swipeable-cards 的薄封装」改写；it-046 节奏数字回填订正；`CHANGELOG.md` 一行。

## 影响范围

`libs/carddeck/src/main/java/com/leo/libs/carddeck/CardDeck.kt`（重写，估 300–500 行）· `libs/carddeck/build.gradle.kts` · `libs/carddeck/settings.gradle.kts` · `libs/carddeck/specs/00-overview.md` ·
`wardrobe/app/src/main/java/com/leo/wardrobe/ui/records/RecordsScreen.kt` · `ui/components/SlotGrid.kt` · `ui/components/BodyCollage.kt`（若改码；仅测量则在验证记录注明） · `ui/outfit/OutfitScreen.kt` · `ui/records/OutfitDetailScreen.kt` · `ui/theme/Motion.kt`（必改） ·
`eats/app/src/main/java/com/leo/eats/ui/spin/SpinScreen.kt` · 根 `gradle/libs.versions.toml` · `wardrobe/settings.gradle.kts` · `eats/settings.gradle.kts` · 根 `README.md` ·
`wardrobe/specs/01-user-stories.md` · `specs/04-architecture.md` · `specs/05-design-system.md` · `specs/06-decisions.md` · `specs/iterations/it-046-*.md`（数字订正） · `eats/specs/04-architecture.md` · `eats/specs/05-design-system.md` · `eats/specs/06-decisions.md` · `CHANGELOG.md`

## 风险与对策

| 风险 | 对策 |
|---|---|
| verticalScroll 嵌套滚动仲裁未实测 | 前置 spike 半天先行，「通」三问判定；不过先出对策 |
| AnchoredDraggable 部分构造器/confirmValueChange 在 1.8.3 已 Deprecated | 只用非废弃重载 + `anchoredDraggableFlingBehavior`；circular 用动态 anchors |
| **eats 回归**：SpinScreen 首次获得可注入动画（手感变化）、彩屑单发（it-016 修过双发）、抽卡节奏两端共享 carddeck 逻辑 | 按验收 #13 回归清单逐项核对，含彩屑恰好一份、落定 Confirm 震一次 |
| **回滚路径**：依赖同迭代内删除，真机集成后才发现内核缺陷难回退 | 拆两次提交：先并入自研内核（保留旧依赖为回退点，或 feature 开关并行一版），双端真机验收通过后再删依赖；验证记录写明回退 commit |
| 手感主观、无上游可参考 | 双端走查 + 验收 #13 真机量化（janky ≤5%）收口 |
| 自研工作量乐观 | 认知 3–5 天（含双端回归 + 真机量化），非 1–2 天 |
| W8 手势瞬移（R1）未真机确认 | 前置 spike ②先复现确认，避免修错对象 |
| 弹簧多源复发 | 验收 #2「单一 spec 源」硬卡 + 评审 query 逐条过 DESIGN §6 |

## 验证记录

**2026-09-26 · 构建/单测/模拟器自测通过（emulator-5554；全部 uiautomator/截屏实证）**

**构建**：`wardrobe assembleDebug -PdemoDefault=true` + `test` 绿；`eats assembleDebug` + `test` 绿；
carddeck composite 编译绿；JitPack 仓库行删除后解析无残留。

**Spike 两问（前置，均已通过）**：
- ① 嵌套滚动仲裁：卡组面上竖滑 → 页面正常滚动、卡组不甩、松手卡位正确（恢复 verticalScroll 后复验仍过）。
- ② R1 确认：旧库源码实证「手势 onSwipe→moveNext 直接将旧顶卡摘出渲染集」（与上游 PR #5「improve swipe-out
  animation」互证）；新内核拖拽中途帧实拍：顶卡 1:1 跟手+旋转、下一张右侧揭示、堆叠可见——飞出动画真实存在。

**W8 卡组（wardrobe）**：前甩 1/5→2/5、回甩 2/5→1/5、慢拖过 100dp 松手（注入手势 v=0）→ 提交 ✓、
‹ › 精确 ±1（修复双提交后 1→2→1 ✓）、卡面点击进 W7 ✓、抽取 8 步全提交含回卷（4→0）落点 4/5 按钮复位 ✓。

**eats**：前甩 1/11→2/11 ✓、换一张 +1 ✓、抽取 13 步全提交含回卷（10→0）、**步距实测 483–607ms**
（基准 420–560 + 每步提交开销）、**飞出 300–360ms（首达截停，≈it-046 的 0.32s 基准）**、
落点 3/11、winner 块（候选文案）出现、换一张/再抽可再入 ✓。

**W1/W7**：槽位横滑（2/5→3/5）、序号翻页与末页回卷（5/5→1/5 即时落位）、老虎机动效（多槽位随机变化）、
OutfitDetail 成品图轮播参数注入 ✓；落定轻弹按 05 #2 落地（启动 1.5s 内不弹）。

**减弱动态（验收 #11）**：`animator_duration_scale=0` 下 wardrobe 抽取 1/5→5/5 即时落位完成、eats 同、
W1 槽滑正常、拖拽仍 1:1；每步 Confirm 震接线在 kernel（app 负责最终落定震）；scale 恢复 1 后复验正常。

**双主题**：浅色/深色走查，无新增纯黑/纯白元素（DESIGN 反例 5）✓。

**帧率（验收 #13 的量化部分）**：卡组连甩 gfxinfo janky 99.28%，但**纯竖滑对照组 100% janky**
（GPU 分位 4950ms 假值）→ 判定**模拟器基线失真、量化无效**，与 it-046 同结论；本机仅模拟器、无真机，
**真机 60fps 量化保留为遗留项**（行为实证 + 结构性修复收口）。

**测试中发现并修复 5 个结构性缺陷**（详见 /tmp/it047_facts.md 底稿，均已回归）：
① 手势落定观察者漏写（顶卡飞出卡死）；② 提交双跑竞态（`committedTarget` 幂等门）；
③ 官方 fling 在 v=0 时丢位置阈值（自研 `flingTarget`）；④ spring 过冲钳位致 state 漂移 1.33px（prev 取 state 实值+残差补足）；
⑤ 异步高优先级归位腰带取消后续 animateTo 使抽取静默死亡（改 tryCommit 内同步 snapTo）。

**2026-09-27 · 对抗评审（4 维度 + 逐条复核：1 blocker / 5 major / 18 minor）后修复与补测**

评审确认项全部修复并回归：
- **blocker：W1/W7 pager 减弱动态缺口**——foundation 1.8.3 字节码实证 `SnapFlingBehavior` 将 decay+snap 全段包进
  固定 scale=1 的 `withContext(DefaultScrollMotionDurationScale)`，系统「移除动画」对其无效。修复：
  `EditorialMotion.pagerFling(state, reduce)` 常态走官方 snap=smooth、降级返回自实现瞬时 TargetedFlingBehavior
  （就近整页直接落位）；开关收口 `EditorialMotion.reduceMotion()`（与 carddeck `rememberDeckReduceMotion`
  同读 Settings 事实源，分层双入口）。**补测：scale=0 横甩松手 0.15s 帧已完全落位 ✓。**
- **major：提交守卫竞态（本次评审-修复循环中发现并修掉）**——残留兜底守卫曾误杀第二次连按 › 的 animateTo
  （观察者 Rest 发射延迟到新飞行首帧、offset 仍≈0 时触发同优先级 snapTo 取消）。
  修复为三重条件（`isAnimationRunning && targetValue==Rest && offset≈0`）。**补测：连按 › 300ms 间隔 = 精确 +2 ✓、
  单击 ±1 ✓；抽取中连按›×2 = 忽略（落定后 3s 不变）✓。**
- **#10 收口**：n/m 计数下沉为独立 `DeckCounter` 组合（结构性隔离，白名单「读取下沉为局部状态」落地）；
  **Layout Inspector 计数器实测与 BodyCollage 并发量化 = 无头环境不可用，登记为遗留项**（同真机 60fps），
  US-47c 相应措辞已改为结构性收敛、删除「经实测」表述。
- **#8 可中断性行为走查**（录屏仍为真机遗留）：①飞行中按住接管→取消飞行、状态一致 ✓；
  ②回中途中反甩→状态一致 ✓；③settle 中连按›→opMutex 串行精确步进 ✓；④抽取中手势/卡面/‹› 均忽略 ✓。
  机制表述更正：中断由 `anchoredDrag` 的 MutatorMutex 同优先级取消实现（1.8.3 非废弃 API 无
  `startDragImmediately=isAnimationRunning` 参数，原表述有误）。
- **步距裁定（如实声明）**：调度值 420–560ms 达标；墙钟实测 483–607ms——步计时含 tryCommit/重组开销，
  修复「末步双尾延时」后收尾 = 自末步起算 560ms。
- **其余修复**：空牌堆 Rest 锚补齐（空表 closestAnchor 越界崩溃面）；flyForwardQuick 目标取闭包常量
  （防锚点重建改道）；阈值震排除程序化飞行（静置手指不误震）；reduceMotion 改 ContentObserver 实时感知
  **补测：现场 0→1 动画帧组见飞行 ✓、1→0 帧组全静息 ✓（均未重启应用）**；
  `EditorialMotion.runSettlePulse` 收口 tween(80)（Motion.kt 按 #9「必改」落地）；轻弹实例随 pagerState
  重建防冻结；RandomButton 双击/size≤1 误震防护；SDK 侧 next/previous 抽取中 no-op。
- **边界补测**：筛选夹紧（#休闲 3/3、#秋 2/2 clamp 路径、#春空态、恢复 ✓）；n=2 循环回看禁锚
  （回甩 1/2 不变 ✓）+ previous 即时回看（2/2 ✓）；恢复全部 → 2/5 ✓。

**已知差异/限制（含评审登记）**：真机 60fps 与 Layout Inspector 计数器、BodyCollage 并发量化、#8 逐条录屏
均为无头环境不可用之遗留；RTL 未处理（应用中文 LTR，旧库有 reverseX）；卡组/抽中态不 saveable（进程重建
复位，与旧库行为对等）；非循环分支未测（两调用点均 circular=true）；eats 彩屑单发为代码路径保证
（drawRandom 幂等门 + null 判定，`confetti++` 每次抽取恰一次）未逐次录屏；共享元素 #3 为存量缺口
（仅目标侧 sharedPhoto 接线、无源侧配对，本迭代入口走查通过但转场为 no-op——非本迭代引入，05 #3 措辞待后续对齐）。
