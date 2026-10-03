# 分报告三：UI 层 + 状态管理深度审查

> 审查通道 C · 全部结论基于逐文件精读（ui/ 46 文件 14,708 行 + MainActivity/WardrobeApp），P1 级发现经主审复核源码确认。

## A. 事实基线

### A1. UI 文件行数（ui/ 共 46 文件，14708 行）

| 文件 | 行数 | 有独立 VM |
|---|---|---|
| wishlist/WishlistScreen.kt | 1125 | 无（AppViewModel） |
| outfit/ExportSheet.kt | 886 | 无 |
| settings/SettingsScreen.kt | 758 | **SettingsViewModel** |
| recap/WardrobeRecapScreen.kt | 758 | **RecapViewModel** + appVm 双持 |
| outfit/OutfitScreen.kt | 699 | 无 |
| chat/ChatScreen.kt | 693 | **ChatViewModel** |
| records/OutfitDetailScreen.kt | 674 | 无 |
| wardrobe/ItemEditScreen.kt | 584 | 无 |
| records/OutfitGenerateSheet.kt | 577 | 无 |
| chat/ChatResultCards.kt | 565 | —（纯组件） |
| **ui/AppViewModel.kt** | **560** | — |
| wardrobe/WardrobeScreen.kt | 551 | 无 |
| recap/DataPackageSection.kt | 534 | 用 RecapViewModel |
| detail/ItemDetailScreen.kt | 524 | 无 |
| records/RecordsScreen.kt | 485 | 无 |
| components/SlotGrid.kt | 448 | — |
| chat/ChatMarkdown.kt | 423 | — |
| chat/ChatViewModel.kt | 406 | — |
| outfit/PersonSheet.kt | 355 | 无 |
| 其余（settings/recap VM、theme、~18 个小组件） | ≤309 | — |

### A2. AppViewModel 体型
- **67 个公开成员**：50 个 public fun + 17 个 public val（其中 12 个 StateFlow：toast/actionToast/data/currentPerson/slotSelections/lastExportedItemIds/personNote/customPrompt/exportSelections/exportSelectionsReady/coachSlotsShown/mixWishes；4 个服务 getter：imageComposer/promptBuilder/share/imageGenerator；外加 **public `val repo`**）。
- 域覆盖：Person/Item/Outfit/Note/打卡/心愿×5/图片导入抠图/toast×2/偏好记忆×5——九个域一个类。
- **13 个 UI 文件以参数形式直接持有 AppViewModel**（Outfit/Records/Wardrobe/ItemEdit/ItemDetail/OutfitDetail/Wishlist/WardrobeRecap/PersonSheet/ExportSheet/OutfitGenerateSheet/ChatScreen(appVm)+DataPackageSection 经 recapVm），另 MainActivity 创建它。
- 4 个域 ViewModel（Recap/Settings/Chat + 列表共用 ChatViewModel）均在 `WardrobeRoot` 顶部 activity 级 `viewModel()` 创建（MainActivity.kt:158-161）。

### A3. 路由对照（spec 声称 vs 实际）
Routes object（MainActivity.kt:136-149）声明 8 条，NavHost 8 个 `composable()` 全部落位，与 spec 04 §「页面导航」一致：`home`(四 Tab：OUTFIT/RECORDS/WARDROBE/CHAT)、`itemEdit?itemId={itemId}`(可选 query)、`itemDetail/{itemId}`、`outfitDetail/{outfitId}`、`recap`、`wishlist`、`settings`、`chat/{sessionId}`。Tab 用 `AnimatedContent` 切换（切 Tab 即销毁重建页面，筛选已 rememberSaveable）。两处 spec 漂移见 U-15。

### A4. 无独立 VM、逻辑写在 Composable 里的屏
WardrobeScreen / OutfitScreen / RecordsScreen / ItemDetailScreen / ItemEditScreen / OutfitDetailScreen / WishlistScreen / PersonSheet / ExportSheet / OutfitGenerateSheet——符合 spec「用户操作走普通函数」的设计取舍。最重的是 OutfitScreen（pager 恢复/持久化、组合推导、随机编排全在 Composable 内，OutfitScreen.kt:154-246）。

## B. 问题清单

