# it-030 · 去 emoji 化与字体纪律

- **状态**：已完成（2026-09-23，Leo 对 UI 审查报告拍板「实施」；走查全过+补遗清零、单测绿，提交 605d83b+补遗）
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
补遗（2026-09-23 验证时清出）：`OutfitDetailScreen.kt`、`ItemDetailScreen.kt`（💬 评论标题）、
`DataPackageSection.kt`（📦/⚠ 前缀）、`WishlistScreen.kt`（📋 复制长图按钮 = C3 漏项、🔗 链接前缀）。

## 验证记录（2026-09-23）

- 单测：`./gradlew assembleDebug testDebugUnitTest` 绿（含补遗后复跑）。
- 模拟器截图对照（reports/2026-09-23-wardrobe-ui-audit/v31-*.png）：W1「混入心愿」Material
  Star、「随机一套」骰子图标；W8「录入成品图」+ 图标；记录页/回顾页标题呈衬线 Title
  （穿搭记录 · Leo / 衣橱回顾 · Leo）；衣物卡实体名（牛津纺衬衫等）单行省略生效。
- **补遗清零**：验收「文本提示语 emoji 一并清理」复核时清出 6 处漏网——Wishlist「📋 复制长图」
  按钮（C3 明确要求的带文字 emoji 按钮，换 Material ContentCopy+文字，与 W7 同款）、
  「🔗 host ↗」去 🔗、W5/W7「💬 评论（N）」×2 去 💬（模拟器复验：评论（0）前无 emoji）、
  数据包「📦 文件名」「⚠ N 件…」去前缀（琥珀色 SymUpdate 已承载警示语义）。📋 按钮所在
  心愿穿搭弹层当前无数据（心愿穿搭 0），以编译+同款已验证惯用法代码复核，未实机点检。
- **保留项（非违例，备案）**：PersonSheet 头像选择 emoji（内容选择器而非功能图标）、
  ✓/✗/✕ 符号（it-026 spec 决策的差异行/弹窗符号分层）、data/domain 层 🙂 默认头像值
  （内容非 ui/）、export/OutfitImageComposer「🌟想买」（导出图像素内容，非 App UI 图标）、
  品类 emoji chips（it-012 spec 决策，待 Leo 专项裁决，本轮不动）。
- 提交切分备注：it-031 的 C7/C10 两处文件（OutfitDetailScreen/WardrobeRecapScreen）编辑
  在本提交时被一并扫入（605d83b），已在 it-031 验证记录备案。
