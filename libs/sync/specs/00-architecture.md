# 00 · sync 架构设计（中立契约 + 后端适配器）

- **状态**：已实现 v0.1.0（2026-09-20，contract + bitable 49 单测全绿；按计划未接入任何应用）。与 v2 设计稿的偏差见 §「实现状态」
- **上游依据**：wardrobe [it-002 调研](../../../wardrobe/specs/iterations/it-002-feishu-bitable-sync.md)

## 0. 实现状态（v0.1.0；2026-09-20 二轮 review 已精简）

**精简记录（零消费方/投机性 API，按需再加）**：`PullCursor` 增量游标（v1 全量对账用不上，`pull(since)` 参数与 `nextCursor` 一并移除）、`ConflictPolicy` 接口与 `SyncPolicy`（唯一策略 LWW 内联引擎）、`SyncState.Working` 的 progress/total 计数、`PendingOpQueue` 接口（单实现，直接用具体类 `FilePendingOpQueue`）、`AttachmentMeta`（附件只留 ref）、`RawRejection`（与 `Rejected` 合并）、`connect` 的 Result 包装（统一为抛出 + state=Failed）。`CollectionAdapter` 的 `localIds+localUpdatedAt` 合并为一次 `localVersions(): Map<String, Long>`。

**与 v2 设计稿的其他偏差**：

- 模块结构：`contract/` + `bitable/` 两个 Gradle 子模块（未设 backends/ 子目录与 android 模块；OkHttp 依赖在 bitable）。
- 草案的 `TableMapping<E>` 落地为 **`CollectionAdapter`**：引擎只说 `SyncEntity`（映射全部在 app 侧），附件经 `attachmentsFor()/onPushed()` 钩子两段式处理（取代 AttachmentSlot 泛型）。
- 状态机：`SyncState.Done(at, pulled, pushed, deleted, rejected)`；`connect` 失败返回 `Result`，`push/pull` 失败抛出并落 `Failed(retryable)`。
- BitableSource 细节：字段类型注册表驱动解码（未知字段折叠 `JsonText` 不炸同步）；`updatedAt = last_modified_time`，兜底实体 `updatedAt` 列；`WriteGate` 全局串行写 + 按 `x-ogw-ratelimit-reset` 退避；`RecordIndex` JSON 文件持久化（entityId↔recordId）；单选列解码为 `Options(单值)`。
- 测试策略落地：FakeTransport 脚本化桩 + 请求体断言（我方请求构造正确性）；真实链路行为属 it-002 真机实测清单范围。

## 1. 定位与目标

把「个人自用安卓 App 的云端轻同步」沉淀为**后端中立**的公共底座：

| 目标 | 含义 |
|---|---|
| **数据源可切换** | 对 app 而言同步后端只是可替换的数据来源；换后端 = 换适配器 + 换配置，app 侧映射/UI/引擎复用 |
| 零业务概念 | 契约层不知道"衣物/穿搭"，只认识"集合/实体/字段/附件" |
| 轻同步语义 | 云端为正本、本地为缓存；拉取对账 + 记录级 LWW；无实时推送 |
| 分发优先 | 凭证即身份；连接配置可二维码分发（payload 带后端标识） |
| 纯 Kotlin 核心 | contract 引擎零 Android 依赖（JVM 可测），平台能力全部注入 |
| 契约可移植 | 同步语义写成语言无关契约，clips Mac 端（Swift）按契约复刻 |

**非目标（v1）**：实时事件订阅、字段级冲突合并、OAuth 用户登录、iOS/Multiplatform 实现。均留扩展点。

## 2. 总体形态：一个契约，多个适配器

```
libs/sync/
├─ contract/                 # 中立契约 + 后端无关引擎（纯 Kotlin，极小依赖面）
│    SyncSource 接口 · SyncEntity/SyncValue · SyncOp/CollectionDelta
│    SyncEngine（队列/对账/LWW/隔离区）· SyncError · 配置与二维码 payload 规范
├─ backends/feishu-bitable/  # 首个适配器：实现 SyncSource
│    内部私有：Transport/TokenManager/BitableClient/WriteGate
│             Bitable 字段 JSON ↔ SyncValue 转换 · entityId↔recordId 索引
├─ android/                  # 平台适配：加密凭证存储、WorkManager 触发、扫码页
└─ specs/                    # 本设计 + 契约文档 + golden fixtures

app 依赖：contract（编译期）+ 选定 backend（运行时注入）
未来：backends/leancloud、backends/webdav…… 各自实现 SyncSource 即可
```

- 接入：app `settings.gradle.kts` `includeBuild("../libs/sync")`；免 Maven 发布，各 app 构建独立。
- **依赖铁律**：`backend → contract`、`android → contract`；contract 不 import 任何后端/Android/app 类型；后端的私有能力（如 Bitable 的 record_id、串行写）**不得泄漏出适配器边界**。