**U-1 ｜ P1 ｜ 组合期执行导航副作用**
- 证据：`detail/ItemDetailScreen.kt:94-97` 与 `records/OutfitDetailScreen.kt:100-103`
  ```kotlin
  if (item == null) { onBack(); return }
  ```
- 影响：`popBackStack()` 在组合期间调用是未定义行为模式——item 变 null 的一帧里若发生多次重组可能连 pop 多级返回栈；也违 UDF（副作用应在 `LaunchedEffect`/回调里）。删除数据后的自然回退路径之外，任何数据变更致 null 都会触发。
- 修复：改 `LaunchedEffect(itemId) { if (item == null) onBack() }` 并渲染轻量占位。

**U-2 ｜ P1 ｜ 表单状态无进程重建/配置变更保护**
- 证据：`wardrobe/ItemEditScreen.kt:100-113` 全部 `remember { mutableStateOf }`（importedFile/cutoutFile/name/category/color/desc/tags）；`wishlist/WishlistScreen.kt:629-637`（WishEditSheet）、`records/OutfitDetailScreen.kt:116-121`（editingItems/draftItemIds）同款。
- 影响：manifest 锁定 portrait 故旋转不触发，但**系统深色切换（uiMode）、字号变更、进程回收**都会重建 Activity → 用户填了一半的表单清零；且 `importedFile/cutoutFile` 引用丢失后已落盘的临时图成孤儿文件（无人再调 `deletePhotoFile`）。
- 修复：文本/枚举态换 `rememberSaveable`（枚举可存 name）；已导入文件名一并 saveable。对比：W3/W8 筛选（WardrobeScreen.kt:96-97）已正确做了，属同一迭代漏网。

**U-3 ｜ P1 ｜ 「整库流」重组合放大：item 级 collect + 实体不稳定**
- 证据 1：12 处 `vm.data.collectAsState()`，其中两处在**列表 item 内部**：`detail/ItemDetailScreen.kt:476`（OutfitThumb，被 W8 网格与 W5 相关穿搭 LazyRow 逐卡调用，RecordsScreen.kt:296、ItemDetailScreen.kt:410）与 `records/RecordsScreen.kt:402`（OutfitDeckCard）。
- 证据 2：`domain/model/Entities.kt` 全部实体为 `@Serializable data class` 含 `List` 字段，**无任何 `@Immutable/@Stable`** → Compose 判不稳定，消费 `data` 的组合无法 skip。
- 影响：任意一次写（加一条评论、打一次卡——`WardrobeData` 整体替换为新实例）→ 每个可见 OutfitThumb/DeckCard 全部重组并重跑 `remember(data, outfit){ mapNotNull }`。衣橱数据量（数百穿搭）下这是最现实的滚动/写并发卡顿源。
- 修复：① 调用点预派生 `items` 传入（部分调用点已这么做，OutfitThumb 未）；② 实体加 `@Immutable` 或换 ImmutableList，解锁 skippable。

**U-4 ｜ P2 ｜ AppViewModel 滑向上帝对象 + 公开 repo 越层口子**
- 证据：67 公开成员（A2）；`AppViewModel.kt:46` `val repo = container.repository` 为 public；`exportSelections/exportSelectionsReady/setExportSelections`（157-165）自 it-061 修3 后 UI 已不读写（ExportSheet.kt:115-118 注释自认），成为死公开 API。
- 影响：grep 证实当前 UI 无人用 `vm.repo`（纪律良好），但公开面本身违反 spec 04:20「UI 永远通过 ViewModel 间接拿数据」；67 成员的类每个域改动都碰它，Review 半径持续膨胀。
- 修复：`repo` 改 private；wishlist 域（5 函数+1 流）照 RecapViewModel(it-021) 先例拆 `WishlistViewModel`；删除死 API。

