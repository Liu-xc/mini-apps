# mini-apps 仓库架构评审报告

- **日期**：2026-09-20
- **评审对象**：整个仓库的工作树当前状态（含尚未提交的 it-007 / it-008 / it-018 / it-019 变更）
- **评审方法**：三个并行深查（wardrobe / eats / libs+根级）+ 对最重论断的逐条人工复核（文件行数、接口宽度、writeHook 使用、Mock 漂移、DemoMode 重复均实读代码确认）
- **规模盘点**：

| 模块 | 主代码 | 测试 |
|---|---|---|
| wardrobe | 48 个 .kt / 9,131 行 | 8 文件 / 50 @Test |
| eats | 54 个 .kt（主源码 45 + 测试 9）合计 7,432 行 | 9 文件 / 49 @Test |
| libs/store | 3 文件 / 170 行 | 14 @Test |
| libs/sync | 12 文件 / 1,307 行（contract 532 + bitable 775） | 49 @Test |
| libs/carddeck | 1 文件 / 106 行 | 0 |
| libs/cutout | 5 文件 / 352 行 | 15 @Test |

---

## 1. 总评

**结论：这是一套克制、自觉、底子很好的架构，但 wardrobe 正站在「小应用架构」撑不住「中型应用体量」的临界点上，而 spec-driven 铁律的执行已经出现系统性松动。**

具体说：分层纪律（domain 纯净、UI 不摸 repo）两 app 全部成立；libs 四个 SDK 的抽象面克制且有真实消费方驱动生长，反 YAGNI 文化有据可查（两轮主动砍投机 API）；演示模式、原子存储、同步契约这些设计都对个人应用恰到好处。问题集中在三处：**wardrobe 的单 God ViewModel + 26 方法宽接口 + Mock 语义漂移**（规模失控前兆）、**常青 spec 与代码大面积脱节**（对以 spec-driven 为铁律的仓库，这是最重的债务）、**零 CI + 版本 6 处手工 pin**（护栏缺失）。

| 维度 | 评分（5 分制） | 一句话 |
|---|---|---|
| 分层与依赖规则 | ★★★★☆ | domain 零违规 import、UI 零摸 repo，仅少量越界缝 |
| 抽象质量 | ★★★☆☆ | libs 侧优秀；app 侧宽接口 + 名存实亡接口 + God VM 拖分 |
| spec-driven 执行 | ★★☆☆☆ | 流程与产物完备，但常青 spec 系统性过期 |
| 测试策略 | ★★★★☆ | 纯逻辑覆盖扎实（177 @Test），VM/UI/平台层 0 覆盖 |
| 工程护栏 | ★★☆☆☆ | 无 CI、无 version catalog、文档滞后多处 |
| 复用策略 | ★★★★☆ | 「接受重复，等第三消费方」原则正确且有 ADR，但未记录的重复在增长 |

---

## 2. 仓库级架构

### 2.1 monorepo + composite build：结构合理

- 一个应用一个目录、互不依赖、各自独立可构建——README 声明的约定与实际一致。
- 跨应用 SDK 走 `libs/` + `includeBuild` composite build（wardrobe 接 store/carddeck/cutout，eats 接 store/carddeck），app 侧以 `com.leo.libs:xxx:0.1.0` 坐标消费，依赖替换正确工作。
- **libs 之间零相互依赖**（唯一的项目内依赖是 sync 内部 bitable→contract），与 AGENTS.md「应用 → libs、libs → libs 的 contract，不得反向」的铁律完全一致，全仓无一处反向。
- 依赖方向唯一的「越界」都发生在 app 内部 UI→data（见各 app 小节），仓库级铁律反而执行得最好。

### 2.2 spec-driven 工作流：流程真实存在，但常青 spec 在失效

做得好的：迭代文件齐全（wardrobe it-001~019、eats it-001~008、clips it-001 提案）；ADR 习惯真实（wardrobe 20 条、eats 14 条、cutout 5 条）；git log 提交规范 `feat|fix|docs(scope): … (#it-XXX)` 大体遵守——59 条中 46 条严格符合，13 条偏离字面（无 scope 的 docs/chore、双应用双 it 引用如 `feat(eats,wardrobe) … (#it-001 #it-006)`、1 条 git Revert），无一条随意格式。

