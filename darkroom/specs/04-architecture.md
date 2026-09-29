# 04 — 架构与分层

## 模块结构（单 app 模块，包即层）

```
com.leo.darkroom
├── develop/          显影引擎（纯 Kotlin，JVM 单测直达）
│   ├── DevelopMode     显影模式三态：阶段名/文案/显现方式/出纸方式/导出起手（it-007）
│   ├── DevelopSpec     模式+进度→视觉参数 + 4×5 色彩矩阵（确定性映射）
│   ├── DevelopClock    毫秒进度累积：tick/boost/seek（甩一甩/刻度条）
│   └── RevealField     三种显现遮罩（拍立得白浊均匀消散/网格块/横向冲洗），共用噪声与桶缓存
├── card/             成片卡面（布局 + 两套渲染端共享真源）
│   ├── CardLayout      纯数学布局 solve(width, maxH?, mode) + aspectOf + sprocketHoles（单测覆盖）
│   ├── CardSpec        结构化字段
│   ├── PhotoLook       五种确定性照片风格参数（it-005）
│   ├── PhotoCardPainter  android.graphics 渲染器（位图导出 + 视频逐帧；内部按 mode 推导 CardPalette）
│   ├── GrainNoise      确定性颗粒噪声瓦片
│   ├── ShareLayout     1:1/4:5/9:16 构图解算与平台安全区（按 mode 反解卡宽）
│   ├── ChemicalMaskBitmap  显现遮罩位图缓存（kind+bucket+seed，Compose/native 共用）
│   └── SampleArt       本地照片样片加载（零权限入口）
├── export/
│   ├── VideoExporter   EGL14 + MediaCodec + MediaMuxer（先音后视频，见 ADR-003）
│   ├── SfxSynth        确定性音轨合成（纯 Kotlin DSP）
│   ├── ShareHelper     MediaStore 落库 + 分享 intent + FileProvider
│   └── ExportPlan      时间线（lead/develop/hold → 帧序列，lead 按模式取值，单测）
├── ui/
│   ├── develop/DevelopCard  Compose 预览渲染器（与 PhotoCardPainter 对表，按 mode 取 CardPalette）
│   ├── develop/ChemicalGauge 自绘药水刻度条（it-007 M1，替换 Material Slider）
│   ├── pick/ModeRow     W1 显影模式选择（it-007 US-14）
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
`card/PhotoCardPainter` 与 `ui/develop/DevelopCard` 互不引用，**共享 DevelopSpec + CardLayout + RevealField + ShareLayout 真源**
（`card` 单向依赖 `develop` 取 DevelopMode/DevelopSpec，与 PhotoLook 既有方向一致）。
不引入 libs/*（MVP 无跨应用需求）。

## 关键管线

- **帧循环**：`withFrameNanos` vsync 驱动 → `DevelopClock.tick(dt)` → StateFlow 进度 →
  Compose 重组渲染。暂停（刻度条拖动）停 tick 但保持帧泵。
- **视频导出**：单后台线程完成 采集→(音轨合成+编码)→EGL makeCurrent→逐帧
  （`PhotoCardPainter.paintShareFrame` 依 `ShareLayout` 生成画布 → 纹理 blit → `eglPresentationTimeANDROID` → swap）→ 编码 drain → mux。
  进度以帧号回调 StateFlow。
- **成片风格**（it-005）：W3 选择保存在 `UiState.photoLook`，由 `PhotoCardPainter` 把同一确定性色调矩阵、颗粒与可选高光晕光用于预览、静图和视频逐帧；新照片复位原色，不写入 `CardSpec`。柔光高光中间图按原照片弱引用缓存，视频帧复用。
- **显影模式**（it-007，ADR-007）：`UiState.mode` 由 W1 `setMode` 先写会话态再落 DataStore，
  启动时只回填一次且用户已选则不回填（避开旧快照回冲）。三个渲染端共用同一组分派函数：
  `DevelopSpec.visualAt(mode, p)`（曲线）+ `RevealField.alphaMask(mode.reveal, …)`（显现）+
  `CardLayout.solve(mode, …)`（几何）+ `CardPalette.forMode(mode)`（印字配色）；
  `ShareLayout.solve(…, mode)` 按 `aspectOf(mode)` 反解卡片矩形。
  `ExportPlan` 的 lead 段按 `mode.leadMs` 取值（胶片 800ms，其余 500ms）。
- **单测边界**：纯 Kotlin 层全量覆盖；`VideoExporter`/`DevelopCard` 走模拟器实测（it-001 验证记录）。

## 构建

独立 Gradle 工程（同 wardrobe/eats），共用根 `gradle/libs.versions.toml`（it-020）；
minSdk 31（RenderEffect，ADR-001）/ target 35 / Kotlin 2.1.21 / M3 1.4.0。
