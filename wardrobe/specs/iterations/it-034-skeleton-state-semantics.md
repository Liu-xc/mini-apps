# it-034 · 页面骨架与状态语义（走查 P1）

- **状态**：已实施（2026-09-24）
- **来源**：[2026-09-24 UI 走查报告](../../../reports/2026-09-24-wardrobe-ui-audit/衣橱UI走查报告-2026-09-24.pdf) · it-034 提案
- **关联**：US-34

## 背景与动机

详情/表单/回顾页存在三段式色阶接缝（浅绿状态栏带 × 白顶栏 × 浅绿内容）；
破坏性操作（删除穿搭、删除心愿、删除评论）与编辑同色并排且无确认；W9 回顾页
禁用态/演示模式态与可点态几乎无差别，比例条同色对不上标签。

## 用户故事

- **US-34a**：作为用户，页面顶部不再有横贯全屏的色阶接缝，顶栏与内容浑然一体。
- **US-34b**：作为用户，删除类操作有警示色与二次确认，不会误删数据。
- **US-34c**：作为用户，我能一眼分辨回顾页里「禁用」「演示模式不可用」「未解锁」
  与可点项，比例条色段能与文字标签对应。

## 验收标准

- W4/W5/W7/W9 状态栏着色跟随页面底色，无三段接缝。
- W7 顶栏删除、W10 心愿详情删除：警示色（红系）+ 执行前确认对话框；
  评论删除 × 点按先确认（或 snackbar 撤销）。
- W9：品类分布条同色系色阶 + 段内/就近直接标注；「生成年度衣橱长图」禁用态
  补解锁条件副文案且对比度达标；数据包导入/导出行演示模式下降透明、去箭头、
  行内「演示模式」徽标；衣柜提醒总开关关闭时 chips 降透明、选中态统一品牌绿。
- `./gradlew assembleDebug testDebugUnitTest` 通过。

## 影响范围

`ui/outfit/OutfitDetailScreen.kt`、`ui/wishlist/WishlistScreen.kt`、
`ui/components/CommentTimeline.kt`、`ui/recap/WardrobeRecapScreen.kt`、
`ui/recap/DataPackageSection.kt`、`ui/wardrobe/ItemEditScreen.kt`、
`ui/detail/ItemDetailScreen.kt`、theme/status bar 配色；spec 02 增交互注记。

## 验证记录（2026-09-24）

- `./gradlew testDebugUnitTest assembleDebug` BUILD SUCCESSFUL，单测 62 项 0 失败
  （与 it-033 基线一致）；`compileDebugKotlin` PASS。
- 状态栏（emulator-5554 像素采样 + uiautomator bounds 双通道）：
  - 根因=edge-to-edge + 根 Scaffold 与 TopAppBar 双重（W9 三层）status bar inset 叠加，
    状态栏背后恒为主题 Paper 浅绿；修法=四白底路由
    （ITEM_EDIT/ITEM_DETAIL/OUTFIT_DETAIL/RECAP）关根 Scaffold 顶部 inset，
    由各页 TopAppBar 吸收，白顶栏铺进状态栏；
  - 实测四页状态栏像素纯白 (255,255,255)、返回行 y=296（原 309–372 三层 inset）；
    搭配/衣橱 Tab 仍 (245,249,243) 浅绿，W10 原表现不变；深色模式自动跟随 surfaceContainer*。
- 删除警示：W7「删除这套穿搭？」、W10「删除「×」？」/「删除这套心愿穿搭？」、
  评论「删除这条评论？」四类二次确认**均已存在**（it-029 C8 起）——走查 C5 对评论属
  误报；本轮补 W7/W10 垃圾桶 error 红（实测 (179,38,30)）、W10 编辑↔删除间距 16dp（字形实测 40dp）。
- W9 语义三处：
  - 比例条按数量降序 6 档绿（#14543A→#BFE1CE），六段色值逐一命中，宽段段内白字直标
    （浅色档回落墨色保对比度）、下方全量文字行保留；
  - 年度长图解锁文案按 `WardrobeRecapCalculator.hasWearData = wearCount > 0` 生成
    （「{year} 年打卡 ≥ 1 次后解锁…」），禁用文字色 #55605A 对纸白 ~6.2:1；
  - 数据包行 demo 态 alpha 0.5（像素反推验证）+ 无箭头 + 行内「演示模式」徽标；
    提醒 chips 选中=primary 容器（实测 (66,158,104)），总开关关→行 alpha 0.45 +
    `enabled=false`（dump 验证），chipsActive 收紧为「开关开且非演示」。
- 装机往返：开/关演示模式完整走查一遍后模拟器已恢复（演示开、提醒关）。
- 过程中发现并修正 4 处 CJK 误码（打扖/穿褡），全量扫描 6 文件无残留。
