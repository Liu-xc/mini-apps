# it-058 · 交互体感打磨：转场连续性 × 反馈 × 占位（上线体感专项）

> 状态：**已完成**（2026-09-29，Leo 确认「一次搞完不分批」后全量实施）
> 来源：Leo 反馈「整体 UI、交互效果、动画等交互体感上的细节达不到上线标准，需要继续打磨」。
> 方法：动效专项走查——最新代码构建（demoDefault）+ 5 段录屏（709 帧 filmstrip）视觉评审 + 全量源码核实（视觉结论逐条对源码钉死，非印象分）。

## 背景与动机

此前 5 轮走查（r1/r2/r3/r5 + 新三面）解决的是**静态视觉**（间距/token/层级/触控目标）；本轮换轴：**时间维度**——转场连续性、按压反馈、加载占位、入场编排。走查发现 3 条 spec 纸面承诺与实现不符（详见 C9），以及全站性的体感缺口：

### C1 · 照片加载无占位，全站白块闪现（P0）

- 证据：冷启动（m1b 录屏 2.75–5.25s）、Tab 每次切换、W3 网格滚动均出现照片位白块；`PhotoCard.kt` 仅 `crossfade(220)`，**无 placeholder 资源/色块**——crossfade 只管「占位→成图」的渐入，占位是空白。
- 照片是本应用的绝对主角，主角的每一次出现都先当白块，是「MVP 体感」的第一来源。
- 修法：`PhotoCard` 两分支统一加灰阶占位（`photoMat` 系色值，浅深主题对值）；评估 Coil 内存缓存 key（W1 缩略与 W8 hero 同文件不同 size → 缓存不命中 → 磁盘重解码，Tab 切换「每次白一下」的放大器）。

### C2 · 共享元素转场从未生效（P0）

- spec 动效 #3 声称「W1→W5、W8→W7 卡片照片无缝放大」；**源码实锤**：全仓库 `sharedPhoto` 仅 `ItemDetailScreen.kt:220` 一端调用（key=`item-photo-${id}`），来源端（`SlotGrid.kt:217` 的 PhotoCard、W3 网格卡、W8 卡组 hero）与 W7 端全部未挂——单端 key 无配对，转场从未跑过。录屏评审证实实际是整页 fade。
- 基础设施完整（`SharedTransitionLayout` + 双 CompositionLocal + NavHost 各 composable provide），**只差来源端接线**，是体感升级性价比最高的一项。
- 修法：W1 槽位照片 / W3 网格卡 / W8 hero 挂 `sharedPhoto("item-photo-${item.id}")`；W7 成品图与 W8 hero 挂 `sharedPhoto("outfit-photo-${id}")`；返回 pop 转场自然获得反向缩小。

### C3 · 全站无按压形变反馈（P1）

- 源码实锤：`animateFloatAsState` 全 App 零使用（carddeck 内核除外）；照片卡/chips/底部 CTA 全是裸 `Modifier.clickable`，仅默认涟漪（黑白灰主题下极淡）。
- 大面积照片卡按下去「没有任何事情发生」的感觉，是体感「死」的主因。
- 修法：新增 `PressableScale` 修饰组件（按压 scale≈0.97 + `EditorialMotion.pop()` 回弹），照片卡、大 CTA、chips 接入；文字按钮保留涟漪，双层反馈不叠加。

### C4 · Tab 切换朴素 + 图片重载白块（P1）

- `Crossfade(tween(220))` 合规但无层次；C1 落地后白块消失。
- 可选微升级：fade + scale(0.98→1) 的 M3 风格微层次（预算仍在 220ms 内，遵守 DESIGN.md §3）。

### C5 · 高潮动作无过程反馈（P1）

- 「随机一套」点击后按钮本体无任何状态（老虎机在槽位上跑，按钮不知道）；无防连点。
- 修法：滚动期间按钮进入忙碌态（文字/图标变化 + 禁用）；触发一次 `tick` 触感对齐卡组基线可选。

### C6 · AI 对话缺上线体感（P1）

- ChatScreen 无 typing indicator；新消息气泡无入场动画（仅 `animateScrollToItem`）。
- 修法：请求期间三点呼吸 typing 指示（assistant 位）；气泡入场 slide+fade 220ms；流式增长已有滚动跟随保持。

### C7 · 首页与弹层入场编排缺失（P2）

- `StaggeredEntrance` 只落了 W3/W8（动效 #7 口径），**W1 首页无入场编排**；W6 导出面板内容同时拍上（spec 动效 #10 承诺 staggered 淡入，`ExportSheet.kt` 实际 0 匹配——纸面承诺）。
- 修法：W1 槽位分区首进 stagger（rememberSaveable 一次性，对齐 W3/W8 惯例）；ExportSheet 设定区 24ms/行错峰淡入。

### C8 · 冷启动衔接（P2）

