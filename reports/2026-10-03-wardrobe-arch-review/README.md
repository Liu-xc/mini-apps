# wardrobe 前后端架构深度审查报告

- **日期**：2026-10-03（同日补充设计质量维度分册）
- **审查对象**：`wardrobe/`（app 89 文件 / 18,917 行 + `:baselineprofile`）及其后端依赖 `libs/agent`（BYOK 模型 SDK，main 2,286 行）、`libs/store`（快照存储 SDK）
- **方法**：4 个并行深审通道（数据与领域层 / AI 网络链路 / UI 状态层 / 构建与测试）+ 1 个补充维度通道（面向对象/实体关系/抽象合理性），全部结论基于逐文件精读，P1 级发现经主审复核源码确认
- **分报告**：[数据与领域层](data-layer.md) · [AI 后端链路](ai-backend.md) · [UI 与状态管理](ui-state.md) · [构建模块与测试](build-testing.md) · [OO·实体关系·抽象](oo-modeling.md)

---

## 1. 总体判断

**架构骨架健康，质量高于典型个人项目；当前债务不在「设计」而在「边界」——失败路径、跨会话状态、取消传播这三类边界行为缺少显式契约。**

- 分层规范（domain 纯 Kotlin / data 实现 / SSOT 单向数据流 / 手动 DI 组合根）实测零违规，且有单测固化；
- `libs/agent` 的数据驱动多厂商抽象（ADR-030）真兑现：扩 5 家聊天厂商 SDK 零改动；
- Key 安全红线（Keystore AES-GCM、mask、缓存用摘要、不上 wire 不进数据包）执行彻底；
- 测试选点准（112 个测试全打纯逻辑层，含不变量与 Mock↔Impl 一致性），但**最大外部依赖 libs/agent 的 94 个测试不在 CI**，且 it-077~079 三连 hotfix 的主战场 `OutfitImageGenerator` 零测试。

**问题总量：P0×0 · P1×12 · P2×36 · P3×8**。无阻断级问题；12 个 P1 中 6 个是小切口修复（≤20 行），建议按 §5 路线图在 2-3 个迭代内清零。

## 2. 架构基线（实测）

```
ui/ 14,708 行 (78%) ── AppViewModel(560行,67公开成员) + 3 个域 VM(Chat/Settings/Recap)
domain/   842 行 ── 实体/查询/合并 全纯函数，零 Android import（grep 验证）
data/   2,207 行 ── WardrobeRepositoryImpl(387行,27写点全经 mutate{}) + 图片/偏好/会话/生图/mock
export/   440 行 / platform/ 127 行 / di/ 146 行（组合根手动装配）
后端 = libs/agent（8 聊天厂商 + 生图三协议，SSE 自解析，FileSessionStore/UsageLedger）
     + libs/store（SnapshotStore 原子写 + SsotRepository Mutex 串行 + PackageCodec）
导航 8 路由与 spec 一致；包依赖纪律干净但无 Gradle 层 enforce（单模块无从 enforce）
```

「前后端」形态：**无自建服务端**，后端即「libs SDK + 厂商 API 直连 + 本地文件持久化」。这个形态与 ADR-024（BYOK 直连、无运维）一致，审查未发现需要推翻的架构决策。

## 2.5 设计质量维度补充结论（详见 [oo-modeling.md](oo-modeling.md)）

**范式判断**：wardrobe 是「函数式快照核心（不可变 data class + 纯函数 + 单点 mutate）+ 接口化边界（Mock/Impl 双仓库、SDK 双轨）」，不是经典 OOP——与单文件 JSON 快照存储自洽，**不应套「贫血模型」教条否定**。设计债在四个具体点：

1. **[P1] WishOutfit 缺状态机（D-1 的建模级根因）**：「wishItemIds 至少一件」是伪不变量——「全部购齐等待升级」是合法业务状态却被不变量排除。deleteWishItem 与 purchaseWishItem 对同一「wishItemIds 变空」事件给出**删除 vs 保留**两种矛盾处置；Impl 用 cleaned() 一刀切删、Mock 无加载边界而容忍——**两个实现合法状态空间分裂（LSP 实锤）**，一致性测试只对齐写路径、无「写后模拟 reload」用例，故演示模式永不暴露。
2. **[P1] libs/agent 双事实源（O-1）**：ProviderPreset/ModelInfo 与 ProviderSpec/ModelSpec 同批模型 id 双写两处（5 厂商），派生规则无 assert 无测试——「加厂商零代码」的 OCP 承诺打对折。
3. **[P2] String id 无类型 + `wish:` 伪 id**：关系引用全靠字符串约定，伪 Item 混入下游靠 `isWishSlot` 逐点判定，「不落 Outfit」的防线是 createOutfit 过滤的**巧合**而非设计。
4. **[P2] SDK 抽象注意力错位（O-2/O-3/O-4/O-5）**：消费方真会踩的协议路由工厂没人做（A-2 的 SDK 侧根因）、双轨终态语义不一致、传输内核双份复制、size 双通道优先级靠 put 顺序。

