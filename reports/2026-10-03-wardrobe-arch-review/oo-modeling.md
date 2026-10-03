# 分报告五：面向对象 · 实体关系 · 抽象合理性专项审查

> 2026-10-03 补充维度（第一轮四分册覆盖正确性/韧性/性能/构建，本册专审**设计质量**）。domain 层与 Repo/Mock 类结构由主审亲自精读；libs/agent 抽象由专项通道审查（其 O-1~O-11 编号在本册沿用）。文件路径以 wardrobe/app/src/main/java/com/leo/wardrobe/ 与 libs/agent/src/main/kotlin/com/leo/libs/agent/ 为根。

## 0. 总纲判断

wardrobe 的建模风格不是经典 OOP，而是——

**函数式快照核心**（不可变 data class + 纯函数变换 + 单点突变 `SsotRepository.mutate{}`）**＋接口化边界**（repository 双实现 Mock/Impl、SDK 双轨 ChatModel/ImageModel）。

这个选择与存储形态（单文件 JSON 快照 + 原子写）自洽：不可变快照让「transform 纯函数=天然回滚」成立，级联删除是「整快照重写」而非逐实体操作。**不应该用「贫血模型 bad」的教条否定它**——给 data class 塞行为反而会破坏 mutate 的纯函数性。真正的设计债不在「不够 OO」，而在四个具体点：**一个缺失的状态机、一套无类型保护的关系引用、一处双实现的状态空间分裂、若干抽象的注意力放错了半边**。

## 1. 面向对象维度

### 1.1 SOLID 逐项评估

| 原则 | 评估 | 证据 |
|---|---|---|
| **S** 单一职责 | **半违反**。AppViewModel 九域一类 67 成员（上轮 U-4）；但 WardrobeRepositoryImpl 387 行承载 27 写点实为「领域不变量的唯一实现地」，职责单一成立 | AppViewModel.kt 全文件；WardrobeRepositoryImpl.kt |
| **O** 开闭 | **真兑现与未兑现并存**。ModelCatalog 数据驱动扩 5 厂商 SDK 零改动（真 OCP）；但 O-1 双写使「加聊天模型=改两处数据」打了对折。PromptTemplate 策略点（BuildOutfitPrompt.kt:32 `fun interface`）自 it-002 至今**只有 DefaultPromptTemplate 一个实现、从未被替换**——投机抽象 | libs/agent/ModelCatalog.kt:178-187 |
| **L** 里氏替换 | **有实锤违反**（本册核心发现，见 §1.2）：MockWardrobeRepository 与 WardrobeRepositoryImpl 对「购齐待升级的 WishOutfit」持有不同合法状态空间——同一接口的两个实现，可观察行为不同 | Mock vs Impl 对照见下 |
| **I** 接口隔离 | **拆了但未兑现**。it-021 把门面拆成 Person/Item/Outfit/WearLog/Wish/Note 六子接口，接口注释明说「新代码应按需依赖最窄的子接口」——但 grep 证实全部消费方（AppViewModel/ChatViewModel/Mock/全部测试）持有的都是 WardrobeRepository 全量门面，**零处窄接口消费**。拆分收益只剩「接口可读性分组」 | domain/repository/WardrobeRepository.kt:8-10 注释 vs 实际引用 |
| **D** 依赖倒置 | **兑现好**。domain 定接口、data 实现（含 Bitmap 经 ImageStore/ImageEditStore 双接口隔离在 data 层）；UI→VM→repo 无越层（已登记例外仅 DemoMode 直调两处）。SDK 侧 wardrobe 只依赖 ChatModel/ImageModel 接口与数据目录，未绑 OkHttp 实现 | domain/repository/ImageStore.kt 注释「domain 不引用 Android 类型」 |

### 1.2 LSP 违反实锤：两个实现的状态空间分裂（上轮 D-1 的建模级根因）

上轮 D-1（买齐心愿件重启被 cleaned() 静默删除）在数据层视角是「过滤条件写错」；从 OO 视角看，**根因是契约本身建模错位，两个实现各自用不同方式调和，分裂出不同的合法状态空间**：

