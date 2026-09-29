# it-069 · P2 机械细项打包（review 收尾）

状态：**已实施**（2026-09-29；Leo 拍板「开机械细项包」——只收无产品决策的细项，IA/结构类另立）

> 编号勘注：P2 包原规划位次 it-068，因 [it-068-outfit-edit-with-effect-annotation](it-068-outfit-edit-with-effect-annotation.md) 顺延为 it-069。

## 背景与动机

2026-09-29 全量 review（it-066 来源）除三个 P1 与 it-065/068 外，还有十余条 P2 机械细项：触控/文案/状态一致性与两处小的行为缺口。本迭代一次性收口；**IA 类不收**（设置入口归宿、数据包入口、W8 列表重构、W12 会话删除、W3 品类图标可发现性——均需产品决策或结构性改动，留后续迭代）。

## 涉及的用户故事

- US-05/US-08（W1 随机与保存按钮语义）、US-10/US-03（筛选状态保持）、US-13/US-21（文案与折叠符号）、US-41b/44（对话输入与时间戳）——均为既有故事的完成度修补，不新增故事。

## 验收标准（细项清单）

1. **W1 空组合护栏**：组合为空时「复制长图」「保存这套」禁用（防空穿搭/空面板）；「随机一套」在每品类仅 1 件时点击给轻提示（不再静默）。
2. **Tab 切换保筛选**：W3 的品类/标签筛选、W8 的标签筛选用 `rememberSaveable`，Tab 往返不丢。
3. **日期语义统一**：W7 顶栏「穿搭 · 日期」由 `createdAt` 改 `updatedAt`（与列表缩略、评论一致——编辑后不再两处日期打架）。
4. **一致性细项**：W1 顶栏 TextButton 触控 ≥44dp；W5 相关穿搭卡下去掉重复「点开看整套 ›」（整卡可点不变）；W8「随机一套」改与 W1 同形制 TextButton+图标；W10「已购入」折叠 `▾/▸` 文字改 Material ExpandMore/Less；W10 去「→」「↗」混排残留（「→ 去预览」→「去预览」等）；ChatList 时间戳并入 `yyyy/MM/dd HH:mm` 全站格式；W2 emoji 选择行改 FlowRow（8 枚不溢出）。
5. **W6 重拼反馈**：长图重拼中预览角落显示轻进度指示；用户手改文案后动维度导致重新生成时 toast「文案已按新设定重新生成」（手改丢失不再无声）。
6. **W13 流式预输入**：生成期间输入框可继续打字，仅禁发送（停止按钮语义不变）。
- 全局：无新增色彩/音效/动效；`testDebugUnitTest + assembleDebug -PdemoDefault=true` 全绿。

## 实施方案

逐项小改：`OutfitScreen.kt`（1/4）、`WardrobeScreen.kt`/`RecordsScreen.kt`（2）、`OutfitDetailScreen.kt`（3）、`ItemDetailScreen.kt`/`WishlistScreen.kt`/`PersonSheet.kt`/`ChatListScreen.kt`（4）、`ExportSheet.kt`（5）、`ChatScreen.kt`（6）；specs 02 相关注记 + CHANGELOG。

## 影响范围

- **代码**：上述 8 个 UI 文件；**不涉及**数据模型、仓储、导航结构、导出格式。
- **常青 spec**：`02-wireframes.md`（注记）、必要时 `05-design-system.md`。

## 验证记录

**构建与测试（2026-09-29）**

- `./gradlew :app:testDebugUnitTest :app:assembleDebug -PdemoDefault=true`：BUILD SUCCESSFUL，91 测全过。

**AVD 冒烟（wardrobe_test / emulator-5558）**

- ✅ W1 无回归：顶栏三枚 TextButton 提到 48dp 后布局正常，组合非空时动作按钮正常可用（截图）。
- 各细项均为条件渲染/参数/文案级改动（编译 + 既有测试覆盖）；空组合禁用态、W8 随机按钮新形制等逐项截图因 AVD 输入链不稳未采集，**留真机顺手复验**（清单即上文验收标准 6 条）。
- 02-wireframes 增 it-069 注记；无新增色彩/音效/动效。
