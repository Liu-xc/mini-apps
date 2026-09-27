# 04 — 架构与分层

## 模块结构（单 app 模块，包即层）

```
com.leo.darkroom
├── develop/          显影引擎（纯 Kotlin，JVM 单测直达）
│   ├── DevelopSpec     进度→视觉参数 + 4×5 色彩矩阵（确定性映射）
│   ├── DevelopClock    毫秒进度累积：tick/boost/seek（甩一甩/药水条）
│   └── ChemicalDiffusion  照片种子驱动的低频药膜扩散遮罩（纯 Kotlin）
├── card/             成片卡面（布局 + 两套渲染端共享真源）
│   ├── CardLayout      纯数学布局 solve(width, maxH?)（单测覆盖；可用高度反解 + 签名域互斥，it-002）
│   ├── CardSpec        结构化字段
│   ├── PhotoCardPainter  android.graphics 渲染器（位图导出 + 视频逐帧）
│   ├── GrainNoise      确定性颗粒噪声瓦片
│   ├── ShareLayout     1:1/4:5/9:16 构图解算与平台安全区
│   ├── ChemicalMaskBitmap  扩散场位图缓存（Compose/native 共用）
│   └── SampleArt       本地照片样片加载（零权限入口）
├── export/
│   ├── VideoExporter   EGL14 + MediaCodec + MediaMuxer（先音后视频，见 ADR-003）
│   ├── SfxSynth        确定性音轨合成（纯 Kotlin DSP）
│   ├── ShareHelper     MediaStore 落库 + 分享 intent + FileProvider
│   └── ExportPlan      时间线（lead/develop/hold → 帧序列，单测）
├── ui/
│   ├── develop/DevelopCard  Compose 预览渲染器（与 PhotoCardPainter 对表）
│   ├── pick|develop|result|settings  四屏
│   └── theme/          DesignTokens/Motion/Typography/DarkroomTheme（继承基线）
├── data/             PrefsStore(DataStore) + PhotoRepository(解码)
├── platform/         Haptics + ShakeDetector
├── DarkroomViewModel 状态机 + vsync 帧循环（withFrameNanos）
└── DarkroomApp/MainActivity  导航壳（crossfade 220ms）
```

## 依赖规则

`ui → develop/card/export/data/platform`（单向）；`export → card` 复用统一卡片渲染；
`card` 不依赖 `export`，`develop` 不依赖 `android.*`（仅 Compose runtime 注解，保证 JVM 单测）；
`card/PhotoCardPainter` 与 `ui/develop/DevelopCard` 互不引用，**共享 DevelopSpec + CardLayout + ChemicalDiffusion + ShareLayout 真源**。
不引入 libs/*（MVP 无跨应用需求）。

## 关键管线

- **帧循环**：`withFrameNanos` vsync 驱动 → `DevelopClock.tick(dt)` → StateFlow 进度 →
  Compose 重组渲染。暂停（药水条拖动）停 tick 但保持帧泵。
- **视频导出**：单后台线程完成 采集→(音轨合成+编码)→EGL makeCurrent→逐帧
  （`PhotoCardPainter.paintShareFrame` 依 `ShareLayout` 生成画布 → 纹理 blit → `eglPresentationTimeANDROID` → swap）→ 编码 drain → mux。
  进度以帧号回调 StateFlow。
- **单测边界**：纯 Kotlin 层全量覆盖；`VideoExporter`/`DevelopCard` 走模拟器实测（it-001 验证记录）。

## 构建

独立 Gradle 工程（同 wardrobe/eats），共用根 `gradle/libs.versions.toml`（it-020）；
minSdk 31（RenderEffect，ADR-001）/ target 35 / Kotlin 2.1.21 / M3 1.4.0。