SOLID 记分：S 半违反（AppViewModel）/ **O 半兑现**（ModelCatalog 真兑现，PromptTemplate 策略点从未被替换）/ **L 有实锤违反**（§1）/ **I 拆而未用**（6 子接口零处窄消费）/ D 兑现好。另有 OutfitImage.source 弱类型、不变量守卫位置三分散（repo 写入口/VM 判重/加载清洗——判重倒挂在调用方）等 P2，全部小切口可修。



### 数据正确性

| # | 问题 | 位置 | 证据要点 |
|---|---|---|---|
| D-1 | **买齐心愿单品后重启，心愿穿搭被静默删除**（预览图孤儿化） | `WardrobeRepositoryImpl.kt:378` | `purchaseWishItem` 把 wishItemIds 清空移入 itemIds（:257-259），但 `cleaned()` 以 `wishItemIds.isNotEmpty()` 为保留条件——重启 onLoad 即过滤掉该实体。Mock 仓库无 cleaned 行为相反，演示模式掩盖此 bug |
| D-2 | **主+bak 双损坏时静默返回 default，下一次写消灭最后恢复线索** | `libs/store/SnapshotStore.kt:44,57-61` | `load()` 双失败返回 default 无任何信号；首次 commit 先把损坏 file copy 进 bak 再 rename 覆盖，全程无提示 → 全量无声清零 |
| D-3 | **导入预检读图片无异常防护，损坏 zip 可崩溃 App** | `WardrobePackages.kt:99-102` + `RecapViewModel.kt:207-210` | `pkg.imageBytes(name)` 的 ZipException 未捕获，`startImport` 的 launch 无兜底（对照 applyImport:238 有 catch(Throwable)） |
| D-4 | **放弃编辑泄漏图片文件，且全仓库无孤儿文件 GC** | `ItemEditScreen.kt`（无 DisposableEffect 清理） | 选照片即落盘 + 自动抠图再落一个候选；点关闭未保存 → 1-2 个 webp 永久残留。`referencedImages()` 注释自称「回收判定共用」但无任何回收器实现 |

### AI 链路韧性（it-077~079 hotfix 的结构性根因区）

| # | 问题 | 位置 | 证据要点 |
|---|---|---|---|
| A-1 | **参考图装配静默降级**——it-079 同类根因仍有残留 | `OutfitImageGenerator.kt:201-216` | 合成失败/解码 null 三条路径全落 `emptyList()` 不报错，随后照发**无 image 字段**的纯文生图请求；用户按图文编辑价付费却拿到无参考图结果，UI 无提示 |
| A-2 | **aitryon-plus 可被选中但永远无法生成** | `ModelCatalog.kt:253-266` + `SettingsScreen.kt:476` / `OutfitGenerateSheet.kt:235` + `OutfitImageGenerator.kt:84-93` | 目录带 IMAGE_GEN capability 进下拉（两处下拉均无 imageProtocol 过滤），但 app 只实例化 OkHttpImageModel——选中即每次必败，违反 it-077 自己的「UI 二期开放」拍板 |
| A-3 | **聊天轨取消不中断 HTTP**——it-077 的取消补丁只修了生图两适配器 | `libs/agent/OkHttpChatModel.kt:43-84` | `call.execute()` 阻塞式，取消仅在 SSE 逐行循环的 `ensureActive()` 被观察；连接/DNS/响应头阶段（OkHttp 默认 10s×2）退出页面后请求仍在后台跑完并计费。生图轨 `Call.await()` 桥接模式（`OkHttpImageModel.kt:150-165`）未回移 |
| A-4 | **SSE 中途「干净断连」被装配成正常完成** | `libs/agent/internal/Sse.kt:79-89` | 已收到部分 TextDelta 后对端正常关闭（无 IOException）→ `Completed(半截文本, finishReason=null)` 落盘为最终回答，无错误无重试提示 |

### UI 正确性与构建门禁

