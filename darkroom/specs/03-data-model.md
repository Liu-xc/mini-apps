# 03 — 数据模型

## 内存态（DarkroomViewModel.UiState）

| 字段 | 类型 | 说明 |
|---|---|---|
| screen | PICK/DEVELOP/RESULT/SETTINGS | 导航态 |
| photo | Bitmap? | ≤1600px 解码结果（会话唯一真源） |
| spec | CardSpec | 成片卡面字段 |
| progress | Float 0..1 | 显影进度（DevelopClock 单一真源） |
| playing/ejecting/shakeHint | Boolean | 显影台动画状态 |
| speed | DevelopSpeed | SLOW/STANDARD/FAST（12s/8s/4s） |
| exporting/exportProgress | Boolean/Float | 导出进度 |
| exportFormat | SQUARE/PORTRAIT | 1080×1080 / 1080×1350 |
| savedImageUri/savedVideoUri/lastSavedKind | Uri?/枚举 | 已落库结果（分享入口） |

## CardSpec（成片卡面，结构化单一真源）

| 字段 | 类型 | 约束 |
|---|---|---|
| title | String | ≤24 字，超长省略；空=只留日期章 |
| dateText | String | ≤11 字符，形如 `1988 07 21`（可任意改） |
| showWatermark | Boolean | 卡脚「显影 DARKROOM」显隐 |

## CardLayout（纯布局解，`solve(width)→rects`）

SIDE_FR=0.05、BOTTOM_FR=0.25 → 整卡高宽比 1.20；photo 正方；title/stamp/watermark 三矩形。
所有坐标随宽度等比缩放（预览/导出/视频同构）。

## DevelopVisual（进度→画面参数）

phase / imageAlpha / saturation / contrast / brightness / warmth / blurFraction /
reveal / grain / vignette —— 全部由 `DevelopSpec.visualAt(progress)` 确定性给出。

## 持久化（DataStore `darkroom_prefs`）

| key | 类型 | 默认 |
|---|---|---|
| develop_speed | String(enum) | STANDARD |
| shake_enabled | Boolean | true |
| watermark_default | Boolean | true |

## 落库产物

- 图片：`Pictures/显影/显影_<yyyy-MM-dd>.jpg`（JPEG 95）
- 视频：`Movies/显影/显影_<yyyy-MM-dd>.mp4`（H.264+AAC MP4）
- 均 IS_PENDING 两段式写入；分享走 content:// URI。
