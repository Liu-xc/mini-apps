# 04 · 技术架构

## 分层与依赖规则

```
┌───────────────────────────────────────────┐
│ ui/        Compose 界面 + ViewModel        │  主要依赖 domain（+ Android/Compose）
├───────────────────────────────────────────┤
│ domain/    实体、Repository 接口、UseCase   │  纯 Kotlin，无 Android 依赖（JVM 可测）
├───────────────────────────────────────────┤
│ data/      Repository 实现、图片编解码、偏好  │  实现 domain 接口；持久化机制由 libs/store 承担
├───────────────────────────────────────────┤
│ export/    合成图/元数据/剪贴板分享（门面）   │  Android 图形与系统交互
├───────────────────────────────────────────┤
│ platform/  WorkManager 提醒等系统调度       │  Android 专用
└───────────────────────────────────────────┘
di/AppContainer.kt = 组合根，装配一切依赖（手动构造器注入）
```

**规则**：依赖只能从上到下；domain 不 import 任何 data/ui/export 类型；UI 永远通过 ViewModel 间接拿数据。
**现状例外**（it-023 记录）：WardrobeScreen 直调 `data.mock.DemoMode`（演示入口显隐）。

## 模块结构

```
com.leo.wardrobe/
├─ WardrobeApp.kt                 # Application：创建 AppContainer
├─ MainActivity.kt                # 单 Activity：NavHost + 底部四 Tab + 主题
├─ di/AppContainer.kt             # 组合根（演示仓库/图片目录切换；内置演示资源按 revision 刷新）
├─ domain/
│  ├─ model/      Person Item Outfit OutfitImage Note WearLog WishItem WishOutfit
│  │              WardrobeCategory TagPresets WardrobeData（Queries.kt 派生查询）
│  ├─ repository/ WardrobeRepository(门面) + Person/Item/Outfit/WearLog/Wish/Note 子接口(it-021)
│  │              ImageStore(接口)
│  └─ usecase/    BuildOutfitPrompt PickRandomOutfit WardrobeRecapCalculator StaleItemSelector(it-021)
├─ data/
│  ├─ repo/WardrobeRepositoryImpl.kt   # 继承 store SDK 的 SsotRepository（SSOT+原子落盘+广播；writeHook 预留同步登记）
│  ├─ image/ImageFileStore.kt          # 实现 ImageStore + ImageEditStore 接口(it-021)；文件管理走 store SDK FileMediaStore
│  ├─ prefs/                           # PrefsStore（组合记忆/文案记忆/真实与 Mock 隔离的 AI 连接偏好）RecapPrefsStore / KeystoreApiKeyStore
│  ├─ chat/                            # ChatSessionIndex（会话摘要目录；消息仍归 agent FileSessionStore）
│  └─ mock/                            # Mock 数据/仓库/DemoMode；MockChatCache + CachedMockChatModel（it-050）
├─ export/
│  ├─ OutfitImageComposer.kt      # Bitmap 拼合成图（2列网格+品类标签）
│  ├─ JpegXmp.kt                  # 成品图 XMP 元数据回写（it-013）
│  └─ ShareClipboard.kt           # 复制文本/图片、ACTION_SEND 分享（门面）
├─ platform/
│  └─ ReminderScheduler.kt        # WorkManager「好久没穿」每日提醒（it-018，ADR-019）
└─ ui/
   ├─ theme/       DesignTokens Typography WardrobeTheme
   ├─ components/  PhotoCard PhotoPicker SlotGrid Tags CommentTimeline EmptyState Confetti…
   ├─ AppViewModel.kt             # 全局：角色状态 + 数据流（SSOT 出口）
   ├─ outfit/      OutfitScreen(W1) PersonSheet(W2) ExportSheet(W6)
   ├─ records/     RecordsScreen(W8) OutfitDetailScreen(W7)
   ├─ wardrobe/    WardrobeScreen(W3) ItemEditScreen(W4)
   ├─ detail/      ItemDetailScreen(W5)
   ├─ recap/       WardrobeRecapScreen RecapViewModel WardrobeRecapLongImage(W9，it-018/021)
   ├─ wishlist/    WishlistScreen(W10，it-019)
   ├─ settings/    SettingsScreen(W11) SettingsViewModel(it-041 阶段A，模型连接域)
   └─ chat/        ChatListScreen(W12) ChatScreen(W13) ChatViewModel WardrobeTools（多会话 + 只读门禁）
```

## 设计模式

| 模式 | 用在哪 |
|---|---|
| Repository | `domain.repository` 定接口、`data.repo` 实现；UI 不感知持久化 |
| SSOT 单一数据源 | 基类 `SsotRepository`（libs/store）：内存快照 + `StateFlow`；写操作「改快照→原子落盘→广播」，落盘失败回滚。三 Tab 与角色过滤全部是流上 `map` |
| MVVM + UDF | **全局 `AppViewModel`**（SSOT 出口）+ 域 ViewModel（it-021：`RecapViewModel` 持回顾/提醒域）；用户操作走普通函数，表单类带 `onDone(Boolean)` 回调。原设想的「每屏 ViewModel + sealed Event」未采用 |
| 组合根 + 构造器注入 | `AppContainer` 手动装配，替换假仓库即可测 ViewModel |
| 值对象 | WardrobeCategory、OutfitImage、Tag(=String) |
| 策略 | BuildOutfitPrompt 的 PromptTemplate 文案模板可整体替换；演示模式的组合根装配切换 |
| 门面 | ShareClipboard 统一封装「剪贴板文本/图片 + 系统分享」的平台差异 |