问题在于：**AGENTS.md 自己规定「spec 与代码不一致视为迭代未完成」，而当前不一致是系统性的**——

| 位置 | 脱节内容 |
|---|---|
| wardrobe `specs/04-architecture.md` | 声称「每屏 ViewModel + StateFlow\<UiState\> + sealed Event」——全 app 实际只有 1 个 AppViewModel，UiState/sealed Event 全仓零命中；usecase 清单列了不存在的 ComposeOutfitImage / ImportItemPhoto；模块图缺 platform/、ui/recap/、ui/wishlist/；路由表缺 recap、wishlist |
| wardrobe `specs/00-overview.md:40` | 「范围外（明确不做）：背景抠图」——与已完成的 it-016 抠图**直接矛盾** |
| eats `specs/04-architecture.md` | 列了不存在的 `map/PickLocationController.kt`、usecase `ComputeStats`；缺 data/prefs/、ui/recap/、ReminderScheduler/RecapSaver；路由表过期（main vs home、缺 recap）；声称「写失败 → ViewModel 捕获 → snackbar」但 AppViewModel **0 个 try/catch**，commit 抛异常会直接崩进程 |
| 根 `README.md` | libs 表只有 store/sync 两行，**已实现且已接入的 carddeck/cutout 未列入**；apps 表缺 clips |
| `libs/store/README.md`、`libs/sync/README.md` | 仍写「架构设计中……尚未写码」——与 v0.1.0 已实现的事实矛盾 |
| 各 spec 头部测试数 | store 写「20 单测」实际 14；cutout 写「14 用例」实际 15 |
| eats it-008 | 迭代文件头部「待确认（Leo）」，CHANGELOG 却已记为完成——文档间状态不同步 |
| wardrobe 版本 | `build.gradle.kts` versionName 0.1.0 / versionCode 1，CHANGELOG 已发到 0.4.6，it-011~019 共 9 个迭代未发版 |

**根因判断**：不是流程设计问题，是迭代速度问题——it-017/018/019 在同一天落地，「④验证回填 + 常青 spec 同步」这一步被系统性跳过。spec 越旧越没人敢改，脱节是复利增长的。

### 2.3 缺失的工程护栏

- **完全没有 CI**（无 .github/workflows）。全仓 177 个 @Test 的价值完全依赖本地自觉跑 `gradlew test`；多会话并行开发（当前工作树就有 4 个迭代未提交）正是最需要 CI 兜底的场景。
- **无 version catalog**：Kotlin 2.1.21 / AGP 8.7.3 / Compose BOM / serialization 等版本在 2 app + 4 libs 共 **6 处手工重复 pin**，当前恰好对齐但无护栏；cutout 的 onnxruntime 1.20.0 与 wardrobe 的 onnxruntime-android 1.20.0 需手工保持一致（ADR 有警示但无机制）。
- 卫生问题：`.agents/` 里误提交了 `__pycache__/*.pyc`；`plugins/agent-flow/` 是无文件的空占位目录；reports/ 入库 213 个图片/PDF（png/jpg/webp/pdf，对公开仓库体积有影响，可接受但应自觉）。

---

## 3. libs 四个 SDK 逐库评审

### 3.1 store（170 行 / 14 测试）——恰到好处的抽象，两 app 均已接入

