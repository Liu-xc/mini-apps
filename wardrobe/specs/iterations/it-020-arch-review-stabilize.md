# it-020 · 架构评审止血：Mock 级联对齐 + 写路径错误兜底 + 常青 spec 对齐

- **状态**：实施中（2026-09-21，arch-review-fixes worktree）
- **来源**：[仓库架构评审报告 2026-09-20](../../reports/2026-09-20-architecture-review/report.md) P0-1 / P0-2 / P0-4 / P1-1 / P1-2 及 wardrobe 相关 P2

## 背景与动机

2026-09-20 的全仓架构评审发现 wardrobe 三类问题需要立即处理：

1. **P0-2 Mock 语义漂移（已实锤）**：`MockWardrobeRepository` 的 `deletePerson` / `deleteItem` 缺少 it-019 心愿域级联（真实现 `WardrobeRepositoryImpl` 有），演示模式下心愿域数据行为与真实模式不一致。
2. **P0-4 写路径错误兜底覆盖不全**：AppViewModel 有 9 处 try/catch 但集中在 it-017/019 新增的 5 条写路径；删除、Outfit/Note CRUD、组合记忆等其余写路径无兜底，commit 抛异常会沿 viewModelScope 崩进程（spec 04 声称的「ViewModel 捕获 → 提示 + 回滚」未兑现）。
3. **P0-1/P2 常青 spec 与代码脱节**：04-architecture 的 usecase 清单/模块树/路由表/MVVM 描述与实际不符；00-overview「范围外：背景抠图」与已完成的 it-016 直接矛盾；06-decisions ADR-017 排在 ADR-016 之前；CHANGELOG 已发 0.4.6 而 versionName 仍 0.1.0。

另含**仓库级护栏**（评审 P1-1/P1-2，随本迭代一并落地，影响两个应用）：
- GitHub Actions CI：wardrobe / eats / libs×4 六个构建的单测矩阵（全仓 177 个 @Test 目前只靠本地自觉）。
- 根 `gradle/libs.versions.toml` version catalog：Kotlin/AGP/Compose BOM 等版本目前 6 处手工重复 pin，无护栏。

## 涉及用户故事

无新增用户故事（技术债清理，不动交互与功能面）。演示模式（US-14 演示相关验收）间接受益：心愿域数据在演示模式下与真实行为一致。

## 验收标准

1. `MockWardrobeRepository.deletePerson` 级联删除 wishItems / wishOutfits；`deleteItem` 从 wishOutfits.itemIds 移除该件——与 `WardrobeRepositoryImpl` 语义一致，并有单测锁定（MockWardrobeRepositoryTest 新增 2 用例）。
2. AppViewModel 中所有经 `viewModelScope.launch` 调用 repo **写操作**的路径统一有异常捕获（toast + Log；内存回滚由 libs/store 的 commit 序列天然承担）；引入 `launchSafely` 助手并迁移语义简单的既有捕获点，避免双样板。
3. specs 与代码一致：
   - `00-overview.md`：范围外清单移除「背景抠图」（it-016 已做）。
   - `04-architecture.md`：模块树补 platform/、ui/recap/、ui/wishlist/、export/JpegXmp、data/prefs/；usecase 清单改为实际三项；路由表改 `home / itemEdit?itemId={itemId} / itemDetail/{itemId} / outfitDetail/{outfitId} / recap / wishlist`；MVVM 描述改为「单 AppViewModel 为全局数据流出口」的现状 + it-021 拆分计划；错误处理描述与实现后的实际一致；测试策略/构建配置更新（carddeck+cutout 坐标、Kotlin 2.1.x）。
   - `06-decisions.md`：ADR-016 移到 ADR-017 之前（纯顺序修正，内容不变）。
4. 仓库级：`.github/workflows/ci.yml` 六构建单测矩阵；根 version catalog 接入六个构建；根 README libs 表补 carddeck/cutout、apps 表补 clips、状态列更新；`__pycache__` 出库并入 .gitignore。
5. 既有 50 个单测全绿 + Mock 新增用例绿 + 两 app `testDebugUnitTest` 通过。

## 影响范围

- `app/src/main/java/com/leo/wardrobe/data/mock/MockWardrobeRepository.kt`（+2 行级联）
- `app/src/main/java/com/leo/wardrobe/ui/AppViewModel.kt`（launchSafely + 写路径补兜底）
- `app/src/test/.../MockWardrobeRepositoryTest.kt`（+2 用例）
- `specs/00-overview.md`、`specs/04-architecture.md`、`specs/06-decisions.md`、`specs/CHANGELOG.md`
- 仓库级：`.github/workflows/ci.yml`、`gradle/libs.versions.toml`、六个构建的 settings/build 接线、根 README、libs README 状态行、`.gitignore`

## 明确不做（拆到 it-021）

- AppViewModel（538 行 / 约 13 类职责）与 WardrobeRepository（26 方法宽接口）的**结构拆分**——评审 P0-3，需独立迭代 + 模拟器走查，提案见 `it-021-structure-split.md`。
- ImageStore 接口收编（P1-3）、巨型 Composable 文件分解（P1-4）、ReminderScheduler 候选规则入 domain（P1-5）——随 it-021 或下次触碰相关文件时处理。
- versionName 与 CHANGELOG 版本号对齐——发版决策，留待下次发版迭代统一处理。

## 验证记录

- **2026-09-21（arch-review-fixes worktree）**：
  - wardrobe `testDebugUnitTest`：**52/52 绿**（50 既有 + 新增 2 条 Mock 心愿域级联锁定用例）；eats `testDebugUnitTest`：**49/49 绿**。
  - version catalog 迁移后六构建全量重跑：wardrobe 52 / eats 49 / libs/store 14 / libs/sync 49 / libs/cutout 15 / libs/carddeck build 通过——合计 179 用例 0 失败。
  - 未做模拟器走查：本轮无 UI/交互变更，唯一行为变化是失败路径（commit 异常从「闪退」变「toast」），建议下次例行走查顺带覆盖一条失败路径（如演示模式下故意写失败较难构造，可暂缓）。
  - CI 工作流（`.github/workflows/ci.yml`）已入库，**未在 GitHub Actions 实跑**（分支未推送）；本地等价命令全绿，首次推送后需看一眼首跑结果。
- **2026-09-21 补充（R1 走查）**：演示模式下写路径失败兜底经走查间接覆盖——sheet 校验失败路径实测（转正无照片提交被拦截、无崩溃）；详见 it-023。

## 决策记录（仓库级，暂记于本迭代文件——根级暂无 ADR 归属地）

1. **CI**：单 job 顺序跑六构建（`wardrobe/gradlew -p`，统一 Gradle 8.9 wrapper；carddeck/cutout 无自带 wrapper）；JDK 17 temurin；只跑单测不跑 assemble（个人仓库控制时长）；push 限 main + PR 触发。
2. **version catalog**：根 `gradle/libs.versions.toml`，六个构建 settings 各自 `from(files(...))` 引用（composite build 间无自动继承，逐构建声明）；onnxruntime/onnxruntime-android 共用一个 `onnxruntime` 版本号，从机制上锁死 cutout ADR-002 的「版本须对齐」。
3. spec 头部不再钉死测试用例数字（store「20 单测」→14、cutout「14 用例」→15 的漂移教训），以 CI/测试套件为准。
