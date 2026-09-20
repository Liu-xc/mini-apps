# it-021 · 结构拆分：ViewModel 与 Repository 宽接口分解（仅动结构不动功能）

- **状态**：阶段 1 已实施（2026-09-21，a911ba7）；步骤 2 的 WishlistViewModel 拆分经评估裁剪（理由见下）；余项转为低优先观察
- **提案日期**：2026-09-21

## 背景与动机

[架构评审报告](../../reports/2026-09-20-architecture-review/report.md) P0-3：wardrobe 的「小应用架构」已到撑不住「中型体量」的临界点——

1. **AppViewModel 538 行、约 13 类职责**，是全 app 唯一 ViewModel；it-017~019 每轮迭代往里加 5~10 个函数；且 VM 直接实例化 UI 渲染器（`WardrobeRecapLongImage`）做位图编排、把 export 层对象 getter 透传给屏幕。
2. **WardrobeRepository 91 行 26 个 suspend 方法横跨 7 个聚合域**（Person/Item/Outfit/WearLog/WishItem/WishOutfit/Note），ISP 违例；直接后果是 Mock 仓库要手抄全部 26 个方法语义——it-020 修掉的级联漂移正是这种手抄的必然产物，宽接口不拆，漂移会再来。
3. ImageStore 接口名存实亡（P1-3）：实现多 3 方法不在接口上、容器与 Composer 持具体类。
4. WishlistScreen 970 行 9 个 composable、4 个完整 ModalBottomSheet 表单塞一个文件（P1-4）。
5. ReminderScheduler 候选规则内嵌 companion 不可 JVM 测（P1-5；eats 同构位置已是 domain 纯函数，向其看齐）。

## 提案方案（分四步，每步独立可验证可提交）

### 步骤 1：Repository 按聚合拆接口（数据层，风险最低收益最大）

- `domain/repository/` 拆为 `PersonRepository / ItemRepository / OutfitRepository / WearLogRepository / WishRepository / NoteRepository`（各 ≤6 方法）+ 组合门面 `WardrobeRepository`（继承上述接口，签名不变或极小调整）。
- `WardrobeRepositoryImpl` 仍是单类实现全部接口（存储格式、级联、`cleaned()` 一概不动）；`MockWardrobeRepository` 同步实现——接口收窄后，Mock 与 Impl 的语义对齐面从 26 方法降到 6×小接口，配 it-020 的级联一致性测试继续锁定。
- UI/VM 调用点改持细分接口或组合门面（二选一，实施时定）。

### 步骤 2：AppViewModel 拆分（UI 层）

- 保留轻量全局 VM：`currentPerson` / `data` / `toast` 通道 / `isDemo`。
- 按域拆 feature VM（或同 VM 内聚的 handler 类，实施时按屏幕耦合度定）：`WishlistViewModel`（心愿域全流程 it-019 的 ~150 行）、`RecapViewModel`（回顾长图/提醒）、`OutfitViewModel`（组合/导出/成品图）。
- 长图渲染编排（`generateRecap`）移出 VM：渲染器构造权下放 UI 层或 platform，VM 只出数据与意图。
- VM 不再透传 `imageComposer/promptBuilder/share` getter——屏幕需要的导出门面经 ui 层组合根参数传入。

### 步骤 3：ImageStore 接口收编（P1-3）

- `cutoutTo/decode/exportDir` 收进接口语义（cutoutTo 的 engine 参数注入方式随之定义），或确认无第二实现后删接口直用具体类（与 eats 的接口暴露方式对齐，推荐前者）。

### 步骤 4：UI 文件分解与可测性（P1-4/P1-5）

- `WishlistScreen.kt` 拆为 screen + 4 个 sheet 文件；`rememberPhotoPicker` 导入样板抽公共组合函数（8 处重复）。
- ReminderScheduler 候选规则抽 `domain/usecase/StaleItemSelector` 纯函数（对齐 eats `MemoryCandidateSelector`：注入 clock，补 JVM 单测）。

## 验收标准

1. 单文件/单类规模：AppViewModel 及各 feature VM <300 行、职责 ≤5 类；单接口方法数 ≤8；WishlistScreen 主文件 <400 行。
2. 既有 52 个单测全绿（步骤 1 允许同步调整测试构造）；步骤 2/4 无测试可锁，**必须模拟器全页走查零回归**（结构改动，交互与视觉零变化）。
3. spec 04-architecture 的「MVVM + UDF」与模块树同步改写（it-020 已先记录单 VM 现状，本迭代兑现拆分后的真实结构）。

## 影响范围

- domain/repository、data/repo、data/mock、ui/AppViewModel 与全部屏幕的 vm 调用点、di/AppContainer、platform/ReminderScheduler、specs/04-architecture.md、CHANGELOG

## 明确不做

- 存储格式/级联语义/任何用户可见行为变更；eats 侧同构拆分（eats VM 258 行未到临界，等 clips 落地再评估）。

## 验证记录

### 阶段 1 实施记录（2026-09-21，a911ba7）

- ✅ **步骤 1**：Repository 拆为 Person/Item/Outfit/WearLog/Wish/Note 六个子接口，`WardrobeRepository` 门面继承（`data` 快照在门面）；Impl/Mock/调用点零改动，56 单测绿。
- ✅ **步骤 2 部分**：`RecapViewModel` 拆出（recapPrefs/setReminder/syncReminderSchedule/generateRecap(personId,…)/saveRecapImage/shareRecapImage），`WardrobeRecapScreen` 双 VM（appVm=角色/数据/toast，recapVm=回顾域）；长图渲染异常补 runCatching 兜底；MainActivity 启动对齐改走 recapVm。
- ✅ **步骤 3**：`ImageEditStore` 接口（cutoutTo/decode/exportDir）收编，ImageFileStore 实现；OutfitImageComposer/AppViewModel 改依赖接口；domain ImageStore 保持 JVM 纯净（Bitmap 不进 domain）。
- ✅ **步骤 4 部分**：ReminderScheduler 候选规则 + wearStats 聚合抽 `domain/usecase/StaleItemSelector` 纯函数，+4 单测（含「恰好 N 天入选」边界）。
- ⚠️ **裁剪：WishlistViewModel 拆分**——评估结论：WishlistScreen 对 VM 的 13 个调用点中 11 个是全局能力（data/currentPerson/toast/importPhoto/imageFileOf/share），心愿域私有状态仅 mixWishes（2 行）；拆分只会把薄委托搬来搬去，收益/风险比不成立。若心愿域继续长出演化逻辑（如智能排期、价格追踪）再拆。
- ⚠️ **搁置：WishlistScreen 970 行文件拆分**——机械拆文件需理顺 9 个 composable 间的私有 helper 耦合，纯装饰性收益；待下次功能性触碰该文件时顺带拆。

### 模拟器验证

- 阶段 1 装包冒烟：W9 回顾页（新 RecapViewModel 路径）、W10 心愿页零崩溃；导出复制长图（Composer 走 ImageEditStore 接口路径）实测「已复制 ✓」。
- it-023 三轮走查（同 worktree）全部交互流复验通过。
