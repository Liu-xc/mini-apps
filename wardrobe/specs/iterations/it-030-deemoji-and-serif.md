# it-030 · 去 emoji 化与字体纪律

- **状态**：实施中（2026-09-23，Leo 对 UI 审查报告拍板「实施」）
- **来源**：审查报告 C3 / C4（DESIGN.md §2.5、§5.2、§2.3）

## 背景与动机

DESIGN.md v1.0（2026-09-21）确立「功能图标一律 Material Icons，emoji 不得作为功能图标」。
审查发现 emoji 图标系统性存在（多数为 it-019 心愿域引入，早于基线定稿）；同时卡片实体名
用粗体 Sans（titleMedium/titleSmall），违反 §2.3「Title/实体名走衬线」。

**范围裁决**：品类 emoji 圆 chip（W3 品类 Tab、W4 品类 chips）是 it-012 的 spec 决策，
评审建议按「内容插画 vs Material Icons」专项裁决——**本轮不动**，待 Leo 拍板。

**评审误报更正**：「🎲 随机一套」实为 Material Casino 图标（小尺寸形似 emoji），无需修改；
W10 心愿卡「无价格展示位」系测试数据把价格拼进名称所致（实况：price 字段有值即展示），不修。

## 用户故事

- **US-30a**：作为用户，我在任何页面看到的功能入口图标都是 Material 线性图标（愿望星标、
  复制、收进想买、拍照选图、心愿穿搭段等），不再出现彩色 emoji 图标。
- **US-30b**：作为用户，衣物卡、心愿卡、穿搭卡上的实体名以衬线呈现，与详情页一致（print 母题）。

## 验收标准

- Given W3 标题行心愿入口，Then 为 Material Star 图标（icon-only 入口带 contentDescription）
- Given W1 混入心愿 / 存为心愿 / 复制长图，W7 复制长图，W10 各段与表单，Then 全部图标为 Material Icons
- Given W4/W10 选照片入口，Then 相机语义由 Material PhotoCamera 图标承载（文字去 📷 前缀）
- Given W3 衣物卡 / W10 心愿卡 / W8 穿搭卡实体名，Then 衬线 Title 层级（titleLarge，单行省略）
- 全仓 `ui/` 目录不再有 emoji 作为唯一功能图标（文本提示语中的 emoji 一并清理）；构建 + 单测绿

## 影响范围

`WardrobeScreen.kt`、`OutfitScreen.kt`、`SlotGrid.kt`（愿望占位/角标）、`WishlistScreen.kt`、
`WardrobeRecapScreen.kt`、`ItemEditScreen.kt`、`ExportSheet.kt`、`RecordsScreen.kt`；纯视觉，不动逻辑。
