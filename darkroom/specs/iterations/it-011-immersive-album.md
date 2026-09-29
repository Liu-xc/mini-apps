# it-011 — 沉浸式相册显影

状态：**实施完成（2026-09-29）**
日期：2026-09-29

## 背景与动机

Leo 的新玩法需求（原话归纳）：读取用户相册并在首页展示；左右滑动浏览；**每次切换到新照片就执行一遍
当前模式的显影动画**（拍立得/数码/胶片）；**本次启动内已渲染过的图片不再重复动画**；保留一个
**手动重播按钮**。

这是一条与「选一张 → 显影台 → 成片」并列的沉浸式路径：浏览即显影，翻到哪张哪张在你眼前洗出来。

## 方案

### 信息架构（W1 双态）

- **沉浸相册态**（已授权且相册非空）：大卡 HorizontalPager 占主区（左右滑翻页，邻页 peek 提示可滑），
  顶行沿用 `DARKROOM · 私人暗房` + 设置；底部一行 = 重播按钮 + 三枚模式 chip + 拍照入口；
  右上页码 `n / total`。
- **回退态**（未授权 / 拒绝 / 相册为空）：完整保留原选图布局（权限引导卡 + Photo Picker 主按钮 +
  拍一张 + 模式 chip + 样片），引导卡点击时才发起权限请求（不冷启动硬弹）；拒绝后按钮变「再试一次」
  并说明原入口不受影响。

### 播放语义（US-18）

- 每张卡内部状态机：`出纸（EditorialMotion.ejectEase 850ms）→ 显影（speed.durationMs 线性）→
  落定（Confirm 触感）`，曲线与 W2 共用真源（DevelopSpec/DevelopCard 原样复用）。
- **played 集（`GalleryPlayback`）**：本次启动内已播（含点按跳过）的 MediaStore id 集合；
  翻回已播页直接显示成品。滑走未播完的页回正成品态（未标 played，回来重播）。
- **点按两义**：显影中点按 = 跳到成品；成品点按 = 携该照片直达 W3（编辑/导出，日期章取照片
  拍摄日期，相纸/字体/脚注沿用当前选择）。W3 返回回到相册。
- **重播按钮**：把当前页 id 移出 played 集，卡片即重新出纸显影。
- 分页加载：60/页，近尾部预取；缩略图 loadThumbnail 1080 + 进程内 LRU（24 张）。
- 减弱动态：直接落成品（标记 played）。

### 权限（ADR-008）

READ_MEDIA_IMAGES（API 33+，34 起「选择照片」部分授权亦可读）/ READ_EXTERNAL_STORAGE（≤32，
maxSdkVersion 声明）。拒绝路径完整可用（Photo Picker 零权限语义保留）。**推翻 it-001 ADR-002
「全 App 零危险权限」中的相册读取部分**，拍照/导出仍零权限。

## 验证记录

- `testDebugUnitTest assembleDebug installDebug` 通过；67 测全绿（+GalleryPlaybackTest 3：
  首见即播/标记生效、重播精确移除、快照一致）。
- AVD 走查（Android 14，模拟器由并行会话间隙完成）：见下——授权前回退态与引导卡、`pm grant`
  直授后沉浸态、翻页首播/翻回不重播、点按跳过、重播、点开成片（W3 携拍摄日期）、W3 返回相册、
  未授权路径回归。（截图存 /tmp/dk-it011/）
- 版本 0.4.0（versionCode 9，新玩法属 minor 功能级）。

## 影响范围

- 代码：`data/AlbumRepository.kt`（新，含 ThumbCache）、`develop/GalleryPlayback.kt`（新）、
  `ui/pick/GalleryPager.kt`（新，GalleryPager/GalleryCard）、`ui/pick/PickScreen.kt`（双态重构）、
  `DarkroomViewModel.kt`（相册态/played/重播/openGalleryResult）、Manifest（运行时权限）。
- spec：01（US-18）、02（W1 双态线框）、03（AlbumPhoto/Repository、UiState 字段）、05（动效表
  相册卡行）、06（ADR-008）。
