# it-015 · 质感 pass：触感基线 + 统计数字动效 + 入场预算修正（DESIGN.md 首轮落地）

- **状态**：实施中（2026-09-21 Leo 拍板「先完成现有应用的质感提升」）
- **范围**：eats 全局质感，不新增任何用户功能、不动数据模型
- **对表基准**：根目录 [DESIGN.md](../../../DESIGN.md)（本迭代即其首轮落地，动效参数、触感规则、红线均引用之）

## 背景与动机

与 wardrobe it-027 同一轮：补 DESIGN.md 盘点出的触感缺口（§4 全仓为零）与统计数字跳变（§5 反例 9），另发现 eats 特有一处动效预算违规（§3：列表入场 stagger 在从详情返回时重放）与一处对比度贴线。

## 改动清单

### A. 触感反馈（DESIGN.md §4）

新增 `ui/components/Haptics.kt`（与 wardrobe 同构实现）：接线点：

| 动作 | 触感 |
|---|---|
| 记一笔落账成功（✓ 形变+彩屑处） | confirm |
| 随机抽一张落定（含结果条「再抽」） | confirm |
| 保存/更新食堂成功 | confirm |
| 名称必填等未就绪 toast | error |

### B. 统计数字 Count-up（DESIGN.md §5 反例 9 → 修复）

新增 `ui/components/CountUp.kt`：`CountUpText(target, format, …)`，弹簧 `EatsMotion.smooth()`，三格 60ms 错峰。应用于 W7 三大数字（档位内顿数/总花费/去过店数；总花费为空仍显示「—」不参与动画）。

### C. 动效预算修正（DESIGN.md §3）

`ListScreen.StaggeredEntrance` 现用 `remember { Animatable(0f) }`，从详情返回会整列重放 24ms×12 错峰入场（预算红线：>200ms 入场只允许首进）。改为屏幕级 `rememberSaveable` 标志，仅首进播放入场，返回走即时路径。

### D. 对比度微调（DESIGN.md §2.2）

实测 `inkFaint #84907F` 在 paper 上 3.08:1，贴线无余量 → 调至 `#828E7D`（3.23:1，色相不变、目视无差），深色与其余 token 全部达标不动。已知例外（记录不改）：按钮纸字/accent ≈3.2:1，按大号文字口径接受；accent 品牌色不动。

## 验收标准

- Given 落账/抽中/保存成功，Then 一次轻微确认震感
- Given 首次进 W7 或切换 今年/去年/累计，Then 三大数字 count-up；Given 档内无花费，Then 总花费仍显示「—」
- Given 从列表进详情再返回，Then 列表不重放错峰入场
- `./gradlew :eats:app:assembleDebug` 与单测全绿；无新增权限

## 影响范围

`ui/components/`（新增 2 文件）、`SpinScreen` / `LogVisitSheet` / `PlaceEditScreen` / `ListScreen` / `RecapScreen` / `theme/DesignTokens`（一处色值）；spec 同步：05-design-system.md（触感小节 + 动效清单 +1 + token 值）。

## 验证记录（2026-09-21 回填）

- 构建 + 单测：`./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` / `:app:testDebugUnitTest` 全绿——**53 tests, 0 failures**。
- 模拟器冒烟（emulator-5554）：安装启动正常；统计回顾页三大数字（14 顿 / ¥513 / 9 店）经新 `HeroCell→CountUpText/CountUpFloatText` 渲染正确（花费 ¥ 口径正常）；列表→统计导航正常；logcat 无 FATAL。
- 入场预算：`StaggeredEntrance(animate = !entranceDone)`，`rememberSaveable` 标志首进 ~900ms 后置位——返回/二次进入即时显示，不再重放 24ms 错峰（代码层验证 + 启动冒烟通过）。
- 触感为物理反馈，模拟器无法自动断言：已接线 **5 处**（confirm ×4：落账/抽中落定/结果条再抽/保存食堂；error ×1：名称必填），待真机手感复核。
- 对比度实测（脚本计算）：inkFaint `#84907F` 对 paper 3.08:1 贴线 → 改 `#828E7D` 得 3.23:1（对 white 3.43）；深色 6.92 不变；其余 token 全达标。
