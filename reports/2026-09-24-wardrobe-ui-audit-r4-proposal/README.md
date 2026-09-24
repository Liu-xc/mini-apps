# 衣橱 UI 走查与优化记录 · 2026-09-24–25

本轮复核最新 `wardrobe` 源码构建，并在 `emulator-5554` 安装后重新检查关键路径。完整全页证据与三轮问题归档见 [第三轮 PDF 报告](../2026-09-24-wardrobe-ui-audit-r3/衣橱UI第三轮走查报告-2026-09-24.pdf)。本轮以剩余问题为范围，不重复记录已经修复的触控热区、空态、导出弹层和深色底色问题。

## 当前复核

- `./gradlew installDebug`：BUILD SUCCESSFUL；61 个任务中 60 项为 UP-TO-DATE，当前源码已安装。
- W1 槽位分页：点击外套 `3/4` 后显示 `4/4`，页码与单品同步更新；角色弹层打开后切换 Leo→Mia→Leo 成功，复核后已恢复 Leo。
- W2 角色状态仍有行底色、勾号、「使用中」三重表达；底部 Tab 仍有选中 pill、绿色图标和绿色文字三重表达。
- W1 长名称（如「黑色罗纹高领衫」「鼠尾草绿尼龙双肩包」）仍能出现孤字换行或省略，和全名无损的旧验收表述不符。
- W3 在「全部 + #复古」下显示 5 件；UI dump 中末行卡片文字节点 `[585,1863][990,2007]` 与 FAB `[922,1969][985,2032]` 相交 `63×38px`。当前实现已有 96dp 列表底距，继续单纯增大底距不能消除中间滚动位置的浮层遮挡，建议改为不覆盖内容的固定 CTA。
- W10 当前仍有约 73dp 顶部空档（状态栏底 y=110px，标题/返回行 y≈302px）；分段描边保留 Material 默认紫灰 `#79747E`。心愿录入表单的括号/星号格式混用，标签建议可横滑但没有渐隐提示；滑到行尾后才出现「春、夏、秋、冬、早春、早秋、自定义」。点名称框唤起键盘后固定保存栏仍可见，空名称时正确禁用。
- 对比度复算：浅色白字 / `#429E68` = `3.32:1`；`#429E68` / `#DDF0E4` = `2.79:1`；白字 / `#1D6845` = `6.73:1`。`#808D82` / `#F5F9F3` = `3.26:1`，符合次级文字 3:1 底线；本轮不把它误报为违反该底线，只按实际使用角色检查更高要求。
- 未对业务数据做增删；角色与 W1 槽位已恢复原状态，W3 标签筛选已清除。

## 共性问题与建议拆分

| 级别 | 问题 | 建议迭代 |
|---|---|---|
| P0 | 底部 Tab / 角色当前态重复表达选中状态 | [it-037](../../wardrobe/specs/iterations/it-037-ui-redlines.md) |
| P0 | W3 FAB 覆盖可滚动卡片区域 | [it-037](../../wardrobe/specs/iterations/it-037-ui-redlines.md) |
| P1 | 浅色主题白字/品牌绿组合对比度不足；若干弱化文字低于对应 token 阈值 | [it-038](../../wardrobe/specs/iterations/it-038-contrast-tokens.md) |
| P1 | W1 窄槽位长名称孤字折行/省略不一致 | [it-039](../../wardrobe/specs/iterations/it-039-ui-layout-polish.md) |
| P2 | W10 描边、顶栏节奏、表单文案和标签滚动提示；少量符号/阴影/定位线索 | [it-039](../../wardrobe/specs/iterations/it-039-ui-layout-polish.md) |

## 实施前基线截图

- W1 搭配与长名：[current-W1-match.png](assets/current-W1-match.png)
- W2 角色弹层：[current-W2-roles.png](assets/current-W2-roles.png)
- W3 标签筛选：[current-W3-filtered.png](assets/current-W3-filtered.png)
- W10 心愿页：[current-W10.png](assets/current-W10.png)
- W10 新建表单：[current-W10-form.png](assets/current-W10-form.png)
- W10 标签横滑后：[current-W10-form-tags-scrolled.png](assets/current-W10-form-tags-scrolled.png)
- W10 键盘态：[current-W10-keyboard.png](assets/current-W10-keyboard.png)

## it-037 布局提案草图

```
┌ 衣橱 · Leo              回顾  心愿 ┐
│ 全部  上装  外套  下装 …   筛选   │
│ ┌────────────┐ ┌────────────┐    │
│ │ 单品照片    │ │ 单品照片    │    │
│ │ 名称 / 标签 │ │ 名称 / 标签 │    │ ← 内容滚动区不与新增入口重叠
│ └────────────┘ └────────────┘    │
│          …                         │
├───────────────────────────────────┤
│           ＋ 添加衣物              │ ← 固定 CTA，≥48dp
├───────────────────────────────────┤
│       搭配          记录       衣橱 │
└───────────────────────────────────┘
```

