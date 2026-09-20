# it-009 · 架构评审止血：写路径错误兜底 + 常青 spec 对齐

- **状态**：实施中（2026-09-21，arch-review-fixes worktree）
- **来源**：[仓库架构评审报告 2026-09-20](../../reports/2026-09-20-architecture-review/report.md) P0-1 / P0-4 及 eats 相关 P2（仓库级 CI/version catalog 随 wardrobe it-020 一并落地，见其影响范围）

## 背景与动机

评审发现 eats 两类问题：

1. **P0-4 错误兜底缺失**：AppViewModel **0 个** try/catch，spec 04 声称「写失败 → ViewModel 捕获 → snackbar + 回滚」完全是空头承诺；commit 抛异常沿 viewModelScope 直接崩进程。
2. **P0-1/P2 spec 脱节**：04-architecture 列了不存在的 `map/PickLocationController.kt` 与 usecase `ComputeStats`；缺 `data/prefs/`、`platform/ReminderScheduler`、`platform/RecapSaver`、`ui/recap/`；路由表过期（`main` 应为 `home`、缺 `recap`）；MVVM「每屏 ViewModel + sealed Event」描述与单 AppViewModel 现实不符。另：README 状态仍写「it-001 已完成」、CHANGELOG it-006 仍标「进行中」、it-008 迭代文件头部「待确认」与已入库事实不符、06-decisions ADR-011 排在 ADR-010 之前。

## 涉及用户故事

无新增用户故事（技术债清理）。所有写操作（US-01~US-11 的增删改）在失败时从「闪退」变为「toast 提示且数据不落脏」。

## 验收标准

1. AppViewModel 中所有经 `viewModelScope.launch` 调用 repo **写操作**的路径统一异常捕获（`launchSafely` 助手：失败 toast + Log；内存回滚由 libs/store 的 commit 序列天然承担）。
2. `specs/04-architecture.md` 与代码一致：模块树（去幽灵文件、补 prefs/platform/recap）、usecase 清单（BuildCandidates / MemoryCandidateSelector / RecapCalculator）、路由表（`home / placeEdit?placeId={placeId} / placeDetail/{placeId} / recap`）、MVVM 现状描述、错误处理描述与实现后一致、构建配置补 store 坐标与 Kotlin 2.1.x。
3. `README.md` 状态行更新；`specs/CHANGELOG.md` it-006 结清、补 it-009 条目；`specs/iterations/it-008-*.md` 状态同步为已完成；`06-decisions.md` ADR-010 移到 ADR-011 之前。
4. 既有 49 个单测全绿。

## 影响范围

- `app/src/main/java/com/leo/eats/ui/AppViewModel.kt`（launchSafely + 写路径兜底）
- `specs/04-architecture.md`、`specs/06-decisions.md`、`specs/CHANGELOG.md`、`specs/iterations/it-008-eat-drink-play-wishlist.md`、`README.md`

## 明确不做

- ListScreen 等 641/514 行单 composable 文件分解（P1-4）、UI 层「今天」计算收敛到显式时区（P2-8）——随下次触碰相关文件时处理。

## 验证记录

- **2026-09-21（arch-review-fixes worktree）**：eats `testDebugUnitTest` **49/49 绿**；launchSafely 覆盖 savePlace/deletePlace/setWish/setPlan/logVisit（含拔草撤销闭包）/deleteVisit/setReminder/syncReminderSchedule/generateRecap 全部写路径（spin 偏好 setter 属 DataStore 偏好、不属 SSOT 落盘，不在范围）。未做模拟器走查（无 UI/交互变更，行为变化仅为失败路径：崩溃→toast）。