**U-5 ｜ P2 ｜ onDone(Boolean) 契约在异常路径不完备**
- 证据：`AppViewModel.kt:436-463` saveWishItem 与 `:325-352` updateOutfitItems 走 `launchSafely`——**校验失败回调 `onDone(false)`，repo 异常只 toast 不回调**；`createOutfit`(:314)/`duplicateOutfit`(:355) 异常时 onDone 永不调用。对比 saveItem(:252-284)/purchaseWishItem(:471-503)/applyPhotoRecut(:233-249) 自管 try/catch，两路都回调——同类操作三种契约。
- 影响：依赖回调收尾的调用方在异常时无收尾信号（WishEditSheet.kt:752 目前「sheet 留开可重试」是侥幸正确的表现，非设计保证）。
- 修复：`launchSafely` 增加 `onError: (() -> Unit)?` 参数或在表单类函数统一自管 try/catch 调 `onDone(false)`。

**U-6 ｜ P2 ｜ ChatScreen 流式渲染：每 token 屏幕级重组 + 全文重解析**
- 证据：`chat/ChatScreen.kt:139` `streaming` 在屏幕体级 collect；`:257-273` live item `AiBubble(text = streaming + "▍")`；`chat/ChatMarkdown.kt:269` `remember(text, wardrobeItems) { parseAssistantReply(text) }` 每 token 对全文重跑行级正则；`ChatMarkdown.kt:52-64` `Column { lines().forEachIndexed }` 无行级 key（未变行因参数相等可 skip，末行仍整行重建）。
- 影响：每 TextDelta → ChatScreen 全体重组 → 可见历史气泡因捕获 unstable `recommendationItems`(List) 无法 memo 而逐个重执行。数百字回复 × 10-30 delta/s 量级下可感（低端机打字感掉帧），消息列表本身已虚拟化（LazyColumn + 稳定 key `row-N`，`:300`）。
- 修复：把 streaming 读取下沉进独立 `StreamingBubble` composable（体级只读布尔）；delta 合并节流 30-50ms 再更新 StateFlow。

**U-7 ｜ P2 ｜ 巨型 Composable**
- 证据：`settings/SettingsScreen.kt:64-758` —— **单个 @Composable 约 690 行，全文件零私有子组件**（grep 证实唯一函数声明）。聊天卡+生图卡+外观+演示模式+用量全在一个函数体。次名：WishlistScreen.kt 1125 行（9 个 composable，结构尚可，按 sheet 拆文件即可）。
- 影响：重组粒度粗（任一 StateFlow 变化整函数重执行）、可读性/可测性差。
- 修复：按「连接卡/生图卡/外观卡/用量卡」拆 4-5 个私有 composable，纯展示态用参数下传。

**U-8 ｜ P2 ｜ 硬编码色值游离于 token 之外**
- 证据（grep，theme/ 外 30 处 / 8 文件）：`wishlist/WishlistScreen.kt:101-110`（8 个品类灰阶）、`components/SlotGrid.kt:242,295,414`（衬纸 0xFFF2F3F5 ×2、名称条 0x8C000000）、`ItemDetailScreen.kt:175-176` 与 `ItemEditScreen.kt:283-284`（棋盘格 0xFFF2F3F5/0xFFE1E3E8）、`WardrobeRecapScreen.kt:499-504`（图表墨阶 6 档）、`DataPackageSection.kt:65-70`（**DangerTint=0xFFFDECEC 浅粉，深色模式未适配**）、`Confetti.kt:35`。`Color.White` 11 处（多为压深底文字，可接受）。
- 影响：0xFFF2F3F5 重复 4 处属复制粘贴；DangerTint 在深色主题下是亮粉底，是唯一的真·深色覆盖缺口（棋盘格/灰阶属刻意的中性语义色，但应进 `WardrobePalette` 备档）。

**U-9 ｜ P2 ｜ LaunchedEffect 内做业务写操作（约 8 处）**
- 证据（D 节有全表）：`outfit/ExportSheet.kt:184-196`（去抖写 DataStore personNote/customPrompt）、`outfit/OutfitScreen.kt:144-152`（markCoachSlotsShown 落盘）与 `:165-172`（**snapshotFlow 收 settledPage → 每次翻页即写 DataStore**，无去抖）、`ExportSheet.kt:166-182` regenerate（合成长图文件）、`records/OutfitGenerateSheet.kt:164-167`（合成参考长图）、`recap/WardrobeRecapScreen.kt:137-141`（startImport 重导入）、`chat/ChatScreen.kt:179`（open→ensure 会话）。
- 影响：均为「effect 当事件通道」的可辩护折衷（注释里普遍写了动机），但翻页写盘频率 = 手滑频率，与「UI→VM 函数」的正道有偏差。
- 修复：`setSlot` 收集处加 300ms 去抖；其余可接受但建议集中到 VM。