```kotlin
// Impl：加载/replaceAll 时清洗——把「wishItemIds 空」视为非法态，直接删除
class WardrobeRepositoryImpl ... : SsotRepository<WardrobeData>(store, onLoad = { it.cleaned() })
//   cleaned(): .filter { it.wishItemIds.isNotEmpty() }          ← 删除"购齐待升级"态
override suspend fun replaceAll(data: WardrobeData) { mutate { data.cleaned() } }   // 同款清洗

// Mock：无任何加载边界（进程内常驻），replaceAll 原样直写
override suspend fun replaceAll(data: WardrobeData) { _data.value = data }          // 不清洗
//   且 Mock 的 purchaseWishItem 后 wishOutfits 允许 wishItemIds 为空（map 不 filter，常驻等待 promote）
```

- **Impl 的合法状态空间**：wishItemIds 空 → （下次加载即被清除）不存在。Mock：wishItemIds 空 → 合法中间态（等待 promote）。
- 语义一致性测试（MockWardrobeRepositoryTest 7 用例）只对齐了**写路径**（deleteItem 级联/deleteWishItem 联动/createOutfit 过滤，注释明说「it-020：与 Impl 对齐」），**没有也无法覆盖加载边界**——Mock 根本没有加载边界。于是演示模式永远不会暴露此分歧。
- 教训升级：**「Mock 与 Impl 语义一致性」测试策略（it-020）的盲区是生命周期边界行为**（加载/重启/进程死亡），一致性用例应包含「写后模拟 reload 再断言」的形态。

spec 03 声称的不变量「wishItemIds 至少一件」本身就是**与生命周期冲突的伪不变量**——「全部购齐、等待一键升级」是合法业务状态，却被不变量排除。正确建模见 §2.2。

### 1.3 贫血模型的裁决

全部实体为纯 `@Serializable data class`，行为总共三处（`WishItem.purchased/asSlotItem`、`WardrobeData.isEmpty`、`Item.isWishSlot` 扩展），其余行为全在 Queries（扩展函数）/ Repository（写路径）/ usecase（纯函数类）。**裁决：对本形态合理**——

- 快照存储要求实体=序列化载体，行为进 data class 会同时把逻辑钉进序列化边界；
- `mutate { it.copy(...) }` 的纯函数变换风格（immutable + copy）是 Kotlin 函数式惯例，与「充血领域模型」是两种范式，混用才糟糕；
- 唯一的「充血尝试」`WishItem.asSlotItem()`（Entities.kt:133-142）恰恰制造了 §2.3 的伪 id 问题——**错不在行为放实体，错在表示法**（把愿望伪装成事实 Item）。

### 1.4 封装缺口：非法状态可被任意构造

`WardrobeData` 及全部实体都是全公开字段，任何代码可 `copy()` 出悬空引用（itemIds 指向不存在单品）、跨角色引用（personId 错配）的「合法 Kotlin、非法领域」快照。防线只有两道且位置分散：repo 写入口过滤（createOutfit 过滤无效 id）+ 加载时 cleaned()。**「构造即合法」在类型/构造层面零保障**——这是快照聚合的固有代价，规模小时可接受，但值得在 spec 中如实记为已知取舍（当前 spec 未记）。

## 2. 实体关系维度

### 2.1 聚合建模：单一大快照聚合

`WardrobeData` 七集合扁平、无嵌套、关系全用 String id——本质是**把整个库当一个大聚合根**（快照=聚合实例，mutate=聚合事务）。这与 ADR-002/008（单文件 JSON、原子写、合并语义按 id 整实体覆盖）互为因果：聚合小了原子写就没意义，原子写了聚合内部就无需细粒度一致性。**取舍合理**。代价：① 引用完整性只能运行时保证（cleaned() 兜底而非类型禁止）；② 无聚合边界=无「谁能改什么」的静态划分，全靠 repo 单写入口纪律（该纪律实测成立，27 写点无旁路）。

### 2.2 [P1] WishOutfit 缺状态机——「伪不变量」引发的连锁（建模级最重要发现）

实体关系真相是一个**未被建模的状态机**：

