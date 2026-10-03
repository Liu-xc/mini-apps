# 分报告一：数据层 + 领域层深度审查

> 审查通道 A · 全部结论基于逐文件精读，P1 级发现经主审复核源码确认。

## A. 事实基线

### 实际分层与关键文件行数

| 层 | 文件 | 行数 | 职责 |
|---|---|---|---|
| libs/store | SnapshotStore.kt | 83 | tmp→rename 原子写、.bak、三级恢复、迁移链 |
| libs/store | SsotRepository.kt | 39 | 内存快照 + StateFlow + **Mutex** 串行 mutate |
| libs/store | MediaStore.kt | 51 | 文件管理（uuid 命名/删） |
| libs/store | PackageCodec.kt | 172 | zip 结构 + manifest 契约（零业务概念） |
| domain/model | Entities/Queries/DataPackageMerge/WardrobeCategory | 179/72/99/20 | 实体、纯查询、合并纯函数 |
| domain/repository | 8 个接口 | 12–37 | 按聚合域拆分（it-021） |
| domain/usecase | 5 个 | 11–140 | 全部纯函数 |
| data/repo | WardrobeRepositoryImpl.kt | 387 | 27 个写点 + cleaned() |
| data/image | ImageFileStore.kt / ImageEditStore.kt | 200/24 | 解码/EXIF/1440px/webp82/抠图 |
| data/packages | WardrobePackages.kt | 182 | 导出/预检/导入三段式 |
| data/prefs | Prefs/Recap/Keystore | 184/49/78 | DataStore ×2、AES-GCM+AndroidKeyStore |

**与 spec 的偏差点**（按仓库铁律「spec 与代码不一致视为迭代未完成」）：
1. **ADR-002 声称「并发写由单线程 Dispatcher 串行化」——不实**。实际是 `SsotRepository.kt:25` 的 `Mutex` + `SnapshotStore.kt:54` 的 `withContext(Dispatchers.IO)`。互斥本身成立，但机制与 spec 描述不符，且锁外预读不受保护（见 D-5）。
2. spec 03「升级时运行迁移函数」：机制已实现且有测试，但 `AppContainer.kt:30-36` 装配时 `migrations` 为空、schemaVersion 恒为 1——迁移链处于休眠，尚未被真实使用过。
3. spec 03 不变量「wishItemIds 至少一件」与 purchase→promote 的中间态自相冲突（见 D-1）。
4. 04-architecture「落盘失败回滚」：对内存快照成立（不赋值天然回滚），仅此而已——锁外 TOCTOU、文件删除失败均不在保护范围（如实陈述于 D-5/D-6）。

## B. 问题清单（P0: 0 / P1: 4 / P2: 8）

### D-1 [P1] cleaned() 静默删除「已购齐待升级」的心愿穿搭，预览图孤儿化
**位置**：`WardrobeRepositoryImpl.kt:375-378`
```kotlin
val validWishOutfits = wishOutfits
    .filter { it.personId in personIds }
    .map { w -> w.copy(itemIds = ..., wishItemIds = w.wishItemIds.filter { ... } ) }
    .filter { it.wishItemIds.isNotEmpty() }   // ← 问题行
```
**证据链**：`purchaseWishItem`（:257-259）把愿望件移出 wishItemIds、移入 itemIds——`WishRepositoryTest.kt:107` 明确断言购入后 `outfit.wishItemIds.isEmpty()` 并依赖**同一会话内**立即 promote（:109）。但 cleaned() 只在加载（`onLoad = { it.cleaned() }`）与 `replaceAll` 时执行。
**触发路径**：① 买齐全部愿望件 → 重启进程 → onLoad cleaned() 过滤掉该 wishOutfit（previewImages 引用随之消失，AI 试穿预览文件永久孤儿化）；② 买齐后执行任意数据包导入 → `replaceAll(merged)` 内的 cleaned() 同样删除它。
**影响**：心愿穿搭实体丢失（previewImages 不可恢复）；Mock 仓库（无 cleaned、进程内常驻）行为与真实实现相反，演示模式掩盖此 bug。
**修复**：过滤条件改为 `wishItemIds.isNotEmpty() || itemIds.isNotEmpty()`（或引入显式 `allPurchased` 状态字段）；补「购入→重载→promote 仍可用」回归测试。