底部 Tab 仅保留 pill + 图标作为选中提示；角色行仅保留浅底 +「使用中」文字。

### 选中态与长名称

```
底部 Tab（当前）     →  底部 Tab（提案）
pill + 绿图标 + 绿字  →  pill + 绿图标 + 中性字

当前角色（当前）     →  当前角色（提案）
浅绿底 + ✓ + 使用中  →  浅绿底 + 使用中

窄槽名称（当前）     →  窄槽名称（提案）
黑色罗纹高领
衫                  →  黑色罗纹高…  [完整名供读屏]
```

灰盒方案按实机屏幕比例绘制，并人工复核了文字出界、控件重叠与徽标间距：

- [四页改版缩略图](assets/wf-proposal-contact-sheet.png)
- [W3 衣橱：固定新增入口](assets/wf-proposal-W3-closet.png)
- [W2 角色行：浅底 + 状态文字](assets/wf-proposal-W2-roles.png)
- [W1 名称栏：单行省略](assets/wf-proposal-W1-namebar.png)
- [W10 表单：统一文案与横滑提示](assets/wf-proposal-W10-form.png)

当前实机截图与改版灰盒的逐页对照：

- [四页现状 / 提案对照拼图](assets/wf-current-vs-proposal-contact-sheet.png)
- [W3 衣橱对照](assets/compare-W3-current-proposal.png)
- [W2 角色对照](assets/compare-W2-current-proposal.png)
- [W1 名称栏对照](assets/compare-W1-current-proposal.png)
- [W10 心愿表单对照](assets/compare-W10-current-proposal.png)

重画方式：`python3 reports/2026-09-24-wardrobe-ui-audit-r4-proposal/draw_proposal_wireframes.py`。绘制使用仓库 UI 审计技能的 `wireframe_lib.py`，符号由几何形状自绘。

## 实施后源码落点

确认后已按三份迭代提案实施，并同步更新用户故事、交互线框、设计系统与变更日志：

- [底部导航选中态](../../wardrobe/app/src/main/java/com/leo/wardrobe/MainActivity.kt)；[W3 固定新增按钮](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/wardrobe/WardrobeScreen.kt)；[W2 角色选中行](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/outfit/PersonSheet.kt)。
- [W1 槽位名称](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/components/SlotGrid.kt)改为单行省略、无障碍读取完整名称。
- [语义强调色](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/theme/DesignTokens.kt)与[浅深主题映射](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/theme/WardrobeTheme.kt)分开图形色、文字色及实心按钮色。
- [W10 表单](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/wishlist/WishlistScreen.kt)、共用 [TagInput](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/components/Tags.kt)和[自适应渐隐](../../wardrobe/app/src/main/java/com/leo/wardrobe/ui/components/ScrollFade.kt)已完成。

## 实施后复验

- `./gradlew testDebugUnitTest assembleDebug`：BUILD SUCCESSFUL；`./gradlew installDebug`：BUILD SUCCESSFUL，已安装最新 APK。
- 模拟器交互：W2 Leo→Mia→Leo；W3 全列表滚动及「帽子 + #复古」筛选空态；固定新增 CTA 进入 W4；W10 表单、标签行、键盘态和深色主题。未增删演示数据，当前角色已恢复 Leo。
- 浅色主色 `#1D6845` 对白字 6.73:1；浅色次级色 `#747F75` 对淡绿容器最低 3.51:1；深色强调绿 `#74C790` 对墨绿卡片 7.92:1。完整组合见 [it-038 验证记录](../../wardrobe/specs/iterations/it-038-contrast-tokens.md)。
- 新截图：[W1 槽位](assets/final-W1-wardrobe.png)、[W2 角色](assets/final-W2-roles.png)、[W3 衣橱](assets/final-W3-closet.png)、[W3 空筛选](assets/final-W3-filtered-empty.png)、[W4 标签](assets/final-W4-tags.png)、[W10 心愿](assets/final-W10-wishlist.png)、[W10 表单空态](assets/final-W10-form.png)、[W10 键盘空态](assets/final-W10-keyboard.png)、[W10 键盘填名](assets/final-W10-form-filled-keyboard.png)、[W10 深色表单](assets/final-W10-form-dark.png)。

迭代提案与验收记录：[it-037](../../wardrobe/specs/iterations/it-037-ui-redlines.md) · [it-038](../../wardrobe/specs/iterations/it-038-contrast-tokens.md) · [it-039](../../wardrobe/specs/iterations/it-039-ui-layout-polish.md)。
