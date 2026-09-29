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
- 数据通路预验：相册播种（adb push 3 张样片至 Pictures）已被 MediaStore 索引（content query
  实证），缩略图解码走 loadThumbnail 标准路径。
- **AVD UI 走查待补**：验证窗口内模拟器被并行 wardrobe 会话持续占用（相机实拍流程，不宜打断），
  授权前回退态/沉浸态翻页/跳过/重播/点开成片的实机走查未完成——待模拟器空出后补录，或以
  Leo 真机实测反馈替代。编译期风险已由单测与既有真源复用（DevelopCard/ejectEase）压低。
- 版本 0.4.0（versionCode 9，新玩法属 minor 功能级）。

**收尾修正（2026-09-29，Leo 实机反馈「怎么退出/点按应看大图/成片与编辑要分开」）**：

1. **W3 拆双态**：`resultEditing` ——显影完成默认落**成片查看态**（大预览 + 点按看大图 + 仅关键路径
   操作：存图片/存视频/分享/再洗一张）；「编辑」按钮进**编辑态**（风格/相纸/字体/卡面/画幅全部细项 +
   右上「完成」返回查看态；返回键同义）。沉浸相册进成片也默认查看态。
2. **点按=查看大图**：沉浸相册点按成品卡打开共享 `PhotoViewer`（暗底全屏），底部动作「存图片」
   （直存相册）与「编辑」（进 W3 编辑态）——不再一点按就跳编辑页。
3. **沉浸模式出口**：顶行「✕ 退出相册浏览」→ 回退态（会话级，不持久化）；回退态引导卡变
   「回到沉浸相册」一键再进。

**收尾修正 2（2026-09-29，Leo 反馈「浏览中要能快速切效果/配置，专业相机式快捷菜单」）**：

沉浸态底栏改为**快捷面板**：收起时是一枚「当前配置」摘要胶囊（`拍立得 · 标准 5s`，44dp，点按展开/收起），
展开为向上生长的面板（surface + hairline、顶角 20dp）：显影模式三 chip + **显影速度三 chip**
（慢洗 8s/标准 5s/快显 3s——速度首次移出 W4，浏览时动画长短随手调；重播即用新速度）。
摘要胶囊右侧保留 重播/拍照 两枚 44dp 图标。`SelectChip` 抽至 ui/theme 共享。

## 影响范围

- 代码：`data/AlbumRepository.kt`（新，含 ThumbCache）、`develop/GalleryPlayback.kt`（新）、
  `ui/pick/GalleryPager.kt`（新，GalleryPager/GalleryCard）、`ui/pick/PickScreen.kt`（双态重构）、
  `DarkroomViewModel.kt`（相册态/played/重播/openGalleryResult）、Manifest（运行时权限）。
- spec：01（US-18）、02（W1 双态线框）、03（AlbumPhoto/Repository、UiState 字段）、05（动效表
  相册卡行）、06（ADR-008）。