```
collecting（有心愿件）──逐件购入 purchaseWishItem──▶ allPurchased（wishItemIds 空，待升级）
        │                                                    │
        ├─ deleteWishOutfit ▶ 删除                    promoteWishOutfit ▶ 转正式 Outfit + 删除
        └─ deleteWishItem（最后一件）▶ 「变空的心愿穿搭一并删除」（接口 KDoc 明文！）
```

矛盾就在这：`deleteWishItem` 的语义是「wishItemIds 变空 → **删除**」，而 `purchaseWishItem` 的语义是「wishItemIds 变空 → **保留等待 promote**」——**同一个「wishItemIds 变空」事件，两种处置**，实体上却没有区分两者的状态位，只能靠「是谁触发的事件」这一外部信息。cleaned() 无法区分这两种来路，一刀切删除（D-1）；Mock 无 cleaned 所以内存里两者并存。接口 KDoc（WishRepository.kt:17-18）甚至同时写着这两条矛盾语义而无察觉。

**修复方向**（与上轮 D-1 修法合并）：显式建模状态——推荐最小改动：保留「购齐待升级」实体（cleaned 过滤条件改 `wishItemIds.isNotEmpty() || itemIds.isNotEmpty()`，购齐的必然 itemIds 非空），并让 `deleteWishItem` 的级联改为「不删实体、只移除引用」（与 purchase 同语义，统一两条路径）；或加 `status: Collecting|AllPurchased` 字段。同步动作：Mock/Impl 一致性测试补「写后模拟 reload」用例形态。

### 2.3 [P2] String id 无类型区分 + `wish:` 伪 id：类型系统外的约定

- `personId/itemId/outfitId/wishItemId/imageFile` 全是裸 String，互相赋错编译器不救（真实发生过：上轮 D-5 的锁外检查、it-079 的绝对路径误拼，都属于「字符串当类型用」的受害者族）。
- `WISH_SLOT_PREFIX` 伪 id（Entities.kt:18-21）放大此风险：`WishItem.asSlotItem()` 产出 `id = "wish:" + id` 的**伪 Item** 流入 W1 槽位及全部下游（长图/文案/拼贴/打卡入口），靠 `isWishSlot` 扩展属性在每个消费点判定。伪 id 不落 Outfit 的防线是「createOutfit 的无效 id 过滤**天然**防御」（ADR-020 原话）——即防线恰好成立但非设计使然：`"wish:xxx"` 恰好不在 items 集合里所以被过滤。若未来某查询按 id 前缀误匹配、或 createOutfit 改为按 slot item 直接建，防线即失守。
- **修复方向**：`@JvmInline value class PersonId/ItemId/OutfitId/WishItemId`，序列化边界（@Serializable 字段）保留 String、域内传递用值类型（kotlinx.serialization 对 value class 有原生支持，成本主要是改签名）；伪 id 至少先建独立类型 `WishSlotId(value)` + `Item.wishSlotOrigin: WishItemId?` 替代前缀字符串。

### 2.4 Note 的多态关联：经典反模式的可控版

`parentType: NoteParent(ITEM|OUTFIT) + parentId: String`（Entities.kt:79-88）是「多态关联」——无外键、无类型安全、查询必带两参、cleaned 要清洗两遍。平面 JSON 快照下 sealed 泛型 parent 不易序列化，**可辩护**；但代价应记入 spec（当前 spec 把它当普通关系描述）。若未来加第三种可挂评论的实体（如心愿单品），这个形状会加速恶化——备选演进：分集合 `itemNotes/outfitNotes` 或 parentId 携带类型前缀。

### 2.5 关系基数与冗余：干净

- Item M:N Outfit 单向（`Outfit.itemIds`）+ 反查派生（`outfitsContaining`，Queries.kt:28）——**单一真源，无双向维护**，正确。
- `WearLog.personId` 冗余「写时固化」——spec/实体注释均明示是有意反范式（查询免 join），且级联删除 outfit 时 log 随之删除、冗余不失义。好。
- `OutfitImage` 值对象定位准确（file+addedAt+溯源三元组），手动/AI 共存同字段。

### 2.6 [P2] `OutfitImage.source: String` 弱类型