- **快照原子写**（`SnapshotStore.kt:51-59`）：json 编码 → tmp 写入 → 旧文件 copy 成 .bak → tmp rename 覆盖 → 首次提交也保证 bak。`load()` 三级恢复（主 → bak → default），rename 原子性使非加锁同步读安全，commit 走 Mutex 串行。对单用户个人应用，这比上 SQLite/WAL 的选择正确（ADR-002/008 有完整论证）。
- **迁移链**（`SnapshotStore.kt:67-79`）：逐版本链式升级、缺步安全停、版本不推进防死循环、高版本前向容忍——设计完备。但**两个 app 目前都未传 migrations**，走「可空默认字段」零迁移策略（schemaVersion 恒 1）。这不是缺陷，但迁移链属于「已建未用的能力」，下次 schema 变更时应优先启用而非继续堆可空字段。
- **SsotRepository 的 mutate 序列**是全仓库最好的小设计：mutex → transform → commit → 快照赋值 → writeHook；commit 抛异常则快照不赋值，**落盘失败天然回滚内存**（wardrobe/eats 各测试都验证了这一点）。
- **writeHook 目前是死缝**：全仓 app 源码 0 处赋值（唯二使用是 SDK 自测）。它是为 sync 预留的（ADR-010 明示），有单测覆盖语义、成本近零——**可接受的预留**，但接入 sync 前它是「为 0 消费方服务的缝」。
- 二轮 review 砍掉零消费方 API（BackupCodec、sweep、LoadOutcome）的记录在 spec §0——这是反 YAGNI 文化的实证。

### 3.2 sync（1,307 行 / 49 测试）——教科书级的契约/适配器分离，但 0 消费方

- `contract` 模块零 OkHttp/Android 依赖，`SyncValue` 七值封闭集 + `local:` 附件占位协议；`SyncEngine` 205 行覆盖状态机、分批 push、全量对账 pull、LWW 裁决、删除传播；错误折叠成 7 类 + 用户恢复文案。
- `bitable` 适配器把飞书全部私有能力（token 缓存、自动建表、分页、record_id 收编、串行写、429 退避、错误码映射表）关在边界内，对外只泄漏契约类型——**这是本仓库抽象质量最高的一段代码**。`WriteGate`（36 行）用 Mutex 对齐飞书「单表同时只发一次写」，`delayFn` 可注入以虚拟时间单测。
- **YAGNI 判断**：不是投机。上游有真实调研（wardrobe it-002：家人多端录入 + 换机备份，含鉴权/限额实测），且二轮 review 已砍掉增量游标/ConflictPolicy 等投机面。但 1,307 行 0 消费方代码躺着是要交维护税的（Kotlin/serialization 升级时要跟着改 6 处 pin 的其中一处）。**建议给接入设时间盒**：若 3 个月内不接，降级归档为 spec + 契约代码，删实现。

### 3.3 carddeck（106 行 / 0 测试）——薄得不能再薄，合理

- 手势动画全在 JitPack 三方库里，本层只做「节奏编排」（drawRandom = 均匀落点 + 55ms→340ms 减速翻张）。`currentIndex` 是消费方真实需要时才加的（git 53c1ea8）——API 生长由使用驱动，这是对的模式。
- 0 测试可以接受（纯 Compose 编排，值得测的只有减速节奏计算，抽出来也就 20 行），但它是 6 个构建里唯一没测试的。

### 3.4 cutout（352 行 / 15 测试）——bytes 进出的边界纪律好

- 公共 API 零 android.graphics / onnxruntime 类型泄漏；模型 bytes 注入（ADR-003）、会话惰性 + 空闲 5 分钟自动释放 + 独立守护线程；**双 runtime 方案聪明**：SDK `compileOnly` 桌面版 onnxruntime（编译期+测试用），移动端运行时由消费方自带 onnxruntime-android——SDK 自己不背 .so。
- 唯一有独立 `06-decisions.md` 的库（ADR-001~005），边缘锐化窗参数由测试报告驱动修订（ADR-005）——决策记录习惯最好的库。

---

## 4. wardrobe：好底子 + 三条规模失控前兆

### 4.1 成立的部分

- **domain 纯净度**：grep 验证 domain/ 全部 import 仅 kotlinx/java.util/自身包，零违规。
- **UI 不摸 repo**：ui/ 与 MainActivity 中 `.repo` 零命中（AppViewModel.kt:48 的 public `val repo` 是暴露的缝，好在无人用）。
- **手写 DI 不是 God class**：AppContainer 仅 78 行、10 个依赖、装配点唯一（ADR-003）；演示模式在组合根切换仓库与图片目录的写法干净利落；cutout 引擎模型 bytes 注入且惰性加载不进启动路径。
- 测试 50 个集中在 domain + data 级联语义（deletePerson/deleteItem 级联、悬空清洗、跨实例持久化），`cleaned()` 悬空引用清洗覆盖六域——**测的正是该测的**。

