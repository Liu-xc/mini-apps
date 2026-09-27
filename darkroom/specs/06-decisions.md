# 06 — 架构决策记录（ADR）

## ADR-001 minSdk = 31（RenderEffect 直通）

- **状态**：已采纳（2026-09-27，it-001）
- **背景**：显影模糊/调色需要 GPU 效果链；minSdk 31（Android 12）可用
  `RenderEffect.createBlurEffect/createColorMatrixEffect` 直通 Compose `graphicsLayer`。
- **决策**：minSdk 31，targetSdk 35。不做 ColorMatrix 软件渲染兼容路径。
- **代价**：放弃 Android 11 及以下设备（2026 年份额可忽略）。**回退预案**：若需覆盖，
  在 `DevelopCard`/`PhotoCardPainter` 加软件 ColorMatrix+缩小模糊分支，映射函数已纯化无需改。

## ADR-002 音效策略：App 内零音效，声音只入导出视频

- **状态**：已采纳（2026-09-27，it-001）
- **背景**：基线 DESIGN.md §4「不加音效（个人工具，公共场合使用）」；但提案（Leo 拍板
  「有情绪价值」）要求显影有出纸/药水/定影声音。
- **决策**：**App 内交互严格零音效**（滑条/按钮/导航无声，符合基线）；
  声音作为**导出视频的内容物**存在（`SfxSynth` 按 ExportPlan 时间线合成 PCM → AAC 入片）。
  视频发到社交平台时声轨是内容（拍立得出纸的仪式感），不是设备 UI 音。
- **依据**：与基线精神一致——公共场合打扰的是设备外放，内容物不受限；已在 05 §1 偏移声明登记。

## ADR-003 视频导出走 EGL 输入面，不录屏、不转 YUV

- **状态**：已采纳（2026-09-27，it-001）
- **备选**：① MediaProjection 录屏（侵入+权限+不可控）② ByteBuffer YUV 输入
  （ARGB→YUV420 手写色彩转换，画质/工作量差）③ 三方编码库（违反零依赖惯例）。
- **决策**：`codec.createInputSurface()` + EGL14 window surface，逐帧
  「离屏卡片位图 → 纹理 blit → `eglPresentationTimeANDROID` 精确打戳 → swap」。
  音轨先整段离线 AAC 编码进内存（几十 KB），首个视频输出格式到达时建 muxer 先写音轨
  （MediaMuxer 非分片模式 stop 时统一写 moov，无交错顺序问题）。
- **收益**：帧帧确定（与预览同一 `DevelopSpec` 映射）、不依赖实时帧率、零新依赖。

## ADR-004 显影模糊用「缩小再放大」近似（native 渲染端）

- **状态**：已采纳（2026-09-27，it-001）
- **背景**：`PhotoCardPainter`（位图/视频端）无 RenderEffect；Java `BlurMaskFilter` 只作用于
  描边，Bitmap 上开销大且半径受限。
- **决策**：按模糊半径把照片缩小到约 1/8–1/30 再双线性拉伸回原大——化学扩散感足够，
  速度可控。**预览端**（Compose）用真 GPU `createBlurEffect`，两端视觉近似而非像素一致
  （AC4 的约束是同一 `CardSpec`/布局/色彩矩阵，非逐像素同帧）。
- **代价**：预览与导出的模糊核略有差异；后续若要严格一致，可在预览端同样走缩小放大。

## ADR-005 确定性优先：进度是唯一真源

- **状态**：已采纳（2026-09-27，it-001）
- **决策**：一切画面 = `f(progress)`：`DevelopSpec.visualAt`（纯函数）→ 色彩矩阵/遮罩参数；
  时间推进只发生在 `DevelopClock`（毫秒累积，支持 seek/boost/倒放）；
  视频帧 = `ExportPlan.frameAt(index)` 采样同一函数。测试策略（曲线/时钟/时间线单测）
  与「药水条任意拖动重看」（AC2）都建立在此决策上。