### D-2 [P1] 主+bak 双损坏时静默返回 default，下一次写覆盖双副本 → 全量无声清零
**位置**：`SnapshotStore.kt:44` + `:57-61`
```kotlin
fun load(): T = read(file) ?: read(bakFile) ?: default()   // 无任何失败信号
...
tmpFile.writeText(payload)
if (file.exists()) file.copyTo(bakFile, overwrite = true)  // 损坏的 file 被复制进 bak
if (!tmpFile.renameTo(file)) throw IOException(...)
```
**证据**：`SnapshotStoreTest.kt:53-59`（bothCorruptedReturnsDefault）证实行为。`SsotRepository.kt:22` 构造即 load，随后首个 mutate 就把 default 写入 file、把损坏 file 复制进 bak——用户最后一线恢复机会被消灭，全程无提示。
**影响**：极端场景（双文件损坏/断电+D-7 叠加）下全量数据丢失且不可知。
**修复**：load 双失败时把损坏文件改名隔离（`.corrupt-<ts>`）并暴露 `loadFailed` 信号，App 层提示用户先导出残包/求助，禁止以空快照直接覆盖。

### D-3 [P1] precheck 读包内图片无异常防护，损坏包可崩溃 App
**位置**：`WardrobePackages.kt:99-102` + `RecapViewModel.kt:207-210`
```kotlin
referenced.forEach { name ->
    val bytes = pkg.imageBytes(name)   // ← CRC 损坏抛 ZipException/IOException，未捕获
```
`RecapViewModel.startImport` 的 `viewModelScope.launch { when (val r = ...precheck(file)) }` 无 try/catch、无 CoroutineExceptionHandler（对照 `applyImport`:238 有 `catch (t: Throwable)`）。`codec.read` 对 manifest/数据条目均有兜底，唯独图片条目循环裸奔——中央目录完好但条目数据损坏的 zip（传输损坏、恶意构造）直接崩溃。
**修复**：`imageBytes` 外包 runCatching，异常计入 reasons（「N 张图片无法读取」）。

### D-4 [P1] 放弃编辑泄漏图片文件，且全仓库无孤儿文件 GC
**位置**：`ItemEditScreen.kt`（照片只在 替换 :122/:136-138、还原 ：148、保存 ：172 三类事件清理，**无 DisposableEffect**）；顶栏关闭 ：184 直接 `onBack()`。
**证据**：选照片即落盘（AppViewModel importPhoto，防 URI 授权失效的设计）+ 自动抠图再落一个候选文件；用户点关闭未保存 → 1-2 个 webp（各 ~100-300KB）永久残留。其它孤儿源：`WardrobePackages.apply` 中途失败的新 uuid 图（:119 注释自认）、`OutfitImageGenerator.kt:162` createOutfit 失败 runCatching 吞掉已生成图、`:147/:151` images.delete 失败静默。`referencedImages()` 的注释（DataPackageMerge.kt:83）声称「回收判定共用」，但**不存在任何回收器实现**。
**修复**：ItemEditScreen 加 DisposableEffect 清理未保存文件；设置页或启动后对账 `images/ 目录 vs referencedImages()` 做一次 GC（个人级规模，毫秒级完成）。

### D-5 [P2] 「单线程 Dispatcher」名不符实 + 锁外 check-then-act 竞态
**位置**：`SsotRepository.kt:29-35`（Mutex 临界区仅覆盖 transform+commit+赋值）；`WardrobeRepositoryImpl.kt:41-46`
```kotlin
override suspend fun ensureDefaultPerson(): Person {
    data.value.persons.firstOrNull()?.let { return it }   // 锁外预读
    val p = Person(newId(), "我", ...)
    mutate { it.copy(persons = it.persons + p) }          // 并发双调 → 两个「我」
```
AppViewModel init（:110）与用户动作（:256/:437 的 `currentPerson.value ?: ensureDefaultPerson()`）可并发。同族 TOCTOU：addWearLog（:203）、purchaseWishItem（:242）、promoteWishOutfit（:323）的存在性检查均在锁外（后两者仅产生窗口极窄的悬空，cleaned() 下次加载兜底）。**修复**：检查挪进 transform 以最新快照判定；同步修正 ADR-002 措辞。

