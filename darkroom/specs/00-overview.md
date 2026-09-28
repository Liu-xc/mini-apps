# 00 — 业务背景与核心价值流

## 定位

**显影（darkroom）= 情绪价值优先的显影工具**。传入一张图片，看它像相纸一样从空白慢慢显影
（三阶段），再「打印」成带灰阶印字的结构化卡片；一键导出**成片位图**或**显影过程视频**
（H.264 MP4，含合成音轨），随时分享。it-007 起提供三种显影模式：**拍立得**（白框相纸，按实物还原）/
**数码相机**（回放屏 + 网格块显现）/ **胶片**（齿孔片条 + 负片翻正），各有独立卡面、显现方式与出纸动画。

一句话定位：**把回忆洗出来**。

- 不是修图器（非 Snapseed）；不联网、无账号，全程本机处理。
- 核心不是效率，是**仪式感**：慢、有触感、有惊喜；反精修——照片本来就在，我们把它重新「洗」一遍。

## 核心价值流

```
相册 Photo Picker / 拍照 intent / 内置示例图（零权限入口）
  → 统一解码 ≤1600px ARGB_8888（EXIF 旋转校正）
  → 显影模式（W1 选择，DataStore 持久化）
  → 显影台：DevelopClock 推进 progress → DevelopSpec.visualAt(mode, p) 确定性映射（视觉参数 + 色彩矩阵）
      预览端 = Compose DevelopCard（RenderEffect 模糊/ColorMatrix/晕开/暗角/颗粒）
      药水刻度条拖动倒放、甩一甩（加速度计）加速
  → RevealField(mode.reveal) 显现前沿（化学偏心 / 网格块 / 横向冲洗）稳定推进
  → 定影定格 → 成片页：CardSpec（标题/日期章/水印）+ CardLayout.solve(mode, …) → 同一渲染器三输出
      ① 预览 DevelopCard ② 位图 PhotoCardPainter.renderCard ③ 视频 VideoExporter 逐帧
  → MediaStore「显影」相簿 + 分享面板（content:// 直出）
```

## 视频导出（一等公民，it-002 确认）

`ExportPlan`：0.5s 空白起手 → 显影全程（对应速度档）→ 定影 hold 1.5s；
1080×1080 / 1080×1350 两档，30fps，`SfxSynth` 合成出纸/药水/定影音轨整段 AAC 入片。
管线：音轨离线合成+编码 → EGL 窗口面逐帧 blit（`eglPresentationTimeANDROID` 打戳）→
MediaCodec(H.264) → MediaMuxer(MP4)。

## 非目标（MVP）

- 滤镜/修图编辑器；30 天延迟「胶卷」模式；云同步/社交；相纸模板商城；多设备接力。

## 关联迭代

- `specs/iterations/it-001-polaroid-develop.md`（MVP 全功能闭环，含视频导出）
- `specs/iterations/it-007-develop-modes-and-fidelity.md`（三显影模式、拍立得还原、药水刻度条）。
