# it-033 · 触控与截断专项（走查 P1 高频先修）

- **状态**：已实施（2026-09-24）
- **来源**：[2026-09-24 UI 走查报告](../../../reports/2026-09-24-wardrobe-ui-audit/衣橱UI走查报告-2026-09-24.pdf) · it-033 提案
- **关联**：US-33

## 背景与动机

走查实测（uiautomator bounds ÷ 420dp 密度）发现触控目标系统性偏小：标题行图标按钮
18dp、品类 chips 19dp、标签 chips 15dp、评论删除 × 14dp、W8 分页胶囊 15dp，均低于
44dp 基线；同时 it-032 新语料的长中文名让 W1 名称条 4/6 截断、W3 卡标题截断，
识别度不足。

## 用户故事

- **US-33a**：作为用户，我在衣橱页、表单页、详情页点按任何图标/chips/分页/删除控件
  都能一次点中，不因目标过小而误触。
- **US-33b**：作为用户，W1 槽位名称条与 W3 卡标题不再出现不可读的截断名称。

## 验收标准

- 标题行图标按钮（回顾📊、心愿🌟）、卡片 ⋮ 菜单触控区 ≥44dp。
- W4 品类 chips、W5 标签 chips 高度 ≥44dp。
- W8 分页胶囊两侧翻页热区 ≥44dp（整半边可点）。
- 评论删除 × 热区 ≥44dp。
- W1 名称条名称 maxLines=2（或等效缩字号）不截断；n/n 序号角标保留 it-031 可点
  翻页交互，补 a11y 描述「第 n 件，共 m 件，点按切换」。
- W3 卡标题 maxLines=2 不截断。
- `./gradlew assembleDebug testDebugUnitTest` 通过。

## 影响范围

`ui/components/SlotGrid.kt`、`ui/wardrobe/WardrobeScreen.kt`、
`ui/wardrobe/ItemEditScreen.kt`、`ui/detail/ItemDetailScreen.kt`、
`ui/records/*`、`ui/components/CommentTimeline.kt`、`ui/components/Tags.kt`；
spec 02 增交互注记、spec 05 增 44dp 触控基线。

## 验证记录（2026-09-24）

- `./gradlew compileDebugKotlin` PASS；`./gradlew testDebugUnitTest assembleDebug`
  BUILD SUCCESSFUL，单测 62 项 0 失败。
- 装机实测（emulator-5554，uiautomator dump + 截图）：
  - W1 名称条三段式生效：「藏蓝色防雨派克外套」「海军条纹针织Polo」「浅棕色帆布托特包」
    自动两行完整显示，鞋槽仍在首屏（y1689–1832 视口内），单行时高度与 it-031 一致；
  - n/n 角标 a11y =「第 2 件，共 6 件，点按切换」✓；✕ 移除节点 126×126px = 48×48dp ✓；
  - W3 📊/🌟/⋮ 节点 126×126px = 48×48dp；卡标题两行 minLines 生效；
  - W4 品类 chips 48dp、行距 8dp；W8 分页左右半各 48×48dp（‹ 上一张 / › 下一张）。
- **口径勘定**：走查报告的 18dp/14dp 等为 content-desc/字形子节点 bounds，真实原热区
  为 32/32/28/44dp；本轮全部改为显式布局尺寸 ≥48dp（W5 标签 44dp、W1 ✕ 宽 28dp 见
  spec 02 it-033 注记的取舍说明）。
- 已知取舍（详见 spec 02 注记与实施偏差记录）：✕ 宽 28dp（名称列预算优先）、
  ✕ 热区向上探入照片 28×24dp 透明带、W8 胶囊中心计数改纯展示。