### D-6 [P2] 文件删除在互斥区外且失败被静默吞掉（方向正确、无兜底）
**位置**：`WardrobeRepositoryImpl.kt:70,80,116,141,175,197,238,302,319`（9 处 mutate 后删文件）；`MediaStore.kt:46-48` `File(dir, name).delete()` 返回值被丢弃。
**结论性回答审查点**：本实现全程「**先 JSON 落盘、后删文件**」，不存在「文件删了 JSON 落盘失败」的悬空窗口——JSON 失败时文件未删（孤儿、可重试），这是正确的设计。剩余问题只是删除失败无日志、无对账。修复：delete 返回 false 时打日志；配合 D-4 的 GC。

### D-7 [P2] SnapshotStore 无 fsync；bak 用 copy 而非 rename
**位置**：`SnapshotStore.kt:57-61`。断电时 rename 可先于数据落盘（ext4 延迟分配）→ file 截断，bak 可救；但**首次提交**路径 bak 复制自刚 rename 的 file（:61），双损触发 D-2。`copyTo(bakFile)`（:58）非原子，中途崩溃产生半写 bak（主文件完好时无害）。修复：FileOutputStream + fd.sync() 再 rename；bak 改为「旧 file renameTo(bak) + tmp renameTo(file)」双 rename。

### D-8 [P2] MERGE 导入的旧文件回收是死代码
**位置**：`WardrobePackages.kt:145-147`。`mergeWardrobe` 永不删本地实体（DataPackageMerge.kt:46「本地有包无→保留」），故 `keptRefs ⊇ localRefs` 恒成立，`localRefs.filterNot { it in keptRefs }` 恒空。误导维护者以为 MERGE 会清文件。REPLACE 分支（:151）有效。

### D-9 [P2] apply 的读-改-写不在同一临界区；writeHook 在锁外
**位置**：`WardrobePackages.kt:127`（读 `repo.data.value`）→ 图片归一化（分钟级）→ `:150` replaceAll。期间用户任何写会被整体覆盖（丢失更新）；UI Running 遮罩降低但未消除概率。`SsotRepository.kt:36` 的 `writeHook` 也在锁外调用，未来接入 sync 后有登记顺序倒置风险（当前 writeHook 为 null，纯潜在）。

### D-10 [P2] 主线程 IO 三处
`WardrobeApp.onCreate → AppContainer init → SsotRepository 构造 → SnapshotStore.load()` 同步读盘解析 JSON（个人级体量小，但属 StrictMode 违例）；`looksCutout` 同步磁盘解码在 `ItemDetailScreen.kt:112` 组合期主线程执行（≤128px 采样，开销小）；演示模式 `AppContainer.kt:112-114` 主线程解包 assets。

### D-11 [P2] 导入图片无尺寸上限，超大分辨率包可 OOM
`ImageFileStore.kt:121-125` 预检只验「可解」不限 bounds；`:108-118` putPackageImage 全尺寸 decodeByteArray 后才 scaleDown。30000×30000 位图 → 数 GB 内存（OOM 被 RecapViewModel `catch(Throwable)` 兜成 Rejected，但进程已濒危）。修复：precheck 按 bounds 拒绝超阈值图；putPackageImage 用 inSampleSize 预降采样。

### D-12 [P2] KeystoreApiKeyStore 首次生成密钥的并发覆盖
`KeystoreApiKeyStore.kt:56-70`：并发首次调用双双 generateKey，后生成者替换 alias；若 A 用被替换的旧钥加密后才写入，该密文永久不可解（get 返回 null 表现为「未配置」，用户重输即可，影响轻；注释已自知）。AES-GCM 实现本身规范（IV 前置存储、128-bit tag、密钥不可导出）。

