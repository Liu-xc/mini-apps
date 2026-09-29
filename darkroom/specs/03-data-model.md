# 03 — 数据模型

## 内存态（DarkroomViewModel.UiState）

| 字段 | 类型 | 说明 |
|---|---|---|
| screen | PICK/DEVELOP/RESULT/SETTINGS | 导航态 |
| photo | Bitmap? | ≤1600px 解码结果（会话唯一真源） |
| mode | `DevelopMode` | POLAROID / DIGITAL / FILM（it-007 US-14）；W1 选择，会话内不变 |
| spec | CardSpec | 成片卡面字段 |
| progress | Float 0..1 | 显影进度（DevelopClock 单一真源） |
| playing/ejecting/shakeHint | Boolean | 显影台动画状态 |
| speed | DevelopSpeed | SLOW/STANDARD/FAST（12s/8s/4s） |
| exporting/exportProgress | Boolean/Float | 导出进度 |
| exportFormat | `ShareFormat` | `SQUARE` 1080×1080 / `FEED` 1080×1350 / `STORY` 1080×1920；默认 `FEED`，仅当前会话状态 |
| savedImageUri/savedVideoUri/lastSavedKind | Uri?/枚举 | 已落库结果（分享入口） |

## CardSpec（成片卡面，结构化单一真源）

| 字段 | 类型 | 约束 |
|---|---|---|
| title | String | ≤24 字，超长省略；空=只留日期章 |
| dateText | String | ≤11 字符，形如 `1988 07 21`（可任意改） |
| footer | String | 卡脚脚注（it-010 替代 showWatermark）：≤24 字、超宽按域缩字；空=不印；默认「显影 DARKROOM」 |
| frame | FrameStyle 枚举 | 相纸框型（it-010，仅拍立得生效）：CLASSIC/CREAM/NOIR/SLATE，配色真源 `CardPalette.forFrame` |
| titleFont | TitleFontStyle 枚举 | SERIF（默认）/SANS |
| titleSize | TitleSizeOption 枚举 | SMALL 0.85 / REGULAR 1.0 / LARGE 1.16（限幅 0.8–1.2，单测锁） |

## CardLayout（纯布局解，`solve(width, maxH?, mode)→rects`）

几何按 `DevelopMode` 分派（it-007 ADR-007）：

| 模式 | 整卡高宽比 | 构成 |
|---|---|---|
| 拍立得 | 1.20（SIDE_FR 0.05、BOTTOM_FR 0.25） | 方形成像区居上 + 底边签名区 |
| 数码相机 | 1.20 | 全幅无边照片 + 底部 0.22w 的 OSD 信息带 |
| 胶片 | `FILM_ASPECT` ≈ 0.977 | 齿孔带 0.09w + 3:2 片格 + 齿孔带 + 片基信息带 0.13w |

三形态都解出 title / stamp / watermark 三矩形，且 title.right ≤ stamp.left（两域互斥）；
胶片额外给出 `sprocketTop/Bottom` 两条带，`sprocketHoles(layout)` 为两端渲染器解出 13×2 孔位。
`aspectOf(mode)` 供 `ShareLayout` 反解卡片构图。所有坐标随宽度等比缩放（预览/导出/视频同构）。

## DevelopVisual（进度→画面参数）

phase / imageAlpha / saturation / contrast / brightness / warmth / blurFraction /
reveal / grain / vignette —— 由 `DevelopSpec.visualAt(mode, progress)` 确定性给出。

it-007 增补字段：`redGain/greenGain/blueGain`（青/品红/黄分染料上色时序）、
`shadowLift`（暗部抬升，按 0..255 空间写入矩阵偏移）、`highlightGain`（高光压缩）、
`invert`（负片反相 0..1，胶片模式自 1 归 0，4×5 矩阵可完整表达）。
拍立得曲线给出分染料与窄动态；数码恒为 1/0/1（无染料、无窄动态）；胶片主要走 `invert`。

## RevealField（显现前沿，it-007 吸收原 ChemicalDiffusion）

`alphaMask(kind, w, h, reveal, seed)` / `alphaAt(kind, x, y, reveal, seed)`，
`kind ∈ {CHEMICAL, BLOCKS, SWEEP}`，720 桶缓存、照片种子确定性；
未显影底色按模式给色（相纸/胶片中性浅灰、数码屏近黑）。

## 持久化（DataStore `darkroom_prefs`）

| key | 类型 | 默认 |
|---|---|---|
| develop_speed | String(enum) | STANDARD |
| develop_mode | String(enum) | POLAROID（it-007 US-14，W1 选择） |
| shake_enabled | Boolean | true |
| watermark_default | Boolean（legacy） | true；it-010 起由 footer_default 承接：false → 空脚注迁移 |
| footer_default | String | 「显影 DARKROOM」；上一次 W3 脚注即默认（it-010 US-17） |
| frame_style | String(enum) | CLASSIC（it-010 US-16） |
| title_font | String(enum) | SERIF（it-010 US-17） |
| title_size | String(enum) | REGULAR（it-010 US-17） |

## 落库产物

- 图片：`Pictures/显影/显影_<yyyy-MM-dd>.jpg`（JPEG 95；画幅由 `ShareFormat` 决定）
- 视频：`Movies/显影/显影_<yyyy-MM-dd>.mp4`（H.264+AAC MP4；与图片共用 `ShareFormat`）
- 均 IS_PENDING 两段式写入；分享走 content:// URI。
