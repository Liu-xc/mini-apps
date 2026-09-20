# 04 · 技术架构

## 分层与依赖规则

与 wardrobe 同构；本应用无 export 层，多一个地图封装层。

```
┌───────────────────────────────────────────┐
│ ui/        Compose 界面 + ViewModel        │  主要依赖 domain（+ Android/Compose/osmdroid 互操作）
├───────────────────────────────────────────┤
│ domain/    实体、Repository 接口、UseCase   │  纯 Kotlin，无 Android 依赖（JVM 可测）
├───────────────────────────────────────────┤
│ data/      Repository 实现、图片编解码、偏好  │  实现 domain 接口；持久化机制由 libs/store 承担
├───────────────────────────────────────────┤
│ map/       osmdroid 封装（MapView 工厂、    │  Android 专用；marker 工厂、瓦片源
│            marker 工厂、瓦片源）             │
├───────────────────────────────────────────┤
│ platform/  系统交互门面（链接/提醒/存相册）   │  Android 专用；UI 不直接碰 Intent
└───────────────────────────────────────────┘
di/AppContainer.kt = 组合根，手动构造器注入
```

**规则**：依赖只能从上到下；domain 不 import data/ui/map 类型；UI 永远通过 ViewModel 拿数据；地图 SDK 不越出 map/ 层——`ui/mapview/` 为 Compose 互操作层（MapScreen、LocationPickerSheet 长按选点在此实现），`di/AppContainer` 持有 osmdroid 全局 Configuration 初始化。
**现状例外**（it-009 记录）：ListScreen 直调 `data.mock.DemoMode`（演示入口显隐）；AppViewModel 引用 `data.prefs.RecapReminderPrefs` 类型。

## 模块结构

```
com.leo.eats/
├─ EatsApp.kt                     # Application：创建 AppContainer
├─ MainActivity.kt                # 单 Activity：NavHost + 底部三 Tab + 主题
├─ di/AppContainer.kt             # 组合根（含演示模式仓库/图片目录切换、osmdroid 全局配置）
├─ domain/
│  ├─ model/      Place Visit PlaceKind PlaceCategory GeoLoc PlaceLink LinkSource
│  │              TagPresets EatsData PlaceWithStats（Queries.kt 派生查询与统计）
│  ├─ repository/ EatsRepository(接口) ImageStore(接口)
│  └─ usecase/    BuildCandidates(转盘候选过滤) MemoryCandidateSelector(提醒候选) RecapCalculator(回顾统计)
├─ data/
│  ├─ repo/EatsRepositoryImpl.kt  # SSOT 基类 SsotRepository 来自 libs/store（ADR-010）；不变量与图片级联在本类
│  ├─ image/ImageFileStore.kt     # URI→WebP 压缩；文件管理由 SDK FileMediaStore 承担
│  ├─ prefs/                      # SpinPrefsStore（转盘过滤配置） RecapPrefsStore（回忆提醒）
│  └─ mock/                       # it-006 演示模式：MockEatsData（种子）/ MockEatsRepository（内存）/ DemoMode（开关）
├─ map/
│  ├─ MapController.kt            # MapView 工厂与统一配置（瓦片源/DPI/初始视野）
│  ├─ ChinaTileSource.kt          # 高德栅格瓦片源（ADR-002 修订，国内可达）
│  └─ PlaceMarkerFactory.kt       # 分类 → 颜色圆点 / 选中态（it-008 起按 category 着色）
├─ platform/
│  ├─ LinkOpener.kt               # ACTION_VIEW 打开链接 + 无处理组件兜底（门面）
│  ├─ ReminderScheduler.kt        # WorkManager「好久没去」提醒（it-007，ADR-013）
│  └─ RecapSaver.kt               # 长图存相册 / 分享 Intent（it-007）
└─ ui/
   ├─ theme/       DesignTokens Typography EatsTheme
   ├─ components/  PhotoStrip RatingStars TagChipInput VisitTimeline EmptyState KindChip LinkChips…
   ├─ AppViewModel.kt             # 全局数据流出口（SSOT）
   ├─ spin/        SpinScreen(W1)
   ├─ mapview/     MapScreen(W2) LocationPickerSheet(长按选点)
   ├─ list/        ListScreen(W3) PlaceEditScreen(W4)
   ├─ detail/      PlaceDetailScreen(W5)
   ├─ visit/       LogVisitSheet(W6)
   └─ recap/       RecapScreen(W7，it-007) RecapLongImage(年度长图)
```