## 3. 契约层（contract）

### 3.1 数据模型（后端中立）

```kotlin
data class SyncCollection(val name: String)          // wardrobe: Persons/Items/Outfits/Notes

data class SyncEntity(
    val id: String,                                   // App 侧 UUID；后端内部自行换算主键
    val fields: Map<String, SyncValue>,
    val updatedAt: Long,                              // 后端权威修改时间（LWW 判据）
)

sealed interface SyncValue {
    data class Text(val value: String?)
    data class Number(val value: Double?)
    data class Bool(val value: Boolean)
    data class Instant(val millis: Long?)
    data class Options(val values: List<String>)      // 单选/多选统一
    data class JsonText(val raw: String)              // 复杂结构（id 数组、嵌套对象）
    data class Attachment(val ref: String, val meta: AttachmentMeta)   // ref 对后端不透明（file_token/文件 URL）
}
```

> SyncValue 是七种中立值的封闭集。任何后端（表格型/文档型/对象型）都能承载它——这是"可切换"的地基。

### 3.2 数据源接口（ports）

```kotlin
interface SyncSource {
    val backendId: String                              // "feishu-bitable" / "leancloud" / ...
    suspend fun connect(config: SyncConfig): ConnectResult    // 测试连通 + 确保集合结构就绪
    suspend fun pull(collection: SyncCollection, since: PullCursor?): PullResult
    suspend fun push(collection: SyncCollection, ops: List<PushOp>): PushResult
    suspend fun putAttachment(name: String, bytes: ByteArray): SyncValue.Attachment
    suspend fun fetchAttachment(ref: String): ByteArray
}

data class SyncConfig(val backend: String, val params: Map<String, String>, val readOnly: Boolean = false)

// 二维码分发 payload（URL scheme 或 JSON）：
// { "v":1, "backend":"feishu-bitable", "ro":false,
//   "params":{ "appId":"cli_xxx", "appSecret":"xxx", "appToken":"BascXXX" } }
```

### 3.3 引擎（后端无关，住在 contract）

```kotlin
sealed interface SyncState {
    data object Disconnected; data object Idle
    data class Working(val phase: Phase, val progress: Int, val total: Int)
    data class Done(val pulled: Int, val pushed: Int, val at: Long)
    data class Failed(val error: SyncError, val retryable: Boolean)
}

class SyncEngine(
    source: SyncSource,                       // 唯一的后端触点（可替换）
    mappings: List<CollectionMapping<*>>,     // App 的实体映射（中立 SyncValue 语法）
    queue: PendingOpQueue,                    // 待推队列（契约提供 JSON 文件默认实现）
    policy: SyncPolicy = SyncPolicy.default(),
) {
    val state: StateFlow<SyncState>
    suspend fun connect(config: SyncConfig): ConnectResult
    suspend fun push(onApplied: suspend (PushOutcome) -> Unit)
    suspend fun pull(onDelta: suspend (CollectionDelta) -> Unit)
}

data class CollectionDelta(
    val collection: String,
    val upserts: List<Any>,                  // 已映射实体（附件仅 ref，字节按需取）
    val deletes: List<String>,               // 云端已消失的实体 id（对账结果）
    val rejected: List<RejectedRecord>,      // 隔离区：映射失败行 + 原因
)
```

**引擎不持有 App 存储**：pull 产出回调、push 消费队列，落地永远在 App 侧——与 wardrobe 的快照 SSOT 零耦合，任何 app 的存储都接得上。

### 3.4 实体映射（App 实现，中立语法）

```kotlin
interface CollectionMapping<E> {
    val collection: String
    suspend fun toFields(entity: E): Map<String, SyncValue>          // 不含附件字节
    suspend fun fromFields(id: String, fields: Map<String, SyncValue>, updatedAt: Long): E?
    val attachments: List<AttachmentSlot<E>> get() = emptyList()
}

class AttachmentSlot<E>(
    val fieldName: String,
    val read: suspend (E) -> ByteArray?,        // null = 无需上传
    val write: suspend (E, SyncValue.Attachment) -> E,
)
```

### 3.5 错误分类（中立，附恢复动作）

```kotlin
sealed interface SyncError {
    data class Network(cause)               // → 重试
    data class RateLimited(resetAfterSec)   // → 退避重试
    data class AuthFailed(detail)           // → 凭证失效，引导重配/重扫码
    data class PermissionDenied(detail)     // → 提示对应平台的授权动作（如飞书挂协作者）
    data class QuotaExceeded(detail)        // → 清理/升级
    data class InvalidRecord(detail)        // → 隔离区
    data class Unknown(http, code, body)
}
// 各后端把自家错误码折叠进以上类别，detail 携带平台化排错文案
```

## 4. feishu-bitable 适配器（backend 实现，全部私有于适配器内）