`source = "manual"/"ai"`（Entities.kt:57）用裸字符串而不用 enum。注释理由是向后兼容——但 enum + 默认值（`@Serializable` 缺字段读 default）同样向后兼容且 it-068 `effectStale: Boolean = false`、it-017 `refImageFile: String? = null` 已两次用这手法。现状任何拼写错误静默变成第三种「来源」，UI 角标筛选依据被稀释。一行修复：`enum class ImageSource { MANUAL, AI }` + `@SerialName` 对齐旧值。

### 2.7 [P2] 不变量守卫位置三分散

同一类「数据完整性」责任落在三个位置，规则不可发现：

| 位置 | 例 | 问题 |
|---|---|---|
| repo 写入口 | createOutfit 过滤无效 id；purchase/promote 单事务联动 | 位置正确（写是唯一入口） |
| **VM/调用方** | `createWishOutfit` KDoc「组合去重不做在此，ViewModel 层判重后调用」（WishRepository.kt:31） | **倒挂**——判重（`wishOutfitWithMembers`/`outfitWithItems`）语义上是不变量（同组合不重复建），却由调用方自觉执行；忘了调=重复实体入库 |
| 加载时 cleaned() | 悬空清洗、§2.2 的一刀切 | 兜底位置合理，但被当成「第一道防线」的错觉已造成 D-1 |

**修复方向**：判重并入 repo 写入口（createOutfit/createWishOutfit 内部查重，与 upsert 幂等语义统一）；spec 03 增补「守卫位置规约」小节：**不变量只在写入口强制、cleaned 只兜「外部导入的数据」**——目前 cleaned 也清「自己写出的数据」，这正是 D-1 的触发面。

### 2.8 effectStale：派生状态落盘的有意决策

`Outfit.effectStale`（it-068）是「成品图是否过期」的可派生标志（item 更新 vs 图录入时间），派生规则复杂所以落盘。ADR 记录完整，属可辩护的缓存式反范式。备忘：它是「必须靠事件维护的缓存」，新增任何「改单品的路径」都要记得置位——`updateOutfit` 手动改 itemIds、数据包导入覆盖 item 都是潜在漏置位点（本轮未逐一验证，建议补一条契约测试钉住）。

## 3. 抽象合理性维度

### 3.1 wardrobe 侧

- **repository 接口族**：门面+6 子接口的**分组**价值真实（KDoc 即文档），「窄接口消费」从未发生使 ISP 收益落空（§1.1）；若不打算推动窄消费，子接口的存在成本（28 方法×2 实现的 override 噪音）与收益大致打平——**不建议删，也不必再拆**。`ImageStore`（URI 导入/文件/删除）与 `ImageEditStore`（抠图候选）按「原图生命周期」切分，边界准确。
- **usecase 层**：五个用例三个是「类 + operator invoke + 注入依赖」的函数式微用例（PickRandomOutfit 注入 Random 可测、BuildOutfitPrompt 注入模板）——粒度恰当、零仪式感。唯一倒挂是 §2.7 的判重位置。
- **演示模式的策略抽象**：组合根装配切换（Mock 仓库/内存 KeyStore/独立目录）是教科书式 Strategy——UI 零感知。代价即 §1.2 的状态空间分裂，是「替身保真度」问题而非抽象设计问题。
- **UI token 架构**：`WardrobePalette`（双主题色对）+ `EditorialColors(@Immutable)` + `staticCompositionLocalOf` 下发 + M3 colorScheme 承载标准槽位——标准且克制。组件 API 全部 Compose 惯例（`rememberHaptics/rememberPhotoPicker` 工厂、`sharedPhoto/pressScale/fadingBottomEdge` Modifier 扩展、默认参数）——**组件层无抽象问题**，上轮 U-8 的硬编码色是纪律缺口非架构缺口。

### 3.2 libs/agent 侧（专项通道结论整合，编号沿用其报告）