### 审查点专项结论（无新编号，归入以上）
- **Zip-slip：无此漏洞**。PackageCodec 不解压到磁盘（内存读）；包内图片名永不成为磁盘路径——apply 统一 `putPackageImage` 生成新 UUID 名（:135 + MediaStore.kt:36-37）再 `remapImageRefs` 重写引用，构造的 `images/../../x` 条目也会被重命名。
- **5 条不变量**：deleteItem（:130-142）/deleteOutfit（:165-176）/deletePerson（:83-117）/cleaned()（:355-387）实现完整（含 wearLogs/wish 域扩展），均有测试；唯一反例即 D-1。
- **零改动承诺**：对 wardrobe.json 成立——precheck 纯只读；apply 的 JSON 唯一写点 replaceAll 走 mutate（失败回滚），SsotRepositoryTest（transform 失败）+ SnapshotStoreTest（commit 失败，用同名 tmp 目录堵写的手法）双保险。对文件系统不成立（失败残留孤儿图，代码注释已声明）。

## C. 亮点

1. **「先 JSON 提交、后删物理文件」的全局顺序约定**（9 个删文件点零例外）——把最危险的悬空引用形态消灭在设计层面，失败只产生可回收的孤儿而非数据损坏。
2. **导入图片「重新转码 + 新 UUID + 引用重映射」管线**：一个设计同时解决格式归一（1440/webp82）、跨包文件名冲突、路径注入三个问题。
3. **「回滚由不赋值天然成立」**：transform 纯函数化使内存回滚零成本，且有失败注入测试双向验证（transform 失败 + commit 失败）。
4. **domain 纯度货真价实**：grep 全量验证零 Android import（仅 kotlin/java.util/java.time/kotlinx.serialization）；Bitmap 经 ImageStore/ImageEditStore 双接口隔离在 data 层，5 个 usecase 全部 JVM 可测。
5. **store SDK 与应用的职责切分干净**：zip 结构/manifest 在 PackageCodec（零业务概念）、合并语义在 domain 纯函数，各自有测试；PackageException 子类化支持应用层转用户语言。
6. **预检-确认-应用三段式导入**与数据承诺自洽（diff 预览、locallyNewer 警示、Rejected 零改动、demo 模式双重拦截）。

## D. 量化

- **行数 top10（全为 UI 层，数据/领域层无一超 400）**：WishlistScreen 1125 / ExportSheet 886 / SettingsScreen 758 / WardrobeRecapScreen 758 / OutfitScreen 699 / ChatScreen 693 / OutfitDetailScreen 674 / ItemEditScreen 584 / OutfitGenerateSheet 577 / ChatResultCards 565。`WardrobeRepositoryImpl` 387 行承载 27 个写点 + cleaned()，职责未过载。
- **写路径**：JSON 写 27 个（全经 `mutate{}`→`SnapshotStore.commit`，**无任何旁路**——grep 验证 app 侧无直接 store.commit/mutate 调用）；图片 put 3 个（importFromUri/cutoutTo/putPackageImage）；图片 delete 调用点 18 处（repo 9 方法 + VM 2 + ItemEditScreen 5 事件 + packages.apply 2 分支）。
- **测试**：wardrobe 20 文件 / 1964 行 / 112 个 @Test / ~348 断言（assertEquals 195、assertTrue 96…）；libs/store 4 文件 / 379 行 / 24 个 @Test。数据层核心覆盖：Impl 14 + Wish 8 + WearLog 6 + Merge 6 + Queries 5 + SnapshotStore 7 + PackageCodec 10 + SsotRepository 4 + FileMediaStore 3 + Mock 一致性 1。
- **零测试关键路径**：WardrobePackages 整条导入集成管线（precheck/apply/零改动/REPLACE 删除）、KeystoreApiKeyStore 加解密往返、PrefsStore/RecapPrefsStore、ImageFileStore（转码/EXIF/透明检测）、全部并发行为（无任何并发测试）、D-1 的跨重启路径。

**总体判断**：架构骨架（SSOT + 原子写 + 级联不变量 + 三段式导入）质量高于典型个人项目，测试对纯函数与不变量覆盖扎实；真正的风险集中在**跨重启/跨会话的状态边界**（D-1/D-2/D-4）与**异常输入的入口防御**（D-3/D-11），这些恰好是单测盲区。
