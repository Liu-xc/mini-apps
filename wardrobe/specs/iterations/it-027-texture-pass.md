# it-027 · 质感 pass：触感基线 + 统计数字动效（DESIGN.md 首轮落地）

- **状态**：实施中（2026-09-21 Leo 拍板「先完成现有应用的质感提升」）
- **范围**：wardrobe 全局质感，不新增任何用户功能、不动数据模型
- **对表基准**：根目录 [DESIGN.md](../../../DESIGN.md)（本迭代即其首轮落地，动效参数、触感规则、红线均引用之）

## 背景与动机

DESIGN.md 定稿时盘点出两个跨 App 的质感缺口：①触感反馈全仓为零（DESIGN.md §4），关键确认动作只有视觉反馈；②统计回顾页大数字直接跳变（DESIGN.md §5 反例 9）。本轮以最小改动补齐，并完成一次红线对表（§5）。

## 改动清单

### A. 触感反馈（DESIGN.md §4）

新增 `ui/components/Haptics.kt`：`rememberHaptics()` → `confirm()` / `error()` / `tick()`，走 `LocalView` + `HapticFeedbackConstants`（CONFIRM/REJECT 需 API 30，低版本回退 LONG_PRESS / VIRTUAL_KEY；tick 用 CLOCK_TICK）。接线点：

| 动作 | 触感 |
|---|---|
| 复制长图成功 / 存相册成功 | confirm |
| ☆ 保存这套 / 🌟 存为心愿 成功 | confirm |
| 穿搭打卡（含再记一次） | confirm |
| 去背景成功 | confirm |
| 评论发送 / 撤销今日打卡 / 还原原图 | tick（轻） |
| 未就绪点保存的 toast 提示 | error |

### B. 统计数字 Count-up（DESIGN.md §5 反例 9 → 修复）

新增 `ui/components/CountUp.kt`：`CountUpText(target, format, …)`，`Animatable` 从 0 起数（首进回顾页）并在档位切换时从旧值过渡到新值，弹簧用 `EditorialMotion.smooth()`，三格 60ms 错峰。应用于 W9 三大数字（单品/穿搭套/打卡次数）。

### C. 红线对表结果（DESIGN.md §5 + §2）

- 对比度实测（脚本计算）：inkFaint/paper 浅 3.26、深 6.91；ink/paper 14.61——全部达标，**无需改值**。
- onPrimary 实为纸色而非白色（深色模式按钮对比 ≈9:1），无问题。
- 触控目标、删除确认/撤销、空态三要素、深色重绘：逐条过表，现状合规，无改动。
- 已知例外（记录不改）：按钮纸字/accent ≈3.3:1，按大号文字（≥14sp）口径接受；accent 品牌色不动。

## 验收标准

- Given 任一关键确认动作成功，When 完成，Then 一次轻微确认震感（不与滚动/导航误触）
- Given 首次进 W9 或切换 今年/累计 档位，Then 三大数字 count-up 过渡而非跳变
- Given 低版本（API <30）设备，Then 触感回退可用、不崩溃
- `./gradlew :wardrobe:app:assembleDebug` 与单测全绿；无新增权限

## 影响范围

`ui/components/`（新增 2 文件）、`ExportSheet` / `OutfitScreen` / `OutfitDetailScreen` / `ItemEditScreen` / `WardrobeRecapScreen`；spec 同步：05-design-system.md（触感小节 + 动效清单 +1）。

## 验证记录（2026-09-21 回填）

- 构建 + 单测：`./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` / `:app:testDebugUnitTest` 全绿——**62 tests, 0 failures**。
- 模拟器冒烟（emulator-5554，API 真机流程同构）：安装启动正常；衣橱回顾页三大数字（18 单品 / 5 穿搭套 / 0 打卡次数）经新 `HeroCell→CountUpText` 渲染正确；logcat 无 FATAL/AndroidRuntime 异常。
- 触感为物理反馈，模拟器无法自动断言：已接线 **13 处**（confirm ×8：复制长图/存相册/保存这套/存为心愿/打卡/再记一次/去背景成功/保存衣物；tick ×4：评论发送/撤销今日/还原原图/文本复制；error ×1：未就绪点保存），待真机手感复核。
- 对比度实测（脚本计算 WCAG 相对亮度）：inkFaint/paper 浅 3.26、深 6.91，ink/paper 14.61，按钮纸字/accentDark ≈9:1——全达标，未改任何色值。