| # | 级别 | 问题 | 一句话 |
|---|---|---|---|
| O-1 | **P1** | ProviderPreset/ModelInfo 与 ProviderSpec/ModelSpec **双事实源双写** | 同一批模型 id 在两处数据各写一遍（ModelCatalog.kt:178-187 等 5 厂商），派生规则「有 models 则忽略 preset.models」无 require 无测试——「加厂商零代码」的 OCP 承诺打对折，漏同步静默漂移。趁 0.1.0 收敛为 ProviderSpec 单源 |
| O-2 | **P2** | ImageModel **缺协议路由工厂** | KDoc 宣称「协议选择由数据决定」但 SDK 无入口承接，选哪个实现类泄漏给消费方——wardrobe 只会 new OkHttpImageModel（上轮 A-2「aitryon 可选必败」的 SDK 侧根因）。补 `ImageModel.of(spec,...)` 工厂，一改解决两侧 |
| O-3 | P2 | 双轨**终态语义不一致** | ChatModel 以异常失败 vs ImageModel 以 Failed 事件正常结束——消费方要学两种 collect 模式，无架构理由，是分期落地的层积。0.1.0 内统一成本最低 |
| O-4 | P2 | 两个生图适配器**重复实现同一传输内核** | Call.await()/download()/错误包装逐字复制（取消修复就得改两处）；internal 化共享 transport helper |
| O-5 | P2 | `resolution` 与 params 的 `"size"` **同一 wire 字段两条入参路径** | 优先级靠 put 顺序隐式决定（extra 静默覆盖 resolution），接口与目录均未声明，无测试钉住 |
| O-6~O-11 | P3 | 备忘合集 | 聊天轨缺 supportsVision 能力位；httpCode=-1 哨兵；ModelCatalog 编译期 object（OCP 边界=改数据也发版，自用可接受）；preset/provider 命名不对称（与 O-1 同根）；工具无 readOnly 语义位；Usage 注解漂移/ToolCallDelta 零消费等卫生项 |

SDK 的整体风格是 **interface-first 的壳 + data-driven 的芯**，与「一人、两 app、CI 不打真 API」的服务人群匹配——**过度设计几乎没有，债集中在 it-077 桥接期**（O-1/O-9 保留旧层级的代价）与**注意力放错半边**（O-2 消费方真会踩的路由没人做）。其亮点（Message/wire 分层且 payload 不上 wire 由结构物理保证、错误按处置方式分类、终态契约测试钉住、Fake 双用途进 main/testing 包）见其原报告，本册不重复。

## 4. 建议汇总（并入总报告路线图）

| 优先级 | 动作 | 归属 |
|---|---|---|
| **P1** | WishOutfit 状态机修复（§2.2：cleaned 条件 + deleteWishItem 级联语义统一；或 status 字段）+ Mock/Impl 一致性测试补「写后模拟 reload」形态 | 并入上轮 it-082 数据边界加固（it-080 已被并行会话占用，路线图顺延） |
| **P1** | agent O-1 收敛 ProviderSpec 单事实源（assert + 契约测试起步） | 并入 it-081 AI 韧性收口 |
| **P2** | agent O-2 `ImageModel.of` 工厂（连带修上轮 A-2 的 UI 过滤） | it-081 |
| **P2** | ID 值类型化（PersonId/ItemId/OutfitId/WishItemId；至少先 WishSlotId 替代 `wish:` 前缀） | it-082 或独立小迭代 |
| **P2** | OutfitImage.source 改 enum；判重并入 repo 写入口；spec 03 补「守卫位置规约」（不变量只在写入口强制、cleaned 只兜外部数据） | it-082 |
| **P2** | agent O-3 双轨终态统一、O-4 传输内核、O-5 size 单通道 | it-081 |
| **P3** | effectStale 置位契约测试；Note 多态关联代价记入 spec；伪不变量教训写 LESSONS | 收尾随手 |

**一句话总评**：这套代码的范式选择（函数式快照 + 接口边界）是对的且执行到位；设计债不在范式而在**三个具体缺口**——WishOutfit 的生命周期没有建模载体（衍生出伪不变量→双实现状态分裂→D-1 数据丢失）、关系引用全靠字符串约定（伪 id 是其最危险的形态）、SDK 桥接期留下的双事实源与半截抽象。都是小切口可修的，没有一个需要推翻重来。