| # | 问题 | 位置 | 证据要点 |
|---|---|---|---|
| U-1 | **组合期执行导航副作用** | `ItemDetailScreen.kt:94-97` / `OutfitDetailScreen.kt:100-103` | `if (item == null) { onBack(); return }` 在组合期间调 popBackStack，属未定义行为模式（数据变更致 null 的那一帧可能连 pop 多级） |
| U-2 | **表单状态无进程重建保护** | `ItemEditScreen.kt:100-113`、`WishlistScreen.kt:629-637` | 全部 `remember { mutableStateOf }`；深色切换/字号变更/进程回收重建即清零，且 importedFile 丢引用后已落盘临时图成孤儿（联动 D-4）。对照 W3/W8 筛选已正确 rememberSaveable |
| U-3 | **整库流重组合放大**：item 级 collect + 实体无稳定性标注 | `ItemDetailScreen.kt:476`（OutfitThumb 逐卡）、`RecordsScreen.kt:402`；`Entities.kt` 全部实体无 `@Immutable` | 任意一次写（加评论/打卡）→ WardrobeData 整体替换 → 每个可见缩略卡全部重组；数百穿搭时是最现实的滚动/写并发卡顿源 |
| B-1 | **libs/agent 的 94 个测试不在 CI** | `.github/workflows/ci.yml` | CI 列了 store/sync/cutout/carddeck，唯独漏掉 wardrobe 的网络协议核心（SSE 解析/wire 隔离都靠它的测试锁契约）；LiveImageSmokeTest 已有 SF_KEY 门控，加入无打真 API 风险 |

## 4. P2 问题按主题归组（36 项，细节见分报告）

### 主题一：静默降级族（最突出的系统性风险，与 it-078/079 hotfix 同形态）

「失败被吞掉、链路继续跑」在本库出现 ≥6 次：A-13 高级参数非法值静默丢参（必填参数缺失变服务端 400 才暴露）、A-16(2) finish_reason=stop 下 toolCalls 被静默丢弃、D-8 MERGE 导入的文件回收是死代码、U-5 onDone(Boolean) 契约异常路径三种实现不一致（5 个函数只有 3 个完备）、A-11 maxSteps 熔断收尾提示以 user 角色落盘、A-15 MockChatCache 版本号未随语料 bump。**建议立一条代码规约：任何「runCatching/getOrNull/空列表兜底」必须要么转为显式错误、要么注释声明可接受的理由。**

### 主题二：跨会话/跨重启状态边界（单测盲区）

D-1（重启丢实体）、D-2（双损坏清零）、D-4（孤儿文件累积）、A-9（会话 JSON 损坏按空会话处理、下一次 append 直接覆写原文件=损坏被「治愈」成数据丢失；regenerate 的 clear+逐条重放窗口可丢整会话）、A-10（ChatSessionIndex 无 remove、index 写失败回退非原子）、D-9（数据包 apply 的读-改-写不在临界区，导入期间用户写会被覆盖）。修复共同模式：**损坏文件先改名隔离（.corrupt）再重建 + 关键状态切换走「写新成功后原子替换」**。

### 主题三：取消与生命周期

A-3（聊天取消不传播）、A-12（ChatViewModel.open 切会话不打断运行中 loop，流式文本串台渲染进新会话）、U-1（组合期导航）、U-11（FullscreenSheet 生成中吞返回键无提示）。

### 主题四：重组性能

U-3（见 P1）、U-6（ChatScreen 每 token 屏幕级重组 + 全文重跑正则解析，streaming 读取未下沉独立组合）、U-7（SettingsScreen 单个 @Composable 690 行零子组件，任一 StateFlow 变化整函数重执行）、U-12（主线程整包拷贝 zip 卡启动）、D-10（三处主线程 IO）、A-8（OkHttpClient 每次调用新建）、A-14（参考长图双重 JPEG 编解码，峰值约 25-30MB 瞬时，现代设备安全但无守护）。

### 主题五：结构债与规范漂移

U-4（AppViewModel 67 公开成员滑向上帝对象 + public `val repo` 越层口子 + it-061 后死 API）、D-5（「单线程 Dispatcher」名不符实，实为 Mutex；ensureDefaultPerson 等 4 处锁外 check-then-act 可并发产出两个「我」）、D-6（文件删除失败静默无日志——但「先 JSON 后文件」的全局顺序约定正确，无悬空窗口）、U-15（spec 漂移两处：SettingsScreen→DemoMode 直调未登记、W6 已改 FullscreenSheet spec 未回填）、B-3（catalog 记 store 0.1.0 实际 0.2.0）、B-11（catalog 注释「六个构建」实际七个）。

### 主题六：测试与门禁缺口

