# it-012 — 相册优先的信息架构重构

状态：**实施完成（2026-09-29）**
日期：2026-09-29

## 背景与动机

Leo 对 it-011 后整体动线的批评：首页表单化按钮太多（选模式/从相册挑一张/现在拍一张/回到沉浸相册……），
「进去的目的就是看照片」；显影模式只是动画样式不该是首页主角；动画完不该跳成片页；
整个交互动线混乱。

## 新动线

```
启动 → W1 相册网格（小图墙；顶行仅 拍照 + 设置）
  │ 点缩略图
  ▼
画册模式（独立屏）：从该页起翻页；首见照片跑显影动画；动画完停留（不跳页）
  │ 点成品卡            │ 底部快捷面板（动画模式/速度）+ 重播
  ▼
大图查看器（存图片 / 编辑）
  │ 编辑                          │ 存图片
  ▼                              ▼
W3 成片（查看态 ⇄ 编辑态）      相册落库
```

拍照（顶行相机）走经典路径：拍一张 → 显影台 → 成片（保留仪式）；显影台不再出现在相册主线上。

## 变更清单

- **W1 = 相册网格**：LazyVerticalGrid（Adaptive 108dp）+ AlbumThumb（256px 缩略、pressScale）；
  未授权/空相册 = 安静引导态（图形 + 单按钮，非表单）；已授权记忆（重装后 checkSelfPermission 直载）。
  原 主按钮/模式区/样片区/引导卡 全部移除；Photo Picker 入口退役（网格即相册）。
- **画册模式独立屏**（`Screen.PAGER` + `AlbumPagerScreen`）：顶行 返回 + 「画册」+ 页码；
  快捷面板（动画模式/速度）与重播随迁；`albumInitialPage` 定起始页。
- **返回栈**：画册 → 网格；成片 → 从画册进的回画册（`resultFromPager`），否则回网格。
- **动画语义不变**：首见播放、played 集不重复、点按跳过、点成品=大图、重播手动。
- ThumbCache 双尺寸键（256/1080 互不污染，36 条 LRU）。
- GalleryCard/DevelopCard/PhotoViewer 等复用不动。

## 验证记录

- `testDebugUnitTest assembleDebug installDebug` 通过，67 测全绿。
- AVD 走查仍受并行会话占用所限待补；实机以 Leo 体验为准（重点：网格→画册→大图→编辑→返回栈）。
- 版本 0.5.0（versionCode 12，IA 重构属 minor）。

## 影响范围

- 代码：`ui/pick/PickScreen.kt`（重写为网格）、`ui/pick/AlbumPagerScreen.kt`（新）、
  `DarkroomViewModel.kt`（PAGER/albumInitialPage/resultFromPager/backToPick 动线）、
  `DarkroomApp.kt`（PAGER 分支）、`data/AlbumRepository.kt`（ThumbCache 双尺寸）。
- spec：02（W1/画册线框重写）、01（US-18 AC 更新）、03（UiState 新字段）。