**U-10 ｜ P3 ｜ 路由参数无类型安全**
- 证据：`MainActivity.kt:136-149` 全 String 手拼 + `getString(...).orEmpty()` 空串兜底（:288,:343,:360），空 id 靠「null→onBack」静默退出（联动 U-1）。未用 navigation-compose type-safe API。参数全是 id 字符串，实际风险低。

**U-11 ｜ P3 ｜ FullscreenSheet 的返回拦截无反馈**
- 证据：`components/FullscreenSheet.kt:42-66`——Dialog 承载，`dismissGuard=true`（生成进行中）时 `onDismissRequest` 为 no-op、✕ 置灰：**系统返回键被完全吞掉且无任何提示**。双模式（预览⇄生成 AnimatedContent 220ms 交叉淡化）本身实现质量好（ExportSheet.kt:220-228），phase 由宿主持有使关闭守卫可行，是正确设计。
- 修复：guard 命中时 toast「生成中，完成后可关闭」。

**U-12 ｜ P2 ｜ 主线程整包拷贝 zip**
- 证据：`MainActivity.kt:103-123` `handleImportIntent` 在 onCreate/onNewIntent 主线程把分享来的 zip 整体 copyTo 缓存。大包（图多的备份几十 MB）卡启动/返回交互。

**U-13 ｜ P3 ｜ ChatViewModel 单例语义**
- 证据：activity 级单例（MainActivity.kt:161），W12 列表与 W13 详情共享；`open(sessionId)`（ChatViewModel.kt:119-134）切会话时整体重置。单窗口下正确；`_messages` 等无 session 作用域隔离，未来多入口并发会互踩。观察项（与 ai-backend.md A-12 联动）。

**U-14 ｜ P3 ｜ 组合期同步 IO 小点**
- 证据：`wardrobe/WardrobeScreen.kt:103` `remember { DemoMode.isEnabled(context) }` 组合期同步读 SharedPreferences（单 key，开销可忽略，记录备查）。

**U-15 ｜ P2 ｜ spec 与代码漂移两处**
- ① `specs/04-architecture.md:21` 例外清单只记 WardrobeScreen→DemoMode，实际 `settings/SettingsScreen.kt:51,752` 也直调 `DemoMode.setAndRestart`（同类 ui→data.mock 越层，未登记）；ChatViewModel.kt:237 引 `data.mock.CachedMockChatModel` 属 VM→data，分层合法。
- ② spec:79 仍写「W6(ExportSheet)…为 ModalBottomSheet 而非路由」，it-077 已改为 FullscreenSheet(Dialog)——spec 未回填。
- 按 AGENTS.md「spec 与代码不一致视为迭代未完成」，两处都应补记。

**U-16 ｜ P3 ｜ 懒列表 key 覆盖缺口（低影响）**
- 证据：`chat/ChatResultCards.kt:530,546`（ResultBrowserSheet 两处 `items(live.size)` 无 key）、`OutfitGenerateSheet.kt:301,382`（静态短列表）。列表在 sheet 存续期内不变，实际无错位风险；对照其余 13 处均已带 key。

## C. 亮点

