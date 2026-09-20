# 04 · 技术架构

## 分层与依赖规则

与 wardrobe 同构；本应用无 export 层，多一个地图封装层。

```
┌───────────────────────────────────────────┐
│ ui/        Compose 界面 + ViewModel        │  只依赖 domain（+ Android/Compose/osmdroid 互操作）
├───────────────────────────────────────────┤
│ domain/    实体、Repository 接口、UseCase   │  纯 Kotlin，无 Android 依赖（JVM 可测）
├───────────────────────────────────────────┤
│ data/      Repository 实现、JSON 存储、图片  │  实现 domain 接口
├───────────────────────────────────────────┤
│ map/       osmdroid 封装（MapView 生命周期、 │  Android 专用；marker/选点/瓦片缓存
│            marker 工厂、选点、瓦片源）        │
├───────────────────────────────────────────┤
│ platform/  系统交互门面（LinkOpener 等）     │  Android 专用；UI 不直接碰 Intent
└───────────────────────────────────────────┘
di/AppContainer.kt = 组合根，手动构造器注入
```

**规则**：依赖只能从上到下；domain 不 import data/ui/map 类型；UI 永远通过 ViewModel 拿数据；地图 SDK 不越出 map/ 层。

## 模块结构

```
com.leo.eats/
├─ EatsApp.kt                     # Application：创建 AppContainer
├─ MainActivity.kt                # 单 Activity：NavHost + 底部三 Tab + 主题
├─ di/AppContainer.kt             # 组合根
├─ domain/
│  ├─ model/      Place Visit PlaceKind GeoLoc PlaceLink LinkSource TagPresets EatsData PlaceWithStats
│  ├─ repository/ EatsRepository(接口) ImageStore(接口)
│  └─ usecase/    BuildCandidates(过滤) ComputeStats(派生统计)  // SpinWheel 权重抽取已随 it-003 移除
├─ data/
│  ├─ repo/EatsRepositoryImpl.kt  # SSOT 基类 SsotRepository 来自 libs/store（ADR-010）；不变量与图片级联在本类
│  ├─ image/ImageFileStore.kt     # URI→WebP 压缩；文件管理由 SDK FileMediaStore 承担
│  └─ mock/                       # it-006 演示模式：MockEatsData（种子）/ MockEatsRepository（内存）/ DemoMode（开关）
├─ map/
│  ├─ MapController.kt            # MapView 生命周期 / 瓦片缓存
│  ├─ ChinaTileSource.kt          # 高德栅格瓦片源（ADR-002 修订，国内可达）
│  ├─ PlaceMarkerFactory.kt       # 类型 → 颜色 / 图标
│  └─ PickLocationController.kt   # 长按选点
├─ platform/
│  └─ LinkOpener.kt               # ACTION_VIEW 打开链接 + 无处理组件兜底（门面）
└─ ui/
   ├─ theme/       DesignTokens Typography EatsTheme
   ├─ components/  PhotoStrip RatingStars TagChipInput VisitTimeline EmptyState KindChip LinkChips…
   ├─ AppViewModel.kt             # 全局数据流出口（SSOT）
   ├─ spin/        SpinScreen(W1) WheelCanvas
   ├─ mapview/     MapScreen(W2) PlaceSummaryCard
   ├─ list/        ListScreen(W3) PlaceEditScreen(W4)
   ├─ detail/      PlaceDetailScreen(W5)
   └─ visit/       LogVisitSheet(W6)
```

## 设计模式

| 模式 | 用在哪 |
|---|---|
| Repository | domain 定接口、data 实现；UI 不感知持久化 |
| SSOT 单一数据源 | `EatsRepositoryImpl` 继承 libs/store 的 `SsotRepository`：写操作「改快照 → 原子落盘 → 广播」由 SDK 承担（ADR-010），三 Tab 全部是流上 map |
| MVVM + UDF | 每屏 ViewModel 暴露 `StateFlow<UiState>`，事件走 sealed interface |
| 组合根 + 构造器注入 | AppContainer 手动装配；替换假仓库即可测 ViewModel |
| 值对象 | PlaceKind、GeoLoc、Tag(=String) |
| 策略 | DemoMode 组合根装配切换（真实/Mock 仓库同接口互换，it-006） |
| 门面 | LinkOpener 封装 ACTION_VIEW 与无处理组件兜底，UI 不直接碰 Intent |

## 状态与导航

- 全局：`AppViewModel` 暴露 `data: StateFlow<EatsData>`；列表排序筛选、地图点集、抽取候选均是流上派生。
- 导航：Compose Navigation。路由：`main`（三 Tab） / `placeEdit/{placeId?}` / `placeDetail/{placeId}`；W6 与选点地图为 ModalBottomSheet 而非路由。
- 抽取过滤配置（类型/忌口/排除天数）存 `DataStore<Preferences>`，跨启动保留。
- 演示模式（it-006）：开关（SharedPreferences）在组合根构造时读取，决定装配真实仓库或内存 Mock 仓库；
  切换重启进程生效；演示中 MainActivity 顶部常驻横幅，点按退出（DEBUG 构建才有入口）。

## 抽取引擎（W1 卡组，it-003）

1. `BuildCandidates`：kind 集合过滤 → 剔除含排除标签的 → 「排除最近 N 天吃过」按 lastVisitAt 剔除。
2. 浏览与抽取：libs/carddeck 卡组侧滑浏览；「随机抽一张」= 随机步数 + 库自带飞出动画按拍播放，落点均匀无权重。
3. 落定：结果条（就吃这个 → W6 预填 / 再抽）+ 彩屑；原 SpinWheel 权重抽取已随 it-003 移除（ADR-006 作废）。

## 错误处理

- 写操作同步落盘，失败抛出 → ViewModel 捕获 → snackbar + 回滚内存快照。
- 地图瓦片加载失败静默降级（缓存/空白底图），marker 与交互不受影响。

## 测试策略

- JVM 单测：Repository 不变量（级联删除/悬空清洗/派生统计）与 Mock 仓库种子、BuildCandidates 过滤、LinkSource.detect 来源识别（eats.json 持久化由 libs/store 的 SnapshotStoreTest 覆盖）。
- UI 以模拟器截图验证（对照 02 线框）；Compose UI 测试留待后续迭代。

## 构建配置

- compileSdk / targetSdk 35 / minSdk 26；AGP 8.7.x + Kotlin 2.0.x（与 wardrobe 同基线）
- 依赖：Compose BOM、material3 1.4.0、navigation-compose、coil-compose、kotlinx-serialization-json、**osmdroid-android 6.1.x**、**com.leo.libs:carddeck**（composite build，W1 卡组）、**com.leo.libs:store**（composite build，ADR-010）、DataStore preferences、JUnit4 + kotlinx-coroutines-test