### 4.2 规模失控前兆一：单 AppViewModel 538 行、约 13 类职责

全 app 唯一的 ViewModel 承担：角色管理、槽位组合记忆、personNote、customPrompt、导出选择、coach 标记、Item CRUD、照片导入+抠图、Outfit CRUD+去重、成品图、Note、WearLog 打卡/撤销、提醒调度、Recap 长图生成+存相册+分享、心愿域全流程、toast 通道。spec 04 声称的「每屏 ViewModel」从未存在过。

雪上加霜的两处：
- `AppViewModel.kt:367` 直接实例化并调用 **UI 渲染器** `WardrobeRecapLongImage` ——VM 层做位图渲染编排，渲染器是 ui 包对象；
- `:51-53` 把 imageComposer / promptBuilder / share getter 透传给屏幕——屏幕经 VM 拿 export 层具体对象，MVVM 的边界被凿穿。

it-017~019 每个迭代都在往这个类里加 5~10 个函数，**它是当前架构里增长最快、离崩溃最近的点**。

### 4.3 规模失控前兆二：WardrobeRepository 宽接口（26 个 suspend 方法、7 个聚合域）

`domain/repository/WardrobeRepository.kt`：91 行里 26 个 suspend 方法横跨 Person/Item/Outfit/WearLog/WishItem/WishOutfit/Note 七域——单接口承担全部域，ISP 违例。直接后果就是下一条：**Mock 要手抄全部 26 个方法的语义**。

### 4.4 规模失控前兆三：Mock 仓库语义漂移（已实锤）

`MockWardrobeRepository`（292 行）手抄 Impl 全部方法语义，且**已经分叉**（本次评审实读代码确认）：

- `deletePerson`：Impl 级联删 wishItems/wishOutfits（it-019 注释明示），**Mock 不级联**（MockWardrobeRepository.kt:67-81 无此两行）；
- `deleteItem`：Impl 从 wishOutfits.itemIds 移除该件，**Mock 不移除**（MockWardrobeRepository.kt:92-98）。

即演示模式下心愿域数据与真实模式行为不一致。这是手抄 26 方法的必然结果——接口越宽，双实现漂移只是时间问题。eats 同构位置（MockEatsRepository 51 行、5 方法）没漂移纯粹是因为接口小。

### 4.5 其他值得记的问题

- **ImageStore 接口名存实亡**：接口 3 方法，但实现类 ImageFileStore 另有 cutoutTo/decode/exportDir 不在接口上，且 AppContainer.kt:37 与 OutfitImageComposer.kt:27 都以**具体类**持有——接口成了摆设。对照 eats：同位置的容器暴露的是接口（`val imageStore: ImageStore`），eats 这处更干净。
- **UI→data 越界**：WardrobeScreen.kt:60/92/304 直接调 `DemoMode.isEnabled/setAndRestart`（data.mock 包）；AppViewModel.kt:181 直接用 ImageFileStore 具体类、:325 直接引 RecapReminderPrefs 类型。
- **WishlistScreen.kt 970 行**：9 个 @Composable 塞一个文件，含 4 个完整 ModalBottomSheet 表单（WishEditSheet/PurchaseSheet/WishDetailSheet/WishOutfitDetailSheet）——单文件坏味道明确。
- `rememberPhotoPicker + vm.importPhoto` 照片导入样板在 5 个文件 8 处重复。
- WardrobeRepositoryImpl 内「收集文件 → mutate → 物理删」模式 7 处重复（可接受，模式短）。

---

## 5. eats：同构骨架的小号健康版

