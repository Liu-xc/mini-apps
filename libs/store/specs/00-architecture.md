# 00 · store 架构设计（本地存储 SDK）

- **状态**：已实现 v0.2.0（2026-09-20 落地 0.1.0 并接入 wardrobe 与 eats；2026-09-21 增 PackageCodec 数据包编解码 + `SnapshotStore.upgrade`，单测全绿——用例数以 CI/测试套件为准，不在此钉死数字）。与 v1 设计稿的偏差见 §「实现状态」
- **参考实现**：wardrobe it-001 数据层（JsonFileStore / ImageFileStore / SSOT 快照 / zip 导出，15 个 JVM 单测已验证）——本 SDK 是它的通用化抽取，不是全新设计

## 0. 实现状态（v0.2.0）

**精简记录（零消费方/投机性 API，按需再加）**：`MediaStore.sweep()/list()`、`loadDetailed()/LoadOutcome`（恢复来源枚举）、`decode()`（外部字节解码）保留的精简先例。BackupCodec（zip 备份编解码）曾于 0.1.0 被精简，**0.2.0 已按需回归为 `PackageCodec`**（消费方：wardrobe it-024 / eats it-012 数据包，格式见 §3.4）。

**与 v1 设计稿的其他偏差**：

- **单模块落地**（未拆 core/android）：`MediaStore` 接口与 `FileMediaStore` 都在 core；图片编解码归 app 注入（wardrobe 的 ImageFileStore 负责 WebP/EXIF），因此不需要 android 模块。
- API 定名：`Migration(fromVersion){transform}` 迁移链；`LoadOutcome.Loaded(source, migratedFrom)/DefaultUsed`；组合缝命名 `writeHook`。
- `MediaStore.file()` 返回文件句柄不校验存在性（读取用 `read()`）；`put(bytes, ext, preferredName)`。
- `load()` 为同步读（rename 原子性保证与并发 commit 安全），`commit()` 为 suspend + Mutex 串行。

## 1. 定位与目标

各 app 本地持久化的公共底座。与 `libs/sync` 的分工：

```
domain（各 app 实体与不变量）
   ↕  Repository = SSOT 快照 + StateFlow（本 SDK 提供）
libs/store —— 本地半边：原子快照持久化 + 媒体文件 + 备份     ⇄  app 层组合  ⇄  libs/sync —— 云端半边：引擎 + SyncSource 适配器
```

| 目标 | 含义 |
|---|---|
| 零业务概念 | 只认识「快照根类型 + 媒体文件」，不知道衣物/餐厅/剪贴板 |
| 快照式而非数据库式 | 个人级数据规模（≤ 数千实体）用整文件快照：零 schema 迁移成本、备份直观、调试可读 |
| 纯 Kotlin 核心 | 快照/迁移逻辑 JVM 可测；图片压缩等平台能力注入 |
| 行为兼容 | wardrobe 现有文件布局与行为不变，替换为 SDK 是纯抽取重构 |

**非目标**：UI 偏好存储（DataStore<Preferences> 归各 app）、SQL/Room 引擎、整盘加密、多进程并发。

## 2. 模块划分

```
libs/store/
├─ core/     SnapshotStore · Migration 链 · SsotRepository · BackupCodec · MediaStore 接口（纯 Kotlin）
├─ android/  WebPMediaStore（压缩参数注入）、加密落盘可选项
└─ specs/    本设计 + 快照文件格式契约
```

依赖铁律：`android → core`；core 不 import Android/app 类型；**store 与 sync 互不依赖**（组合发生在 app 数据层）。

## 3. 核心 API

### 3.1 快照存储

```kotlin
class SnapshotStore<T : Any>(
    private val file: Path,                    // files/<app>.json
    private val serializer: KSerializer<T>,
    private val migrations: List<Migration<T>>, // 逐版本链
    private val io: CoroutineDispatcher,        // 单写 dispatcher（串行化）
) {
    val flow: StateFlow<T?>                     // 损坏且无 bak 时为 null → App 引导重建
    suspend fun load(): LoadResult<T>           // json → tmp 残片 → bak 三级恢复 + 迁移链
    suspend fun commit(next: T): CommitResult   // 原子写（tmp→rename）+ 成功后滚动 .bak
}
```