B-5（**OutfitImageGenerator 230 行零测试——恰是 it-077~079 三连 hotfix 主战场**，buildRefs/参数编码是纯决策逻辑可抽纯函数补测）、B-6（PickRandomOutfit 是五 usecase 唯一没测的，Random 已注入白捡）、B-2（CI 的「sync 单测」是无声空转，src 无 test 目录）、B-7（release lint 全失守：checkReleaseBuilds=false + CI 无 lint，建议 CI 跑 lintDebug 解耦「工具崩溃」与「无门禁」）、B-4（ADR-027 配置期 git 调用与 configuration cache 不兼容未记载、CI 浅克隆下 rev-list 恒 1 的埋雷）、零测试清单还有 JpegXmp（纯 JVM 白捡）、WardrobePackages（仅一处 android.util.Log 微调即可 JVM 测）、ImageFileStore/KeystoreApiKeyStore（需 Robolectric/仪器）。

### 其余散点（P2/P3）

D-7（SnapshotStore 无 fsync、bak 用 copy 非 rename）、D-11（导入图片无尺寸上限可 OOM）、D-12（Keystore 首次生成并发覆盖，影响轻）、A-5（零重试、429 的 Retry-After 无人消费）、A-6（流式 usage 系统性偏低，未发 stream_options）、A-16(1)（SSE 多行 data 拼接不符规范）、U-8（30 处硬编码色值游离 token 外，DangerTint 深色未适配）、U-9（8 处 LaunchedEffect 做业务写，翻页即写 DataStore 无去抖）、U-10/U-13/U-14/U-16（路由无类型安全、ChatViewModel 单例语义、懒列表 key 缺口 3 处）、B-8（R8 空规则可行，但 onnxruntime AAR 无 consumer rules 是 watch item）、B-9/B-10（tools zip 双份入库、reports 截图无上限）。

## 5. 优化路线图建议

> 迭代编号以提交时 `git log` 实际占用为准（LESSONS：编号提交前 re-check）。**it-080 已被并行会话占用（体验包内置 Key，d887dc3），本路线图顺延为 it-081 起。**

### 第一梯队：小切口速修（合并一个 hotfix 迭代，≤半天）

1. **B-1**：ci.yml 加一行 `wardrobe/gradlew -p libs/agent test --no-daemon`（一行堵最大门禁缺口）
2. **A-2**：两处模型下拉过滤 `imageProtocol != DASHSCOPE_ASYNC_TASK`（消灭「可选必败」）
3. **D-1**：cleaned 保留条件改 `wishItemIds.isNotEmpty() || itemIds.isNotEmpty()` + 补「购入→重载→promote 仍可用」回归测试（完整状态机方案见 [oo-modeling.md §2.2](oo-modeling.md)，deleteWishItem 级联语义统一可留 it-081）
4. **D-3**：precheck 的 imageBytes 外包 runCatching 计入 reasons
5. **U-1**：两处 `onBack()` 移入 `LaunchedEffect`
6. **A-7**：生图适配器 catch 改 `Exception`（Cancellation 除外），防运行时异常击穿终态契约致崩溃
7. **U-15/B-3/B-11**：spec 与 catalog 注释回填（铁律：spec 与代码不一致视为迭代未完成）

### 第二梯队：it-081「AI 链路韧性收口」（建议 1 个迭代）

- **A-1**：`run()` 入口硬校验「模型吃参考图（inputLimit>0）而 refs 为空 ⇒ 显式失败」——把 it-079 修的表象升级为不变量，终结静默降级族在生图轨的根因
- **A-3**：`Call.await()` 桥接下沉聊天轨（或抽 SDK 内共享 internal；顺带解决 O-4 传输内核双份复制）
- **A-4**：SSE `completed()` 对 `sawDelta && finishReason==null && 未收 [DONE]` 给可恢复错误
- **A-13**：参数非法值回退 spec.default 并提示，不静默丢
- **A-12**：`open()` 先 stop()
- **A-9/A-10**：会话与 index 损坏隔离 + index 移出会话目录
- **O-1**：agent SDK 收敛 ProviderSpec 单事实源（先 assert 两清单一致 + 契约测试，再择机收窄构造参数）
- **O-2**：SDK 提供 `ImageModel.of(spec,…)` 协议路由工厂，连带修 A-2 的 UI 过滤
- **B-5**：OutfitImageGenerator 的 buildRefs/参数决策抽纯函数补测（hotfix 三连地区上护栏）+ JpegXmp/PickRandomOutfit 白捡测试

### 第三梯队：it-082「数据边界加固」（建议 1 个迭代）