- 分层纪律同样成立：domain 零违规 import、UI 零摸 repo。**越界缝比 wardrobe 少且轻**：AppViewModel.kt:9 import data 层的 RecapReminderPrefs；ListScreen 三处直调 DemoMode（spec 说开关在组合根读取，UI 又读了一次做入口显隐）。
- **AppViewModel 258 行、7 类职责**——未到「过大」级，但同样是全 app 唯一 VM，且 `generateRecap` 在 VM 里做位图合成（AppViewModel.kt:241），与 wardrobe 同病：UI 渲染细节漏进 VM。
- **Mock 体系是正式设计而非脚手架**：相对时间种子（任何时候进演示模式数据都新鲜）、DEBUG 门控入口、`setAndRestart` = commit + 重启进程（注释论证了为何必须重启）。唯一的债：MockEatsRepository 的归一化逻辑与 Impl 逐行重复（注释自认），5 方法规模下可控。
- **单 composable 巨函数**：SpinScreen 641 行只有 2 个 composable（主函数 480+ 行内嵌筛选 chips/提示条/卡组/结果块）；PlaceDetailScreen 514 行 / PlaceEditScreen 423 行同样各只有 2 个 composable。对照 RecapScreen 492 行拆了 10 个 composable——**同一仓库里两种分解纪律并存，RecapScreen 是正确示范**。
- 时间处理纪律未复用：domain 的 RecapCalculator 示范了显式注入时区，UI 里「今天」计算又在 SpinScreen/ListScreen 各写一遍 systemDefault。
- **地图模块隔离方式值得表扬**：不依赖高德 SDK——「高德」只是瓦片 URL（ChinaTileSource 四主机轮询，GCJ-02 自洽不换算有 ADR-002 论证）；PlaceMarkerFactory 纯 Canvas 画分类色圆点 + ConcurrentHashMap 缓存。map/ 三文件绑 Android 类型不进 JVM 单测，与 spec 测试策略声明自洽。
- **提醒候选规则的位置比 wardrobe 对**：eats 放 domain 纯函数 MemoryCandidateSelector（注入 Random，可测，5 个测试），wardrobe 内嵌在 ReminderScheduler companion（绑 WorkManager 类，不可 JVM 测）。同一功能两 app 两种做法，应向 eats 看齐。

### spec 一致性

eats 04-architecture 有 9 处与代码不一致（幽灵文件 PickLocationController、不存在的 ComputeStats、缺 prefs/recap/platform 文件、路由表过期、「每屏 ViewModel」「VM 捕获异常」两段描述的实现不存在）；README 仍写「it-001 已完成」而实际已到 it-008；CHANGELOG it-006 标「进行中」而 it-007/008 已做完。

---

## 6. 跨应用复用：原则正确，账本不全

wardrobe ADR-012 明示「两 App 各自维护长图代码，**接受重复**，待第三个消费方出现再评估」——这个原则本身是对的（个人仓库过早抽公共层是更大的恶）。**问题是有 ADR 的重复只有长图一处，下面这些没记账**：

| 重复项 | 证据 | 备注 |
|---|---|---|
| DemoMode.kt | 27 vs 27 行，**diff 仅包名 + 一行注释**，setAndRestart 逐字同 | 纯样板，零语义差异 |
| Typography.kt | 44 vs 44 行，diff 仅包名 + 对象名 | 同上 |
| Motion.kt | 30 vs 28 行，弹簧参数完全相同（eats 注释自认「同参数」） | 同上 |
| ImageFileStore 四个私有方法 | compressWebp/decodeScaled/rotateByExif/scaleDown **逐字节相同**（MAX_SIDE=1440、QUALITY=82 一致） | WebP/EXIF 编解码是「本可进 libs/store android 模块」的典型（store spec §0 明确把这层划给 app 注入——当时的决定，现在该复核） |
| RecapPrefsStore | 49 vs 50 行，DataStore 键结构同 | 结构性重复 |
| ReminderScheduler | 161 vs 123 行，WorkManager 每日提醒同构 | 候选规则各自实现（合理） |
| DesignTokens | 53 vs 106 行，骨架同构 | **值各异，属合理的结构性重复** |
| AppContainer 骨架 | 67 vs 78 行同模式 | 组合根样板，合理 |

**建议**：clips 是第三个消费方，按 ADR-012 自己的规则，「再评估」的触发条件已经到了。不必现在抽——但应把上表补进 ADR 账本，把「接受重复」从默许变成显式决策；clips 开工时优先评估抽 DemoMode/Typography/Motion/WebP 编解码四处（前三处是无脑搬，第四处进 store 的 android 模块或独立 media 库）。