- 文件头 `schemaVersion` 由 SDK 统一读写；迁移链 `Migration<T>(fromVersion)` 逐级执行，迁移完成后立即原子落盘一次（迁移不跨进程停留）。
- 崩溃安全矩阵（测试逐项覆盖）：写一半崩溃（tmp 残片，主文件完好）、主文件损坏（bak 恢复 + 残片进隔离目录）、两者皆坏（null 引导）。

### 3.2 SSOT 仓库基类

```kotlin
abstract class SsotRepository<T : Any>(private val store: SnapshotStore<T>) {
    val data: StateFlow<T>
    protected suspend fun mutate(description: String, transform: (T) -> T): T
    // 流程：改快照 → commit → 成功广播 / 失败回滚内存并抛出（wardrobe ADR-008 语义原样保留）
    // writeHook: commit 成功后的扩展点 —— sync 队列登记在这里（app 组合时接上）
}
```

`mutate` 的 `writeHook` 是与 sync 组合的唯一缝：**本地落盘成功 → 登记待推 op**，两动作的顺序保证「先本地后云端」的轻同步语义。

### 3.3 媒体存储

```kotlin
interface MediaStore {
    suspend fun import(source: MediaSource): MediaRef      // 压缩(参数注入)→ uuid.webp 落盘
    suspend fun bytes(ref: MediaRef): ByteArray?
    suspend fun delete(ref: MediaRef)
    suspend fun sweep(validRefs: Set<MediaRef>)             // 孤儿清理（快照加载后对账）
    // 云端时代语义：本地=缓存。ref 同时携带云端 SyncValue.Attachment.ref；
    // 缓存命中直接读盘，未命中 → 经 sync fetchAttachment 下载后写盘（App 桥接层职责）
}

data class MediaRef(val localName: String, val cloudRef: String? = null, val lastUseAt: Long)
// LRU 淘汰：App 桥接层定期按 lastUseAt 清理 cloudRef 非空的缓存（正本在云）
```

### 3.4 数据包编解码（0.2.0，it-024/it-012；v1 设计稿 BackupCodec 的按需回归）

```kotlin
class PackageCodec<T : Any>(
    appId: String,                 // "wardrobe" / "eats"
    dataFileName: String,          // "<app>.json"
    serializer: KSerializer<T>,
    expectedSchemaVersion: Int,
) {
    fun read(file: File): RawPackage<T>          // 结构校验后返回（manifest/数据/图片名清单）
    suspend fun write(out, data, exportedAt, generator, counts,
                      imageNames, readImage, onProgress)
}
class RawPackage<T> : AutoCloseable {           // 持 ZipFile，图片按名惰性读
    val manifest: PackageManifest
    val data: T                                  // 已反序列化
    val imageNames: Set<String>
    fun hasImage(name) / fun imageBytes(name)
}
```

校验顺序：zip 可解 → manifest 合法 → app 归属（WrongApp）→ packageFormat（UnsupportedFormat）→
schema 不高于当前（NewerSchema，低版本经 `SnapshotStore.upgrade` 走迁移链）→ 数据反序列化（BadData）。
按 id 合并、实体级校验**不在 SDK**（零业务概念铁律），归各 app domain 纯函数
（`mergeWardrobe` / `mergeEats`）。

## 4. 文件布局约定（与 wardrobe it-001 一致）

```
files/<app>.json          快照（含 schemaVersion 头）
files/<app>.json.bak      上一成功版本
files/tmp/…               原子写暂存（崩溃残留→启动隔离）
files/media/*.webp        媒体（uuid 命名）
```

## 5. 测试策略

- JVM 单测：三级恢复矩阵、迁移链（跨多版本、迁移抛错不留半态）、commit 串行化、mutate 回滚、sweep 孤儿、zip 往返。
- 抽取自 wardrobe 已验证行为：it-001 的 15 个数据层测试随迁为 SDK 回归测试的基底。

## 6. 各 app 接入路径

- **wardrobe（it-002 首个消费方）**：JsonFileStore/ImageFileStore 替换为 store SDK，文件布局与备份格式不变（纯重构，测试护栏下进行）；sync 的 `writeHook` 接入待推队列。
- **eats**：增量替换（其现有 JSON 存储结构对齐后）。
- **clips Android**：直接用；**Mac 端不依赖本 SDK**，按 clips.json 文件格式契约独立实现（zip 双端可互换）。

## 7. 演进候选

媒体云端缓存管理器（LRU 引擎内建）、快照字段级 diff（未来增量备份）、多套快照槽（多源只读衣橱，it-003+）。