- **D-1＋WishOutfit 状态机修复**（按 oo-modeling.md §2.2 方案：cleaned 条件改 `wishItemIds.isNotEmpty() || itemIds.isNotEmpty()`，且 deleteWishItem 级联与 purchase 统一为「移除引用不删实体」；Mock/Impl 一致性测试补「写后模拟 reload」用例形态）
- **D-2**：load 双失败隔离损坏文件 + 暴露 loadFailed 信号，禁止空快照直接覆盖
- **D-4**：ItemEditScreen DisposableEffect 清理 + 启动后 images/ 对账 GC（个人级毫秒级）
- **D-5**：4 处锁外检查挪进 transform；同步修正 ADR-002 措辞
- **D-9/D-11**：apply 全程持锁或快照基线比对；导入按 bounds 拒绝超阈值图
- **A-9**：FileSessionStore regenerate 改「写新成功后原子替换」
- **建模 P2 包**（oo-modeling.md §4）：ID 值类型化（至少先 WishSlotId 替代 `wish:` 前缀）、OutfitImage.source 改 enum、判重并入 repo 写入口、spec 03 补「不变量守卫位置规约」（不变量只在写入口强制、cleaned 只兜外部导入数据）

### 第四梯队：it-083「UI 正确性与性能」（可拆两半，与功能迭代并行）

- **U-2**：表单态 rememberSaveable 化（文本/枚举/已导入文件名）
- **U-3**：实体加 `@Immutable`（或 ImmutableList）+ OutfitThumb 改传预派生 items
- **U-6**：StreamingBubble 下沉 + delta 节流 30-50ms
- **U-5**：launchSafely 加 onError 参数，表单函数统一两路回调
- **U-7/U-4**（可延后）：SettingsScreen 按 卡片拆 4-5 个私有 composable；wishlist 域照 RecapViewModel 先例拆 WishlistViewModel、repo 改 private、删死 API

### 明确不建议做的

- **不拆 Gradle 模块**：18.9k 行中 14.7k 是 ui，领域复杂度仅 3k 且边界已被测试固化；拆模块的主要收益（强制边界）已由纪律+单测兑现，无构建耗时痛点。若未来 ui 翻倍或引入协作者，第一刀是 `:feature:*` + convention plugins（7 处手写 compileOptions 已是信号），而非按 layer 拆。
- **不引入 Room/数据库**：ADR-002 对个人级规模的判断依然正确。
- **不上重试框架/熔断**：自用 app 手动重试 + A-5 的 Retry-After 展示已够，别为不存在的规模买复杂度。

## 6. 亮点（保持项）

1. **「先 JSON 提交、后删物理文件」的全局顺序约定**（9 个删文件点零例外）——把最危险的悬空引用形态消灭在设计层面。
2. **libs/agent 数据驱动抽象真兑现**：扩 5 家厂商 SDK 零改动，厂商差异全落数据位；ImageModel 三协议对上层同构终态语义，异步任务的 deadline 文案明示计费边界。
3. **Key 安全红线执行彻底**：AndroidKeyStore+AES-GCM、全链路 mask、缓存用 SHA-256 摘要、payload 不上 wire 不进数据包——ADR-024/026/029/030 与代码逐条一致。
4. **测试选点策略成熟**：全打纯逻辑层（不变量/解析器/Mock↔Impl 一致性/旧 JSON 兼容），零「为覆盖率而测」；libs/agent 94 测含实调换来的 quirk 断言。
5. **ADR 记录质量罕见地高**：连 versionCode 拒绝覆盖装、x86 CI 跑不了 profile 这类负效应都有后果与接受理由。
6. **导入管线「重转码+新 UUID+引用重映射」**：一个设计同时解决格式归一/文件名冲突/路径注入三个问题；zip-slip 审查确认无此漏洞。
7. **it-071 重组性能专项成体系**：单容器懒网格、派生列表 remember 化、状态读取下沉叶组合——本轮 U-3 是它未覆盖的残余而非回退。

## 7. 规模数据速览

| 维度 | 数值 |
|---|---|
| 主源码 | 89 文件 / 18,917 行（ui 78% / data 11.7% / domain 4.4%） |
| AppViewModel | 560 行 / 67 公开成员 / 被 13 个 UI 文件持有 |
| 写路径 | JSON 27 个（全经 mutate{}，无旁路）；图片 delete 18 处 |
| 测试 | wardrobe 112 @Test / ~346 断言 + store 24 + agent 94（**不在 CI**）+ cutout 15 + carddeck 0 |
| 网络端点 | 8 聊天厂商 + 生图三协议 + 预签名 URL 下载，全 BYOK 直连 |
| 迭代节奏 | 2026-09-19 → 10-01，it-001→079（约 6 迭代/天），it-077 单迭代 15 修订 + 2 hotfix |