**规划矛盾待裁决**：store spec §6 写「clips Android 直接用本 SDK」，clips 自己的 04-architecture 却计划自建 JsonClipStore——两份 spec 打架，开工前必须统一（建议：用 store，理由是原子写/恢复/迁移链已测好，自建等于再抄一遍 SnapshotStore）。

---

## 7. 抽象质量清单

### 值得表扬的抽象（保持）

1. **SsotRepository.mutate 序列**（store）：commit 失败天然回滚内存，两 app 的数据一致性测试都建立在这上面。
2. **sync 的 contract/bitable 分离 + CollectionAdapter 单桥**：引擎不持有 app 存储，飞书细节零泄漏，错误折叠表 + WriteGate 可注入时间单测——全仓库抽象质量最高的代码。
3. **CutoutEngine 的 bytes 进出边界**：零 android/onnxruntime 类型泄漏，双 runtime compileOnly 方案让 SDK 不背 .so。
4. **SyncValue 七值 + local: 附件占位协议**：后端中立且附件生命周期闭环（占位→真实 ref→ack）。
5. **演示模式的组合根切换**：if (demo) Mock else Impl + 图片目录切换 + DEBUG 门控 + 确定性种子——演示与真实零接触。
6. **eats 的 MemoryCandidateSelector / BuildCandidates**：纯函数 + 注入 Random + SpinFilter 数据类承载 5 维过滤，12 个测试含边界（「恰好 N 天前算最近」）。
7. **WardrobeData.cleaned() 悬空清洗**：六域引用一致性在载入时收敛，且有测试。

### 名存实亡或缺失的抽象（修）

1. **wardrobe ImageStore 接口**：实现 3 个方法不在接口上、两处持具体类——要么把 cutoutTo/decode/exportDir 收进接口语义，要么删掉接口别装。
2. **wardrobe WardrobeRepository 26 方法宽接口**：按聚合域拆（Person/Item/Outfit/WearLog/Wish 各自接口，Impl 仍是单类），Mock 漂移的根源。
3. **UiState/sealed Event 模式**：spec 声称存在、代码从不存在。要么补实现要么改 spec——当前状态是「spec 描述了一个更好的架构但没还账」。
4. **错误处理**：spec 说「VM 捕获 → snackbar」，实际 eats 的 AppViewModel 0 个 try/catch；wardrobe 有 9 处捕获但集中在 it-017/019 时期新增的 5 条写路径（设参考照/存单品/打卡/转正/升级），删除与 Outfit/Note CRUD 等其余写路径无兜底，commit 抛异常沿 viewModelScope 崩进程。SDK 层的回滚是真的；用户层兜底一个缺失、一个覆盖不全。

---

## 8. 问题清单（按优先级）

### P0 —— 架构性，不处理会复利恶化

| # | 问题 | 建议动作 |
|---|---|---|
| P0-1 | 常青 spec 与代码系统性脱节（§2.2 表：wardrobe 9 处 / eats 9 处 / 根与 libs README 5 处），直接违反 AGENTS.md「不一致视为迭代未完成」 | 专门开一个 docs 迭代一次性对齐（04-architecture 重写为实际结构、overview 撤掉「不做抠图」、README 补 carddeck/cutout/clips、libs README 撤「尚未写码」）；此后 CI 里加 spec 与路由/文件清单的机械校验（见 P1-1） |
| P0-2 | wardrobe Mock 仓库语义漂移（deletePerson/deleteItem 缺心愿域级联），演示模式数据行为与真实不一致 | 立即补齐两条级联；根治见 P0-3 |
| P0-3 | wardrobe 单 AppViewModel 538 行/13 类职责 + 26 方法宽仓库接口，it-017~019 持续往里加 | 按聚合域拆 ViewModel（至少 Wish/Recap/Item 三块先拆）+ 仓库接口按域拆；长图渲染编排从 VM 移到 platform 层（对齐 eats ReminderScheduler→domain 的方向反过来：渲染器调用权下放 UI/platform） |
| P0-4 | 错误处理声明与实现脱节：eats VM 零捕获；wardrobe 仅 5 条新写路径有捕获、其余无兜底，commit 异常崩进程 | 在 mutate 外围或 VM 侧统一 try/catch → toast/snackbar，兑现 spec 承诺（SDK 已保证回滚，只差用户层兜底）；wardrobe 已有 9 处现成样板可推广 |

