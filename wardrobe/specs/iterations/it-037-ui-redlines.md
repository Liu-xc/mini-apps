# it-037 · 衣橱 UI 红线修复

- **状态**：已实现，验收通过
- **来源**：[第三轮走查报告](../../../reports/2026-09-24-wardrobe-ui-audit-r3/衣橱UI第三轮走查报告-2026-09-24.pdf) C1–C2；最新构建复核见 [本轮审计记录](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/README.md)
- **关联**：US-37

## 背景与动机

最新源码安装到 `emulator-5554` 后复核 W1/W2/W3：底部导航选中态仍同时使用背景 pill、绿色图标和绿色文字；W2 当前角色行同时使用浅绿底、勾号和「使用中」文字；W3 的新增入口是叠在网格上的 FAB，筛选/长名称组合下会占用卡片内容区域。衣橱列表当前已预留 96dp 底距，因此单纯继续加大 `contentPadding.bottom` 不能保证当前视口中的卡片不被盖住。

界面保留已有角色切换、单品新增和底部导航能力，改为用更少的状态线索表达选中态，并为新增入口安排不覆盖内容的固定位置。

## 用户故事

- **US-37a**：作为用户，我能一眼看出当前所在 Tab 与当前角色，但选中状态不重复堆叠图标、色块和状态文字。
- **US-37b**：作为用户，我在衣橱任意筛选和滚动位置查看单品时，新增入口不会遮住单品名称、颜色或标签，并能随时一键新增。

## 验收标准

- 底部导航选中态最多使用两种状态线索：保留选中 pill 与图标强调，三项文字颜色保持一致；三项仍可独立点击，热区不低于 48dp。
- W2 当前角色行保留浅底与「使用中」文字提示，移除勾号；当前/非当前角色仍清晰，角色切换行为不变。
- W3 移除覆盖网格的 FAB，改为固定在底部导航上方的「＋ 添加衣物」入口（触控高度 ≥48dp）。网格内容为其让出布局空间；任何可滚动位置和筛选结果中，CTA 命中区均不覆盖卡片标题、颜色或标签。
- 衣橱为空、筛选为空、单列末行、两列末行均保留明确状态；新增仍一步进入 W4。
- 更新 `01-user-stories.md`、`02-wireframes.md` 与必要的 `05-design-system.md` 交互注记，使规格与实现一致。
- 模拟器验收：全量/类别/标签筛选，列表顶部/中段/末尾；点新增进入 W4；Tab 切换；W1 打开 W2 并切换角色。检查各状态均无覆盖和回归。

## 影响范围

`MainActivity.kt`（底部导航）· `ui/outfit/PersonSheet.kt` · `ui/wardrobe/WardrobeScreen.kt` · `specs/01-user-stories.md` · `specs/02-wireframes.md` · `specs/05-design-system.md`。

## 验证记录 · 2026-09-25

- `./gradlew testDebugUnitTest assembleDebug`：BUILD SUCCESSFUL；66 项任务完成（本次检查均为 UP-TO-DATE）。`./gradlew installDebug`：BUILD SUCCESSFUL，最新包已装至 `emulator-5554`。
- 模拟器 W2 实测 Leo→Mia→Leo，切换成功并恢复 Leo。当前行有浅底及「使用中」，无勾号；底部 Tab 的文字颜色一致，当前项仍有 pill 与图标状态。
- W3 顶/滚动/空筛选状态检查：列表滚动时底部 CTA 固定在导航上方；「帽子 + #复古」结果为 0 件，空状态与「＋ 添加衣物」仍可见。点击 CTA 进入 W4，未保存数据。
- 关键截图：[W1 长名称](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W1-wardrobe.png)、[W2 角色](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W2-roles.png)、[W3 衣橱](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W3-closet.png)、[W3 空筛选](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W3-filtered-empty.png)、[W4 到达](../../../reports/2026-09-24-wardrobe-ui-audit-r4-proposal/assets/final-W4-tags.png)。