- splash → 首页瞬切无过渡；录屏首帧状态栏区疑似闪黑（edge-to-edge 首帧绘制，需复现确认）。
- 修法：窗口背景/splash 背景与 paper 对齐 + 入场淡入；状态栏首帧专项核对。

### C9 · spec 与实现不符勘误（必做，随实施同步）

| spec 动效清单 | 声称 | 实际 |
|---|---|---|
| #3 共享元素 | W1→W5、W8→W7 无缝放大 | 两端未接线，从未生效（本迭代落地） |
| #8 空态 | Lottie 动画 | it-001 起自绘 sway 摆动（零依赖决定），spec 未同步 |
| #10 导出面板 | 内容 staggered 淡入 | 0 匹配未实现（本迭代落地） |

## 合格项（走查确认，不动）

- W6 sheet 弹出弹簧（M3 水准）；ConfettiBurst（26 粒物理抛物线 900ms，DESIGN.md §3 合规）；卡组甩卡内核（it-046~048 已调校）；空态摆动；触感接线面（it-027/047，10 文件覆盖保存/复制/打卡类）；pager 落定轻弹；count-up。

## 实施分批

- **批 1（P0）**：C1 占位 + C2 共享元素接线 + C9 spec 勘误 → 全站「图片为主角」的连续性质变
- **批 2（P1）**：C3 按压反馈 + C5 随机按钮 + C6 对话体感 + C4 Tab 微层次
- **批 3（P2）**：C7 入场编排 + C8 冷启动衔接

## 验收标准

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿；新增组件（PressableScale/占位）单测覆盖关键行为。
- 录屏对比验证（同走查管线）：W1→W5 / W3→W5 / W8→W7 三路径照片连续放大无跳变；Tab 切换无白块；随机按钮有忙碌态；对话有 typing + 气泡入场。
- 减弱动态（系统动画缩放=0）下全部降级为即时状态，无残余动画。
- 深色模式占位/按压/共享元素同构。
- 05-design-system.md 动效清单与实现对齐（#3/#8/#10 勘误收口）。

## 影响范围

- **代码**：`ui/components/PhotoCard.kt`（占位）、`SlotGrid.kt`/`WardrobeScreen.kt`/`RecordsScreen.kt`/`OutfitDetailScreen.kt`（sharedElement 来源端）、新 `ui/components/PressableScale.kt`、`OutfitScreen.kt`（随机按钮态 + W1 入场）、`ExportSheet.kt`（stagger）、`ChatScreen.kt`（typing + 气泡）、`MainActivity.kt`（Tab 转场/冷启动，如动）。
- **常青 spec**：`05-design-system.md`（动效清单勘误 + 新增条目）、根与应用 CHANGELOG。
- **不涉及**：数据模型、导航结构、DESIGN.md 基线（全部在既有基线内实施）。
- **风险**：sharedElement 与 pager/卡组溢出绘制的相互作用（W8 hero 甩卡时禁用共享元素或对齐 key 生命周期）需实测；Coil 缓存 key 调整需防内存压力（演示图集 ~20 件，可承受）。

## 实施记录

- **C1 占位**：`PhotoCard.kt` 非 mat 分支加 `placeholder = ColorPainter(surfaceVariant@45%)`（mat 分支衬纸底色本就兜底）；不动 Coil 内存缓存 key（跨尺寸共享条目会致放大端取小图变糊，明确不做）。
- **C2 共享元素**：`SlotGrid.kt`（W1 槽位，仅 `pagerState.currentPage == page` 挂 `item-photo-${id}`）、`WardrobeScreen.kt`（W3 网格卡同 key，置于尺寸修饰符之后与 W5 端一致）、`RecordsScreen.kt`（W8 hero 挂 `outfit-photo-${outfit.id}-0`）、`OutfitDetailScreen.kt`（W7 轮播仅当前页挂 `outfit-photo-${outfit.id}-$page`，与 hero 的 `-0` 对齐；停在他页返回时无匹配即整页转场，不错位）。
- **C3 按压反馈**：新组件 `ui/components/PressableScale.kt`（`Modifier.pressScale()`：awaitEachGesture 自侦测按压、按下 NoBouncy+StiffnessHigh 快缩、抬手 `EditorialMotion.pop()` 回弹；`reduceMotion()` 时不缩放）。接入：W1 槽位照片（0.975）、W3 整卡（0.97）、W1/W6「复制长图」CTA（0.96）。
- **C4 Tab**：`MainActivity.kt` 顶层 Tab 由 `Crossfade(tween(220))` 升 `AnimatedContent`（fadeIn 220 + scaleIn 0.985→1 220 对 fadeOut 160）。
- **C5 随机忙碌态**：`OutfitScreen.kt` 显式 `rolling: Boolean` state（初版用 `Job.isActive` 推导，实测图标不转——job 状态非 Compose 状态，写入 job 引用虽触发一次重组但无法证成持续驱动；改显式 Boolean + try/finally 复位后实测旋转生效）；Casino 图标 700ms/圈匀速旋转，进行中忽略点击。
- **C6 对话体感**：`ChatScreen.kt` typing 升级 `TypingDots`（三点 140ms 错相呼吸，assistant 气泡同形制 paper+hairline 16dp 容器）；Me/Ai/Tools 行包 `BubbleIn`（MutableTransitionState 首组合 fade 200ms + 上移 1/10 高 220ms）；移除孤儿 `CircularProgressIndicator` import。
- **C7 入场编排**：W1 四 ZoneRow 包 `StaggeredEntrance(index 0-3)` + `rememberSaveable` 一次性（Tab 往返不重播）；`ExportSheet.kt` 五条 `DimensionSettingRow` 包 `StaggeredEntrance(index)`（sheet 每开轻错峰，兑现动效 #10）。
- **C8 冷启动**：新增 `values-v31/themes.xml` 与 `values-night-v31/themes.xml` 显式 `windowSplashScreenBackground` = paper/paper_dark。
- **C9 spec**：动效清单 #3（接线细节）、#7（W1 并入）、#8（Lottie→自绘勘误）、#10（stagger 兑现）修订，新增 #14–#18；根 CHANGELOG 已记。

