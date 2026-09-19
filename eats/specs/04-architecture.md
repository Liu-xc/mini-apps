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
│            marker 工厂、选点控制器）         │
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
│  └─ usecase/    BuildCandidates(过滤) SpinWheel(加权抽取) ComputeStats(派生统计)
├─ data/
│  ├─ json/JsonFileStore.kt       # eats.json 原子写 + bak + schemaVersion 迁移
│  ├─ repo/EatsRepositoryImpl.kt  # 内存快照 + StateFlow（SSOT）
│  └─ image/ImageFileStore.kt     # URI→WebP 压缩落盘 / 删除
├─ map/
│  ├─ MapController.kt            # MapView 生命周期 / 瓦片缓存
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
| SSOT 单一数据源 | RepositoryImpl 内存快照 + `StateFlow<EatsData>`；写操作「改快照 → 原子落盘 → 广播」，三 Tab 全部是流上 map |
| MVVM + UDF | 每屏 ViewModel 暴露 `StateFlow<UiState>`，事件走 sealed interface |
| 组合根 + 构造器注入 | AppContainer 手动装配；替换假仓库即可测 ViewModel |
| 值对象 | PlaceKind、GeoLoc、Tag(=String) |
| 策略 | SpinWheel 权重公式参数化（排除天数、权重函数集中可调） |
| 门面 | LinkOpener 封装 ACTION_VIEW 与无处理组件兜底，UI 不直接碰 Intent |

## 状态与导航

- 全局：`AppViewModel` 暴露 `data: StateFlow<EatsData>`；列表排序筛选、地图点集、转盘候选均是流上派生。
- 导航：Compose Navigation。路由：`main`（三 Tab） / `placeEdit/{placeId?}` / `placeDetail/{placeId}`；W6 与选点地图为 ModalBottomSheet 而非路由。
- 转盘过滤配置（类型/忌口/排除天数）存 `DataStore<Preferences>`，跨启动保留。

## 决策引擎（SpinWheel）

1. `BuildCandidates`：kind 集合过滤 → 剔除含排除标签的 → 「排除最近 N 天吃过」按 lastVisitAt 剔除。
2. 权重：`w = 1 + daysSinceLastVisit`（从未吃过按 30 计）；纯函数，JVM 单测覆盖（含分布断言）。
3. 抽取：按权重随机选一项，并向 WheelCanvas 输出动画时长/圈数参数。

## 错误处理

- 写操作同步落盘，失败抛出 → ViewModel 捕获 → snackbar + 回滚内存快照。
- 地图瓦片加载失败静默降级（缓存/空白底图），marker 与交互不受影响。

## 测试策略

- JVM 单测：JsonFileStore 读写与迁移、Repository 不变量（级联删除/悬空清洗/派生统计）、BuildCandidates 过滤、SpinWheel 权重、LinkSource.detect 来源识别。
- UI 以模拟器截图验证（对照 02 线框）；Compose UI 测试留待后续迭代。

## 构建配置

- compileSdk / targetSdk 35 / minSdk 26；AGP 8.7.x + Kotlin 2.0.x（与 wardrobe 同基线）
- 依赖：Compose BOM、material3 1.4.0、navigation-compose、coil-compose、kotlinx-serialization-json、**osmdroid-android 6.1.x**、DataStore preferences、JUnit4 + kotlinx-coroutines-test