### P1 —— 结构风险，下个迭代窗口处理

| # | 问题 | 建议动作 |
|---|---|---|
| P1-1 | 零 CI，177 个测试全靠本地自觉 | 加一个最小 GitHub Actions：两 app + 四 libs 的 `gradlew test` matrix（国内镜像已在各 settings 配好）；顺手把「spec 路由表 vs MainActivity Routes」之类的机械比对塞进去 |
| P1-2 | 版本 6 处手工 pin 无 catalog | 根目录 `gradle/libs.versions.toml`，各 build 迁移引用（composite build 下各库可引用同一 catalog 文件） |
| P1-3 | wardrobe ImageStore 接口名存实亡 | 收编 cutoutTo/decode/exportDir 进接口，或删接口直用具体类 |
| P1-4 | 500~970 行单 composable 文件（WishlistScreen 970 行 4 个 sheet；SpinScreen/PlaceDetailScreen/PlaceEditScreen 各只有 2 个 composable） | 以 RecapScreen（492 行拆 10 个 composable）为内部基准重构；sheet 表单各自成文件 |
| P1-5 | wardrobe ReminderScheduler 候选规则不可测 | 照 eats 抄：抽 domain 纯函数 + 注入 clock |
| P1-6 | 照片导入样板 8 处重复 | 抽一个 `rememberPhotoImport(vm)` 组合函数 |
| P1-7 | sync 1,307 行 0 消费方 | 设时间盒（如 3 个月）：到期不接则归档为 spec+contract，删 bitable 实现 |

### P2 —— 卫生与记账，随手清

| # | 问题 |
|---|---|
| P2-1 | eats it-008 状态文档不同步（迭代文件「待确认」vs CHANGELOG「完成」）；CHANGELOG it-006「进行中」未结 |
| P2-2 | wardrobe versionName 0.1.0/versionCode 1 vs CHANGELOG 0.4.6；it-011~019 九个迭代未发版 |
| P2-3 | ADR 编号乱序（wardrobe ADR-017 在 016 前、eats ADR-011 在 010 前） |
| P2-4 | spec 头部测试数过期（store 20→14、cutout 14→15）——建议 spec 不写具体数字或写「≥N」 |
| P2-5 | `__pycache__/*.pyc` 入库、plugins/ 空占位目录、reports/ 188 个二进制 |
| P2-6 | clips 与 store 的规划矛盾（用 vs 自建 JsonClipStore）——开工前裁决 |
| P2-7 | wardrobe 无 .gitignore（eats/carddeck 有），不对称但无害 |
| P2-8 | eats「今天」计算在 UI 重复三处，未复用 RecapCalculator 的显式时区纪律 |

---

## 9. 建议路线（顺序，非实施计划）

1. **先还文档账**（P0-1 + P2 全部）：一次 docs 迭代对齐所有 spec/README——这是 spec-driven 仓库的地基，且成本最低。
2. **加护栏**（P1-1 + P1-2）：CI + version catalog。此后所有重构都有测试兜底。
3. **wardrobe 止血**（P0-2 → P0-4 → P0-3）：先补 Mock 级联（10 分钟），再统一错误兜底（半天），再做 ViewModel/接口拆分（一个完整迭代，建议 it-020 专项，不动功能只动结构）。
4. **UI 分解纪律**（P1-4/P1-6）：随下次触碰各屏时顺手做，不专项。
5. **clips 开工前**：裁决 store 复用矛盾（P2-6），顺便做「第三消费方」复用评估（§6）。

---

## 10. 值得保持的实践（这个仓库做对了的）