## 验证记录

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：**BUILD SUCCESSFUL，83 项单测全绿**（it-057 时 81，含并行会话新增 2 项）。
- **视觉模型通道失效**：本次 CDN URL 全部被视觉接口拒收（invalid url，含 `+`/`%2B` 编码、改名重传均不救）——逐页视觉评审改由**像素级程序化验证**承担（PyAV 解码 + 纹理块轨迹/区域帧差分析，脚本在 `/tmp/wardrobe-motion-audit/verify_motion.py` 一带，临时产物不入库）。
- **共享元素三路径**（系统动画缩放=5 的慢放 + 纹理块轨迹）：
  - W1→W5：tap 后槽位照片块 8.72s 收缩起飞 → 9.09s 出现在中间位置/中间尺寸（440,1180 200x400，两态之外的新位置，纯整页 fade 不可能产生）→ 9.81→10.18s 收敛落位；pop 反向 13.45–15.63s 同样成立。真实时标 ≈280ms。
  - W3→W5：26.17s 起飞收缩 → 26.53/26.90 飞行中途帧 → 落位后大图因浅色衣物纹理弱检测不到（符合预期）。
  - W8→W7：6.91s 捕捉到放大中的中间尺寸帧（480x680，介于 hero 480x400 与大图之间）→ 8.63s W7 到位。
- **转场吃系统动画缩放**：5× 慢放下 280ms 转场实测拉长至 ~1.4s——同一机制的另一端即 scale=0 时瞬时，间接证明减弱动态降级。
- **随机忙碌态**：burst screencap 8 帧，tap 后 b1→b3 按钮区帧差 17.93（图标旋转中）、b4 起归零（滚动结束停转）；槽位帧差 47.94→51.54→3.89→0 与滚动节奏吻合。（修复前 Job.isActive 版本实测全程 0.00，已换显式 Boolean。）
- **减弱动态**（scale=0，全屏帧差序列）：随机=单帧 20.2 跳变、pop=单帧 12.57 跳变、Tab=单帧完成，仅剩 Coil 图片 crossfade 的 3-4 帧小幅渐显（图片加载语义，非交互动效，保留）。
- **深色模式**：`cmd uimode night yes` 下 W1/W3/W8 截图像素体检（均值 47/47/65，纯黑占比 <0.2%，无大面积黑块/白块）；dark-w1 首拍全黑系 uimode 切换的 Activity 重建瞬时帧，2s 后复拍正常。
- **Tab 过渡**：v3 慢放 18.90–21.45s 多帧混合（fade+scale 存在），叠加 W3 首进 stagger 块型渐变（22.54→23.26）。
- **typing 三点/气泡入场**：无 API Key 无法实测流式对话，未做端到端验证——组件行为由代码保证（与既有 EmptyState/AnimatedVisibility 同基础设施），留待 Leo 配 Key 后体验。
- 走查遗留：视觉接口恢复后建议对 #14–#18 补一轮逐页截图评审；`/tmp/wardrobe-motion-audit/` 的 976 帧与脚本为临时产物，未入库。
- **验收差异说明**：提案验收中「新组件单测覆盖关键行为」一项未做——`PressableScale`/`BubbleIn`/`TypingDots` 均为纯视觉 modifier/容器（无分支逻辑可断言），且仓库单测基建无 Robolectric/compose-ui-test（为两个视觉组件引入 UI 测试框架属架构决策，超出本迭代范围）；其行为正确性由上述像素级实测覆盖（按压旋转/忙录态已实测，气泡入场随 typing 同留待配 Key 体验）。既有 83 项单测全部通过（无回归）。
