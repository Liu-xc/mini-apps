# it-029 · 止血：P0 崩溃修复 + 评论删除确认 + 导航/弹层色阶

- **状态**：实施中（2026-09-23，Leo 对 UI 审查报告拍板「实施」）
- **来源**：[reports/2026-09-23-wardrobe-ui-audit](../../../reports/2026-09-23-wardrobe-ui-audit/) 共性问题 C1 / C8 / C2

## 背景与动机

UI 审查（2026-09-23）发现三项须立即修复的问题：

1. **P0 崩溃（C1）**：任一格位翻到愿望卡页（混入心愿开，页码 = 列表末位）后关闭「混入心愿」，
   `items` 收缩而 `pagerState.currentPage` 未回卷，重组期 `items[page]` 越界 →
   `IndexOutOfBoundsException`（logcat 实录，SlotGrid.kt:114，it-019 引入）。
2. **P1 违红线（C8）**：W5/W7 评论删除 `onDelete` 直删，无二次确认亦无撤销，违 DESIGN.md §5.7
   （对照：单品/穿搭/角色删除均有确认弹窗）。
3. **P1 跑色（C2）**：底部导航与底部弹层呈 Material 默认淡紫（#F3EDF7 族）——
   `WardrobeTheme` 未覆盖 M3 `surfaceContainer*` 色阶，跌回基线默认值，跳出黑白绿纸感家族
   （评审两批独立像素采样证实）。

## 用户故事

- **US-29a**：作为用户，我在愿望卡页关闭「混入心愿」时，应用不崩溃，格位回到范围内的页。
- **US-29b**：作为用户，我误触评论删除钮时，弹出确认；确认才删除。
- **US-29c**：作为用户，我看到底部导航与所有底部弹层都是纸感家族色，不出现紫灰。

## 验收标准

- Given 鞋格停在第 3/3 页（含愿望卡），When 关闭混入心愿，Then 不崩溃且该格回卷到第 1 页
- Given 任一格 items 收缩（含移除格位品类），Then 无越界崩溃
- Given 点击评论 ✕，Then 出现确认对话框（「删除这条评论？」），确认后删除、取消不动
- Given 浅色/深色模式，Then NavigationBar 与 ModalBottomSheet 底色 = surface token 家族（白 / 墨绿纸面）
- 构建 + 单测全绿；模拟器执行崩溃场景复验无 FATAL

## 影响范围

`SlotGrid.kt`（越界防御 + 页码回卷）、`CommentTimeline.kt`（确认对话框，W5/W7 共用）、
`WardrobeTheme.kt`（surfaceContainer 五档）；不动数据层。
