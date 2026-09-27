# it-002 — 显影 · 稳定性与卡面几何

状态：**提案待确认（2026-09-27，源自首轮 UI 审查）**
日期：2026-09-27

## 背景与动机

2026-09-27 全页面 UI 审查（交付 `reports/darkroom-ui-audit/`，报告 D1–D3 / C1–C2）发现：

1. **P0（已随审查落盘 hotfix）**：`AndroidManifest.xml` 缺 `VIBRATE`，定影落定 `Haptics.confirm`
   抛 `SecurityException` 必崩——已补权限并全流程回归，本迭代做回归收口与防回归（AC1）。
2. **P1 顶行安全区（C1）**：全 App 无 statusBars insets 处理，四页页头嵌进状态栏窗口
   （模拟器实测窗口高 128px）：`⚙`/`←` 控件中心 y=111 点击无响应、y≥130 才生效；页头视觉顶格。
3. **P1 卡面几何（C2）**：`CardLayout.solve(width)` 只吃宽度不吃可用高度——
   显影台 weight 槽高只给 1028px（需 1158px），白底被裁、日期章 32px 与水印整条冲出白框、
   撞上进度读数行；成片页标题域（右界 0.60w）与日期章域（左界 0.58w）交叉 +
   日期右对齐绘制不夹断 → `Summer 2026` 与 `2026 09 27` 字形重叠。

三者都是「结构破损级」视觉缺陷且每天可见，先于功能收口修（对应报告落地拆分 O1–O3）。

## 涉及的用户故事

- 既有 **US-4 成片卡片**（AC4 三处渲染一致——本次修的是*三处一起错的布局解*）；
  新增 **US-9 安全区页头**（四页页头统一让出系统栏、触控目标完整可点）。
  实施时同步 `specs/01-user-stories.md`。

## 用户故事

- **US-9 安全区页头**：W1–W4 页头（品牌行 / 返回 / 标题 / ⚙）整体让出状态栏与手势区；
  44dp 触控目标中心点必可点；页头与状态栏图标不再同区。
- **US-4（补强）卡面几何自适应**：
  - 卡片宽 = min(可用宽, 可用高 ÷ 1.2)，出纸/落定全程不越出容器；
  - 签名区两域互斥：`title.right ≤ stamp.left`（建议 0.52w / 0.54w），日期章绘制夹断到域内，
    标题超长走既有 ellipsis；水印恒在白框内。

## 验收标准

- AC1 P0 防回归：单测（或 lint 规则）保证 manifest 含 `VIBRATE`；定影落定、导出完成触感实测不崩。
- AC2 触摸实测：四页顶行控件**中心点** `input tap` 均生效（对照现状 y=111 死区）；
  `dumpsys window` 确认页头内容 y 起点 > 状态栏窗口高。
- AC3 像素断言：最矮可用槽下显影台整卡（含签名区）完整在白框内，黑底上零残留；
  成片页标题与日期章任意字符串组合无字形重叠。
- AC4 一致性：`CardLayout` 单测覆盖新约束（`height ≤ maxH`、`title.right ≤ stamp.left`、
  归一化路径 `solve(1f)` 行为不回归）；预览/位图/视频三端截图对表不变形。
- AC5 构建 + 既有五套 JUnit 全绿；DESIGN.md §2.5 对表走查。

## 技术方案草案

- **O1 收口（已落盘）**：`AndroidManifest.xml` +`<uses-permission android:name="android.permission.VIBRATE"/>`
  （normal 权限，不破「零危险权限」策略）；本迭代补 AC1 防回归测试。
- **O2 insets**：四页根布局统一 `Modifier.windowInsetsPadding(WindowInsets.statusBars)`
  （或 DarkroomApp 层统一包一层），页头下推；不引第三方、不动导航结构。
  涉及 `ui/pick/PickScreen.kt`、`ui/develop/DevelopScreen.kt`、`ui/result/ResultScreen.kt`、`ui/settings/SettingsScreen.kt`。
- **O3 几何**：`CardLayout.solve(width, maxH = Float.MAX_VALUE)`——`width = min(width, maxH / aspect)`；
  `title.right`、`stamp.left` 改互斥常量；日期绘制 `x = max(stamp.left, right - textW)` 夹断。
  调用点：`DevelopScreen:134`（传 BoxWithConstraints 实际可用高）、`DevelopCard:65`、
  `PhotoCardPainter:65/87`、`VideoExporter:92`（归一化 `solve(1f)` 走 maxH=∞ 分支保持现行为）。
- 单测：`CardLayoutTest` 扩展（AC4）；不改 DevelopSpec/显影曲线。

## 里程碑

- M1 O1 回归收口 + O2 insets（独立可验，先落）
- M2 O3 卡面几何 + 三端截图对表 + 单测

## 影响范围

- `darkroom/app`（manifest、四页根布局、card 包）、`specs/01-user-stories.md`（US-9、US-4 补强）、
  不触碰 libs 与其他应用；完成后回填本文件验证记录 + CHANGELOG 一行。

## 待确认点

1. 页头方案：每页各自加 insets padding（改动分散、可控）还是 DarkroomApp 统一包裹（一次改完、但
   会影响四页现有根布局）？建议**每页统一 helper**（`Modifier.pageTopInsets()`）折中。
2. O3 签名域比例 0.52w/0.54w 是否接受（现状 0.60/0.58 交叠 2%；改后标题可用宽略缩）？
3. P0 补丁已随审查落盘，是否随本迭代一并提交（与 AC1 防回归同 commit）？

（以上三点按提案默认执行：①每页统一 helper——落地为 `ui/PageInsets.kt` 的 `Modifier.pageInsets()`
（statusBars ∪ navigationBars，顶+底一起解决，AC3 手势条遮挡顺带消）；②0.52/0.54 采纳；
③P0 补丁随本迭代提交。）

## 验证记录

2026-09-27 实施完成，AC 全过（模拟器 wardrobe_test，最新源码构建）：

- **AC1**：`ManifestGuardTest` 断言 manifest 含 `VIBRATE`（1 测试）；定影落定/导出完成触感实测不崩
  （SLOW 全流程 + 视频导出回归通过）。
- **AC2**：顶行控件中心点实测——⚙ 中心 y 由 111（状态栏窗口 128px 内死区）→ **239**（安全区下），
  `input tap 969,239` W1→W4 生效；W4 返回中心 `tap 111,239` W4→W1 生效。
- **AC3**：像素断言（`assets/verify-w2-after.png` / `verify-w3-after.png`，脚本化白框带扫描）——
  W2/W3 日期章与水印溢出白框 **0px**（改前 +32px/+100px）；
  长标题回归（`verify-w3-title.png`「Summer 2026」+ 日期）字形间距 **45px**、标题未越 0.54w 域
  （改前重叠 ~140px）。
- **AC4**：`CardLayoutTest` 9 测试全绿（含新增：maxH 反解、无界行为等价、任意宽度域互斥）。
- **AC5**：`./gradlew testDebugUnitTest` 33 测试 0 失败；`installDebug` 成功；四页截图回归
  （assets/verify-*-after.png）。

关键落地：`ui/PageInsets.kt`（新）、四页根布局、`CardLayout.solve(width, maxH)` +
`TITLE_RIGHT_FR/STAMP_LEFT_FR`、Compose/native 双端日期章缩字适配、`DevelopScreen` 槽高反解。