## 设计模式

| 模式 | 用在哪 |
|---|---|
| Repository | domain 定接口、data 实现；UI 不感知持久化 |
| SSOT 单一数据源 | `EatsRepositoryImpl` 继承 libs/store 的 `SsotRepository`：写操作「改快照 → 原子落盘 → 广播」由 SDK 承担（ADR-010），三 Tab 全部是流上 map |
| MVVM + UDF | **全局单 `AppViewModel`**（SSOT 出口）暴露 `StateFlow`；用户操作走普通函数，表单类带 `onDone(Boolean)` 回调。原设想的「每屏 ViewModel + sealed Event」未采用（it-009 起如实记录现状） |
| 组合根 + 构造器注入 | AppContainer 手动装配；替换假仓库即可测 ViewModel |
| 值对象 | PlaceKind、PlaceCategory、GeoLoc、Tag(=String) |
| 策略 | DemoMode 组合根装配切换（真实/Mock 仓库同接口互换、入口双向确认，it-006） |
| 门面 | LinkOpener/ReminderScheduler/RecapSaver 封装系统交互，UI 不直接碰 Intent/WorkManager |

## 状态与导航

- 全局：`AppViewModel` 暴露 `data: StateFlow<EatsData>`；列表排序筛选、地图点集、抽取候选均是流上派生。
- 导航：Compose Navigation。路由：`home`（三 Tab） / `placeEdit?placeId={placeId}` / `placeDetail/{placeId}` / `recap`；W6 与选点地图为 ModalBottomSheet 而非路由。
- 抽取过滤配置（分类/类型/忌口/排除天数/只抽愿望）存 `DataStore<Preferences>`（SpinPrefsStore），跨启动保留。
- 演示模式（it-006）：开关（SharedPreferences）在组合根构造时读取，决定装配真实仓库或内存 Mock 仓库；切换重启进程生效；DEBUG 构建的列表页工具行按钮为唯一入口，按当前模式弹出「进入/退出演示」确认。

## 抽取引擎（W1 卡组，it-003）

1. `BuildCandidates`：category 集合过滤 → kind 集合过滤 → 「只抽愿望」（wishlistedAt != null）→ 剔除含排除标签的 → 「排除最近 N 天吃过」按 lastVisitAt 剔除（it-008）。
2. 浏览与抽取：libs/carddeck 卡组侧滑浏览；「随机抽一张」= 随机步数 + 库自带飞出动画按拍播放，落点均匀无权重。
3. 落定：结果条（就吃这个 → W6 预填 / 再抽）+ 彩屑；原 SpinWheel 权重抽取已随 it-003 移除（ADR-006 作废）。

## 错误处理

- 写操作同步落盘，失败抛出 → `AppViewModel.launchSafely` 统一捕获（it-009：Log + toast；内存快照由 libs/store 的 commit 序列天然回滚）。
- 地图瓦片加载失败静默降级（缓存/空白底图），marker 与交互不受影响。

## 测试策略

- JVM 单测：Repository 不变量（级联删除/悬空清洗）、派生统计、Mock 仓库种子、BuildCandidates 过滤、MemoryCandidateSelector、LinkSource.detect 来源识别、旧数据兼容（PlaceCompat）。eats.json 持久化由 libs/store 的 SnapshotStoreTest 覆盖（ADR-010）。
- UI 以模拟器走查验证（对照 02 线框）；Compose UI 测试留待后续迭代。

## 构建配置

- compileSdk / targetSdk 35 / minSdk 26；AGP 8.7.x + Gradle 8.9 + Kotlin 2.1.x（与 wardrobe 同基线）
- composite build：`includeBuild("../libs/carddeck"、"../libs/store")`，坐标 `com.leo.libs:{carddeck,store}`（ADR-011/010）；JitPack 仓（carddeck 传递依赖）
- 依赖：Compose BOM、material3 1.4.0、navigation-compose、coil-compose、kotlinx-serialization-json、osmdroid-android 6.1.x、DataStore preferences、JUnit4 + kotlinx-coroutines-test