## 状态与导航

- 全局：`AppViewModel` 暴露 `currentPerson: StateFlow<Person?>` 与 `data: StateFlow<WardrobeData>`。
- 当前角色：`WardrobeData.currentPersonOrFirst(savedId)` 是共享解析规则；聊天工具必须使用与 W1/W2 一致的首角色回退，避免 DataStore 尚未写入或保存了旧 id 时误判“当前没有角色”。
- 页面导航：Compose Navigation。路由：`home`(四 Tab，W12 顾问会话列表) / `itemEdit?itemId={itemId}` / `itemDetail/{itemId}` / `outfitDetail/{outfitId}` / `recap` / `wishlist` / `settings`（W11）/ `chat/{sessionId}`（W13 会话详情）；W2(PersonSheet)/W6(ExportSheet) 与心愿域各表单为 ModalBottomSheet 而非路由。
- 组合记忆（US-06）：各品类选中 itemId 存 `DataStore<Preferences>`（PrefsStore），key 按 personId 隔离。
- 回忆提醒开关存 DataStore（RecapPrefsStore），ReminderScheduler 对齐 WorkManager 任务。
- Mock 模式的 AI 偏好、Keystore alias、会话/用量目录、缓存目录与真实衣橱分命名空间；只有主动保存 Mock Key 后才允许外呼。`CachedMockChatModel` 以完整请求与 Key 摘要指纹命中私有缓存，失败/取消不写入。

## 错误处理

- Repository 所有写操作同步落盘；落盘失败抛出 → `AppViewModel.launchSafely` 统一捕获（it-020：Log + toast；带 `onDone` 契约的 saveItem/purchaseWishItem 失败回调 false）；内存快照由 libs/store 的 commit 序列回滚（commit 抛异常则快照不赋值）。
- 图片解码失败显示占位图，不阻塞列表；导入/抠图失败返回 null 并 toast，原图不受影响。

## 测试策略

- `domain`/`data` 纯 JVM 单测：Repository 不变量（级联删除、悬空清洗）、**Mock 与 Impl 级联语义一致性**（it-020）、RecapCalculator、BuildOutfitPrompt、Queries、Mock Chat 缓存命中/Key 变化 miss/失败不写。wardrobe.json 原子写/迁移/恢复由 libs/store 的 SnapshotStoreTest 覆盖（ADR-012）。
- UI 以模拟器走查验证；Compose UI 测试留待后续迭代。

## 构建配置

- compileSdk 35 / targetSdk 35 / minSdk 26；AGP 8.7.x + Gradle 8.9 + Kotlin 2.1.x（compose 插件）
- 版本号自动生成（it-067/ADR-027）：`versionName = VERSION_BASE.<wardrobe 提交数>`（git rev-list 限定 wardrobe/，工作树未提交改动加 `-dirty`）、`versionCode = 提交数`；git 不可用回退基线常量 0.5.0/5
- composite build：`includeBuild("../libs/store"、"../libs/carddeck"、"../libs/cutout"、"../libs/agent")`，坐标 `com.leo.libs:{store,carddeck,cutout,agent}`（ADR-012/013/016，agent 见 it-041/ADR-024）
- 依赖：Compose BOM、material3（Expressive）、navigation-compose、coil-compose、lottie-compose、kotlinx-serialization-json、androidx.exifinterface、DataStore preferences、onnxruntime-android 1.20.0（cutout 运行时，版本须与 SDK 编译期对齐，ADR-016）、leo-agent（BYOK 模型接入，okhttp 由 SDK 传递）、JUnit4 + kotlinx-coroutines-test；carddeck 自研内核（it-047）无三方卡组依赖，JitPack 仓已从 settings.gradle.kts 移除
- 权限：`POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`（it-018）+ **`INTERNET`（it-041/ADR-024，仅 BYOK 模型直连使用）**

## it-054 顾问回复解析边界

- `ChatViewModel.recommendationItems` 从 Repository 快照和当前角色偏好派生当前角色单品；W13 卡片图片只通过 `imageFile` 本地解析，不信任模型 URL。
- `ui/chat/OutfitRecommendationParser.kt` 是纯 Kotlin 文本解析层：识别约定的 Markdown 标题和品类行，输出 UI 值对象，不读写 Repository，不修改会话内容。
- `ui/chat/ChatMarkdown.kt` 负责 Markdown 子集排版与卡片组合呈现；复制仍调用原始 assistant 文本，UI 解析结果不回写 `Message`。
- 解析失败降级为普通 Markdown 正文，避免模型输出格式变化阻断聊天；本迭代不新增持久化字段和写入工具。