1. **it-071 重组性能专项落地质量高且成体系**：W8 废除「外层 verticalScroll+固定高网格」反模式改单容器（RecordsScreen.kt:103-105 注释存档）；派生列表全 remember 化（WardrobeScreen.kt:120-127、OutfitScreen.kt:190-221）；deck 状态读取下沉到 `DeckCounter`/`RandomButton` 独立组合；`SideEffect { deck = controller }` 替代组合期写状态（RecordsScreen.kt:230）；`CountUpLabel` 把动画读取隔离在叶组合（CountUp.kt:41-46）；`BodySlot` remember ImageRequest 保 Coil 请求身份（BodyCollage.kt:206）。
2. **写路径统一兜底**：`launchSafely`（AppViewModel.kt:81-94）+ 全局 snackbar（普通/带动作两种，MainActivity.kt:175-190，消费后置 null 避免去重丢显）；内存回滚交给 libs/store commit 序列，职责切割清晰。
3. **Motion/无障碍纪律**：`EditorialMotion` 集中弹簧参数并自实现「系统移除动画」降级的 pagerFling（Motion.kt:59-75，含官方 SnapFlingBehavior 不吃系统缩放的字节码考证）；触控 48dp 基线、破坏性操作警示红+确认弹窗全站一致；共享元素严格只挂低频导航（it-064 撤销卡组 hero 有完整决策注记，RecordsScreen.kt:423-424）。
4. **Chat 域只读门禁与零信任渲染**：`canChat` 发送前二次校验（ChatViewModel.kt:150-155）防「Key 清除后留无响应消息」；工具结果卡 live 事件与历史回放同一解析入口、只存 id 引用实时取快照（改名换图自动新鲜，删除落占位行，ChatResultCards.kt:50-54）；推荐卡匹配不上的单品灰显不可点（ChatMarkdown.kt:331-351）——不把 LLM 输出当衣橱事实。
5. **FullscreenSheet 双模式**：以 Dialog(usePlatformDefaultWidth=false) 全屏化解决 92% 屏高 BottomSheet 的嵌套滚动/双 sheet 接力问题，预览⇄生成同面板 AnimatedContent 切换，phase 宿主持有支撑关闭守卫（ExportSheet.kt:204-243）。
6. **导航/深链闭环完整**：通知 EXTRA→itemDetail 经 companion StateFlow 中转（进程重建后 onCreate 重读 intent，MainActivity.kt:71-73,166-173）；zip 直达导入→回顾页承接的双通道（SAF + ACTION_SEND/VIEW）都在 root 层接线。

## D. 量化

- **行数 Top10**：WishlistScreen 1125 / ExportSheet 886 / SettingsScreen 758 / WardrobeRecapScreen 758 / OutfitScreen 699 / ChatScreen 693 / OutfitDetailScreen 674 / ItemEditScreen 584 / OutfitGenerateSheet 577 / ChatResultCards 565（AppViewModel 560 居 11）。
- **AppViewModel**：560 行、67 公开成员（50 fun + 17 val，12 StateFlow）；被 17 个文件按名引用，其中 13 个 UI 文件以参数持有；`vm.data`（整库流）collectAsState 共 **12 处**、分布 9 文件。
- **LaunchedEffect 共 32 处**（14 文件）；其中做业务/数据写约 **8 处**：ExportSheet×3（setPersonNote/setCustomPrompt/regenerate 文件写）、OutfitScreen×2（markCoachSlotsShown/setSlot 收集）、OutfitGenerateSheet×1（合成参考长图）、WardrobeRecapScreen×1（startImport）、ChatScreen×1（open→ensure 会话）；DataPackageSection×2 为状态消费（borderline）。纯动画/延时类 16 处，读取类 8 处。
- **rememberSaveable 14 处**（7 文件：MainActivity tab、W3/W8 筛选、入场标志×3、W7 showCollage 等）；**derivedStateOf 0 处**。
- **懒列表 16 处**：13 处带 key（W3/W8/W12/W10 主列表全带 key+部分 contentType），3 处无 key（U-16，皆 sheet 内静态短表）。
- **硬编码色**：theme/ 外 `Color(0x` 30 处/8 文件；`Color.White` 11 处。
- **data.mock 在 ui/ 的引用 3 处**：WardrobeScreen（已登记例外）、SettingsScreen（**未登记**，U-15）、ChatViewModel（VM→data，合法）。
- **onDone(Boolean) 契约函数 5 个**：saveItem/applyPhotoRecut/purchaseWishItem 完备；saveWishItem/updateOutfitItems 半完备；createOutfit/duplicateOutfit 异常路径缺失（U-5）。

### 总评
spec 声称的「全局 AppViewModel + 域 VM + onDone 回调 + 8 路由 + sheet 非路由」与代码基本一致（两处 spec 漂移见 U-15）。最值得排期的三件事：U-1/U-2（正确性：组合期导航副作用、表单无重建保护）、U-3（性能：item 级整库流收集 + 实体无稳定性标注）；U-4/U-7（结构债：AppViewModel 67 成员、SettingsScreen 690 行单体）可在下一个功能迭代顺手偿还。