1. **domain 纯净 + UI 不摸 repo**——两 app 全部成立，这是很多更大的项目都做不到的。
2. **手写 DI 78/67 行**——对个人应用，比 Hilt/Koin 正确；组合根清晰、演示模式切换优雅。
3. **libs 的消费方驱动生长 + 两轮砍 API**——carddeck 106 行、store 170 行，「第三消费方出现再抽象」的克制是真的在执行。
4. **sync 的契约/适配器分离**——教科书级，值得作为 clips 双端契约设计的模板。
5. **演示模式是正式设计**——DEBUG 门控、确定性种子、组合根切换、重启论证，不是脚手架。
6. **提交规范大体遵守**——59 条提交中 46 条严格符合 `type(scope): desc (#it-XXX)`，13 条偏离字面（无 scope 的 docs/chore、双应用双 it 引用、1 条 Revert），但无一条随意格式。
7. **测该测的**——级联删除、悬空清洗、原子恢复、候选边界、退避重试；不测不值得测的（Compose 布局）。
8. **ADR 记录不可逆决策**——「为什么不用 Room」「为什么接受重复」「为什么砍 API」都有据可查。

---

## 11. 复审与勘误（2026-09-20 同日，第二轮）

初稿由三个并行考察 + 抽样复核产出；发布后对全部可证伪断言做了第二轮逐条复验（grep / diff / wc 实测），修正以下各处：

1. **P0-4 原表述过重（最重要勘误）**：wardrobe 的 AppViewModel 并非零捕获——实有 9 处 try/catch/runCatching，集中在 it-017/019 新增的 5 条写路径（设参考照/存单品/打卡/转正/升级）；零捕获的是 eats。已改为「eats 缺失、wardrobe 覆盖不全」。初稿误将 eats 的考察结果推广到了两 app。
2. **「提交规范 100% 遵守」不准确**：59 条提交中 13 条不合字面格式（无 scope 的 docs/chore、双应用双 it 引用、1 条 Revert），无随意格式；改为「大体遵守」。初稿未抽验即采信了考察结论。
3. **eats W7 线框指控撤回**：`02-wireframes.md:148-149` 已含 W7（统计回顾页）完整描述，初稿「W7 未入编号体系」一句有误，已删。
4. **eats 规模口径**：45 为主源码文件数，含测试共 54 个 .kt / 7,432 行。
5. **小数字修正**：reports 二进制实为 213（初稿 188 的口径漏了 webp/jpeg）；RecapPrefsStore 行数顺序笔误（wardrobe 49 / eats 50）。

第二轮复验**确认无误**的高危断言（均为实测）：Mock 心愿域级联漂移（Mock 与 Impl 逐行比对）；wardrobe `00-overview.md:40`「范围外：背景抠图」与 it-016 矛盾；两 app `04-architecture.md` 声称的「每屏 ViewModel / UiState / sealed Event」全仓零实现；幽灵符号（PickLocationController / ComputeStats / ComposeOutfitImage / ImportItemPhoto 均 0 命中）；wardrobe 容器:37 持 ImageFileStore 具体类而 eats:50 持接口；两 app `ImageFileStore` 的 compressWebp/decodeScaled/rotateByExif/scaleDown 归一化 diff 后逐字相同（仅作 diff 上下文出现）；`Motion.kt` 弹簧参数相同（diff 全部为注释与对象名）；两 app settings 均无 libs/sync（0 消费方）；clips 自建 JsonClipStore 与 store spec「直接用」的矛盾实存；wardrobe versionName 0.1.0 vs CHANGELOG 0.4.6；ADR 编号乱序（wardrobe ADR-017→:87 在 ADR-016→:93 前；eats ADR-011→:55 在 ADR-010→:61 前）；store spec「20 单测」实为 14、cutout spec「14 用例」实为 15；libs/store 与 libs/sync README 仍写「尚未写码」；无 .github、无 libs.versions.toml；`__pycache__` ×2 入库；eats it-008「待确认」vs CHANGELOG 已记完成。

**采信未重验的残留（单源、低风险）**：行号级引用（抽验 4/4 全对，未逐一重验）；sync 适配器的错误码折叠表/分页细节描述；「it-017~019 同一天落地」的文件时间戳；wardrobe AppContainer「10 个依赖」的清点。

---

*本报告基于 2026-09-20 工作树（含未提交的 it-007/008/018/019 变更）。所有 file:line 引用为当日实读位置；§1–§10 已经 §11 第二轮复验修订。*