| 内部组件 | 职责 |
|---|---|
| HttpTransport（接口）+ OkHttpTransport | HTTP 抽象；测试换 FakeTransport |
| TokenManager | `tenant_access_token` 获取/缓存（2h）/Mutex 单飞续期 |
| BitableClient | 8 端点封装：listTables/ensureTable/listRecords/searchRecords/batchCreate/Update/Delete/uploadAttachment |
| WriteGate | 串行写（官方建议单表一次写）+ 429 按 `x-ogw-ratelimit-reset` 指数退避 |
| ValueCodec | Bitable 字段 JSON ↔ SyncValue 双向转换（Options 自动建选项） |
| RecordIndex | entityId ↔ recordId 持久映射；**飞书 UI 手建行（无 id 列）→ 收编 recordId 为实体 id** |
| SchemaEnsurer | 首连自动建表/补列（不删列），itemsIds 等复杂结构走 JsonText |

错误折叠示例：HTTP 403/无协作者 → `PermissionDenied("请到表格 ··· 菜单把应用添加为协作者")`；`1061073` → `PermissionDenied("请到开发者后台发布应用版本使权限生效")`；`1061061` → `QuotaExceeded`；429/`99991400` → `RateLimited`。

## 5. 同步语义（v1 契约，后端通用）

1. **Pull**：v1 全量对账——适配器分页拉全集合（≤数千实体，十几请求），引擎 diff：云端新/改 → upserts；本地多出 → deletes。增量游标（`PullCursor`）接口已备，留 v1.1（Bitable 侧用 filter「最后更新时间」）。
2. **Push**：队列合并（同实体多改只剩末条）→ 串行 batch（默认分片 100）→ 成功 ack / 失败保队列退避。
3. **冲突**：记录级 LWW 整行覆盖；`ConflictPolicy` 接口预留（字段合并/人工）。
4. **删除**：物理删除 + 全量对账传播（无墓碑）。
5. **附件**：上行 `putAttachment` 得 ref 再入字段（ref 已在实体则跳过 = 不重传）；下行只回填 ref，字节 App 侧媒体缓存按需 `fetchAttachment`。
6. **触发**：`SyncTrigger` 抽象（Manual / OnAppStart / WorkManager 周期与约束）；实时订阅留位。

## 6. 可扩展性清单

| 扩展点 | 换什么 | 不动什么 |
|---|---|---|
| **SyncSource（后端本身）** | Bitable → LeanCloud/WebDAV/自建 | 契约类型、引擎、App 映射与 UI |
| HttpTransport（适配器内） | OkHttp → Ktor/缓存 | 适配器其余部分 |
| CredentialProvider | 手填 → 扫码 → OAuth/多源 | 引擎/适配器 |
| RetryPolicy / WriteGate | 退避与并发策略 | 端点封装 |
| ConflictPolicy | LWW → 字段合并/人工 | 引擎骨架 |
| SyncPolicy.pullMode | 全量对账 → 增量游标 | diff 逻辑 |
| SyncTrigger | 手动/冷启/周期 → 事件订阅 | 引擎本体 |
| CollectionMapping / AttachmentSlot | 各 app 实体集 | 引擎与适配器 |

**后端间迁移**成为契约层工具：`迁移器 = pull(后端A) → push(后端B) + 附件逐个搬运`，app 无关。

## 7. 测试策略

- contract 引擎：JVM 单测（入队合并、断点续推、对账 diff、隔离区、崩溃恢复）+ **后端无关的一致性测试套件**（同一套用例跑任何 SyncSource 实现——可移植性即测试）。
- bitable 适配器：FakeTransport + golden fixtures（真实请求/响应 JSON，兼作 Swift 端契约对齐用例）。
- 契约文档（01-contract.md，落地时定稿）：端点清单、SyncValue 映射规范、同步语义、错误折叠表、fixtures 格式。

## 8. 版本与演进

- 语义化版本；`SyncConfig.v` + 集合结构 `schemaVersion` 字段留迁移通道（只加列不删列）。
- 远期：增量拉取 v1.1、只读多源挂载（it-003+）、实时事件、OAuth、更多后端。

## 9. wardrobe 接入路径（it-002 落地时）

1. App 依赖 contract + backends/feishu-bitable；DI 组合根构造：`SyncEngine(BitableSource(...), mappings, queue)`。
2. 实现四个 `CollectionMapping`（Person/Item/Outfit/Note；itemIds/effectImages 走 JsonText；Item.image 为单附件槽）。
3. 设置页：扫码/手填三参数 → connect() 校验 → 同步状态条（SyncState 渲染）→ 二维码生成（从 CredentialProvider 读配置编码）。
4. Repository 织入：写后 enqueue；冷启/手动 push→pull；`CollectionDelta` 应用回快照。
5. 过渡形态：codebase 未拆 includeBuild 前，允许以源码包落在 wardrobe 内，但**包结构必须按 `sync/contract` 与 `sync/backend/bitable` 分明**，拆库纯搬移。
