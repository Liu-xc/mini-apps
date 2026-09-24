# it-039 · 衣橱界面文字与细节收尾

- **状态**：已实现，验收通过
- **来源**：[第三轮走查报告](../../../reports/2026-09-24-wardrobe-ui-audit-r3/衣橱UI第三轮走查报告-2026-09-24.pdf) C5–C10；W1 最新构建截图见 [本轮审计记录](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/README.md)
- **关联**：US-39

## 背景与动机

新演示语料让 W1 槽位名暴露出 it-033 动态缩字号/最多两行策略的边界：窄槽位会把一个汉字孤立到第二行，长包名仍省略。W10 表单和其他细节还有描边色阶、顶部节奏、文案格式与横滑提示不一致，以及少量 FAB 阴影、评论删除符号和愿望组合定位提示等可读性问题。

最新包实机复核仍可见 W10 约 73dp 的顶栏空档、默认主题紫灰描边；表单标签建议确实能横滑，但起始位置没有行尾渐隐提示。点名称框唤起键盘后，固定保存栏仍处于键盘上方，空名称时按预期禁用。

本迭代延续衣橱时装编辑风与现有 44/48dp 触控规范；长名称可省略显示，但无障碍描述保留完整名称。

## 用户故事

- **US-39a**：作为用户，我在搭配页窄槽位里能快速辨认单品名，不会看到落单汉字；需要时仍能通过辅助技术读取完整名称。
- **US-39b**：作为用户，我在心愿录入、筛选和查看组合时能理解入口、滚动方向和控件状态，页面边距、描边与文案风格保持一致。

## 验收标准

- W1 槽位名称不再折出孤字：可读字号内单行显示，超出后用尾部省略号；完整名称通过 content description 暴露；`n/n` 可点翻页与移除入口保持不变。
- W10 输入框/分段控件不再出现 M3 紫灰描边残色；顶栏与内容边距遵循 20dp 屏幕节奏；表单标点和空格统一中文全角习惯，内部实现术语改为用户语言。
- 共用 `TagInput` 的已选标签与预设建议两行接入现有 `FadingScrollRow`，仅在确实可横滑且尚未滑到尽头时显示 28dp 渐隐；覆盖 W4、W10 与穿搭标签编辑场景，固定操作不被遮住。
- 复核并处理第三轮 C10 低风险细节：W3 FAB 阴影（若 it-037 移除则不再单独处理）、W5/W7 评论删除符号、发送操作对比度、W1 心愿卡可定位提示。已经由 it-037/038 覆盖的项不重复实现。
- 更新 `01-user-stories.md`、`02-wireframes.md`、`05-design-system.md`；有重要交互路径变化时同步 ADR/决策记录。
- 模拟器覆盖 W1 长名（帽/包/衣物）、W10 表单空/已填、标签行起始/中段/尽头、深浅色模式；检查截断、热区和组件位置。

## 影响范围

`ui/components/SlotGrid.kt` · `ui/wishlist/WishlistScreen.kt` · `ui/components/Tags.kt` · `ui/components/ScrollFade.kt`（复用）· `ui/components/CommentTimeline.kt` · `ui/wardrobe/WardrobeScreen.kt` · `ui/wardrobe/ItemEditScreen.kt` · `ui/records/OutfitDetailScreen.kt` · `specs/01-user-stories.md` · `specs/02-wireframes.md` · `specs/05-design-system.md`。

## 验证记录 · 2026-09-25

- `./gradlew testDebugUnitTest assembleDebug` 和 `./gradlew installDebug` 均通过；以安装后的最新包检查 W1、W4、W10 表单空态/键盘态及浅深色。
- W1 槽位名称显示单行尾部省略；uiautomator 节点保留完整衣物名（如「黑色棒球帽」「锈红色灯芯绒夹克」）和分页可访问描述，不见孤字折行。
- W4/W10 可见文案改为「商品图」「名称（必填）」「价格（可选，元）」「品类（必选）」「颜色（可选）」「描述（可选，用于搭配文案）」；商品图 a11y 描述仍保留「可选」。空名称保存按钮置灰；键盘弹出时动作栏在键盘上方仍可见。输入临时名 `wardrobe_demo` 后按钮转为主色可用态；关闭表单未保存。
- TagInput 复用渐隐组件，W4/W10 标签行在右缘显示淡出提示；W10 28dp 渐隐按横向滚动状态变化。评论删除更换为 DeleteOutline，发送图标的无障碍描述为「发送评论」。
- 截图：[W1 槽位名称](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W1-wardrobe.png)、[W4 标签录入](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W4-tags.png)、[W10 表单](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W10-form.png)、[W10 键盘空态](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W10-keyboard.png)、[W10 键盘填名](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W10-form-filled-keyboard.png)、[W10 深色表单](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W10-form-dark.png)。
