# 04 · 技术架构

## 分层与依赖规则

```
┌───────────────────────────────────────────┐
│ ui/        Compose 界面 + ViewModel        │  只依赖 domain（+ Android/Compose）
├───────────────────────────────────────────┤
│ domain/    实体、Repository 接口、UseCase   │  纯 Kotlin，无 Android 依赖（JVM 可测）
├───────────────────────────────────────────┤
│ data/      Repository 实现、JSON 存储、图片  │  实现 domain 接口
├───────────────────────────────────────────┤
│ export/    合成图/文案/剪贴板分享（门面）    │  Android 图形与系统交互
└───────────────────────────────────────────┘
di/AppContainer.kt = 组合根，装配一切依赖（手动构造器注入）
```

**规则**：依赖只能从上到下；domain 不 import 任何 data/ui/export 类型；UI 永远通过 ViewModel 间接拿数据。

## 模块结构

```
com.leo.wardrobe/
├─ WardrobeApp.kt                 # Application：创建 AppContainer
├─ MainActivity.kt                # 单 Activity：NavHost + 底部三 Tab + 主题
├─ di/AppContainer.kt             # 组合根
├─ domain/
│  ├─ model/      Person Item Outfit OutfitImage Note WardrobeCategory TagPresets WardrobeData
│  ├─ repository/ WardrobeRepository(接口) ImageStore(接口)
│  └─ usecase/    ComposeOutfitImage BuildOutfitPrompt PickRandomOutfit ImportItemPhoto(接口层)
├─ data/
│  ├─ json/JsonFileStore.kt       # 读写 wardrobe.json（原子写 + schemaVersion 迁移 + bak）
│  ├─ repo/WardrobeRepositoryImpl.kt
│  └─ image/ImageFileStore.kt     # URI→WebP 压缩落盘、删除、查找
├─ export/
│  ├─ OutfitImageComposer.kt      # Bitmap 拼合成图（2列网格+品类标签）
│  ├─ PromptBuilder.kt            # 文案模板（策略：可替换模板）
│  └─ ShareClipboard.kt           # 复制文本/图片、ACTION_SEND 分享（门面）
└─ ui/
   ├─ theme/       DesignTokens Typography WardrobeTheme
   ├─ components/  PhotoCard TagChipInput TagRow CommentTimeline EmptyState SlotPager…
   ├─ AppViewModel.kt             # 全局：角色状态 + 数据流（SSOT 出口）
   ├─ outfit/      OutfitScreen( W1 ) PersonSheet( W2 ) ExportSheet( W6 )
   ├─ records/     RecordsScreen( W8 ) OutfitDetailScreen( W7 )
   ├─ wardrobe/    WardrobeScreen( W3 ) ItemEditScreen( W4 )
   └─ detail/      ItemDetailScreen( W5 )
```

## 设计模式

| 模式 | 用在哪 |
|---|---|
| Repository | `domain.repository` 定接口、`data.repo` 实现；UI 不感知持久化 |
| SSOT 单一数据源 | `WardrobeRepositoryImpl` 持内存快照，暴露 `StateFlow<WardrobeData>`；任何写操作：更新快照 → 原子持久化 → 流自动广播。三 Tab 与角色过滤全部是流上 `map` |
| MVVM + UDF | 每屏 `ViewModel` 暴露 `StateFlow<UiState>`；用户操作走 sealed interface Event；state 向下、event 向上 |
| 组合根 + 构造器注入 | `AppContainer` 手动装配，替换假仓库即可测 ViewModel |
| 值对象 | WardrobeCategory、OutfitImage、Tag(=String) |
| 策略 | PromptBuilder 文案模板可整体替换 |
| 门面 | ShareClipboard 统一封装「剪贴板文本/图片 + 系统分享」的平台差异 |

## 状态与导航

- 全局：`AppViewModel` 暴露 `currentPerson: StateFlow<Person>` 与 `data: StateFlow<WardrobeData>`；各屏 ViewModel 从它派生自己角色的子集。
- 页面导航：Compose Navigation。路由：`main`(含三 Tab) / `itemEdit/{itemId?}` / `itemDetail/{itemId}` / `outfitDetail/{outfitId}`；W2/W6 为 ModalBottomSheet 而非路由。
- 组合记忆（US-06）：各品类选中 itemId 存 `DataStore<Preferences>`，key 按 personId 隔离。

## 错误处理

- Repository 所有写操作同步落盘；落盘失败抛出 → ViewModel 捕获 → snackbar 提示且回滚内存快照。
- 图片解码失败显示占位图，不阻塞列表。

## 测试策略

- `domain`/`data` 纯 JVM 单测：JsonFileStore 读写与迁移、Repository 不变量（级联删除、悬空清洗）、PromptBuilder 文案、PickRandomOutfit。
- UI 以模拟器手动/截图验证（it-001），Compose UI 测试留待后续迭代。

## 构建配置

- compileSdk 35 / targetSdk 35 / minSdk 26；AGP 8.7.x + Gradle 8.9 + Kotlin 2.0.x（compose 插件）
- 依赖：Compose BOM、material3（Expressive）、navigation-compose、coil-compose、lottie-compose、kotlinx-serialization-json、androidx.exifinterface、DataStore preferences、JUnit4 + kotlinx-coroutines-test
