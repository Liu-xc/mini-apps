# it-006 · 数据层接入 libs/store SDK + 应用内 Mock 数据源（演示模式）

- **状态**：已确认（/goal 指令，2026-09-20）
- **提案日期**：2026-09-20
- **范围**：`data/` 层替换为 libs/store SDK；新增应用内可切换的 Mock 数据源（演示模式）；顺带修复 01-user-stories.md 历史污染

## 背景与动机

1. **本地存储 SDK 统一**：`libs/store`（0.1.0）已落地并在 wardrobe 全量接入（SnapshotStore / SsotRepository / FileMediaStore）。
   eats 的 `data/json/JsonFileStore.kt` 与 SDK 的 SnapshotStore 语义完全同源（ADR-003 即 wardrobe 同模式），
   属于重复实现；`EatsRepositoryImpl` 的「改快照→落盘→广播」手写样板也正是 SsotRepository 基类的抽象。
   数据层应换用 SDK，删除自研副本，为将来接入 libs/sync（writeHook 缝）铺路。
2. **Mock 数据源**：测试体验与 AI 走查需要一份「内容丰富、相对时间恒新鲜、不污染真实数据」的数据源。
   现有 `tools/demo-data.sh` 是外部脚本、直接覆盖真实 eats.json，用完还得手动清——需要应用内的演示模式：
   一键切换到内存 Mock 仓库，一键退回，真实数据零接触。
3. **specs 污染修复**：`01-user-stories.md` 在 it-003 提交（25c25ce）中被写成 2.4MB 的 US-07 重复块；
   恢复 015a5dd 健康版（77 行）并重放 it-003 的 US-07 修订。

## 方案

### 1. 接入 libs/store

- `app/build.gradle.kts` 增加 `implementation("com.leo.libs:store:0.1.0")`（composite build，与 carddeck 同法）。
- `data/json/JsonFileStore.kt` 删除；AppContainer 改造 `SnapshotStore<EatsData>`（fileName 保持 `eats.json`，
  schemaVersion 迁移链入参原样保留）。**磁盘数据格式不变**（同 JSON 结构 + .bak），老数据无缝升级。
- `EatsRepositoryImpl` 改继承 `SsotRepository<EatsData>`（onLoad = cleaned()），删除手写 Mutex/StateFlow 样板；
  写后图片物理删除等业务逻辑保留在实现类。
- `ImageFileStore` 文件管理部分（uuid 命名/删除）交给 SDK `FileMediaStore`，解码/EXIF/压缩保留在本类（与 wardrobe 同构）。
- `SpinPrefsStore`（DataStore 偏好）**不动**：键值偏好不属于快照存储，SDK 不覆盖。

### 2. Mock 数据源（演示模式）

- `data/mock/MockEatsData.kt`：确定性种子数据——约 10 个 Place（堂食/外卖/自做全覆盖，含评分/标签/美团点评链接/坐标）、
  约 16 条 Visit（近 60 天分布，含评分/花费/感想）。时间戳按「构建时刻 − N 天」相对生成，走查时永远「3 天前刚吃过」。
- `data/mock/MockEatsRepository.kt`：实现 `EatsRepository`，内存 StateFlow，写操作只改内存不落盘（演示数据不可持久化）。
- 开关：`DemoMode`（SharedPreferences 布尔位）在 **AppContainer 构造时** 决定装配真实仓库还是 Mock 仓库；
  演示模式下图片目录指向 `cacheDir/mock-images`（真实 images/ 不被触碰）。
- 切换即重启进程（保存偏好 → 重启 AppContainer 生效）。
- 入口：列表页工具行加「演示」图标按钮，**仅 BuildConfig.DEBUG 可见**（release 零痕迹）；
  按当前模式弹出「进入/退出演示」确认（修订：Leo 反馈演示数据自明，去掉顶部粉色横幅，退出并入同一入口）。
- `tools/demo-data.sh` 保留（外部灌真数据的另一种用法），README 注明两者区别。

### 3. specs 污染修复

- `01-user-stories.md` ← `git show 015a5dd` 77 行健康版 + 重放 it-003 的 US-07（卡组抽取修订）+ NFR-04 措辞同步。

## 涉及用户故事

不新增用户故事（开发/测试基础设施）。受影响：US-05（列表页新增 debug 入口，release 不可见）、NFR-02（存储实现名变更，语义不变）。

## 验收标准

- `eats.json` 读写走 `SnapshotStore`；`data/json/` 目录删除；`JsonFileStore` 单测随之删除（SDK 已有同语义测试）
- 老版本数据文件在新版本直接可读（格式未变）
- 演示模式：开启后三 Tab 全部呈现丰富假数据；新增/删除只影响内存；退出后真实数据原样；非 DEBUG 构建无入口
- `assembleDebug` / `test` 全绿；模拟器实测演示模式开关往返

## 影响范围

`app/build.gradle.kts`、`di/AppContainer.kt`、`data/json/*`（删）、`data/repo/EatsRepositoryImpl.kt`、
`data/image/ImageFileStore.kt`、新增 `data/mock/*`、`ui/list/ListScreen.kt`（debug 入口+横幅）、
`MainActivity.kt`/`EatsApp.kt`（重启工具）、specs（03/04/06/01 修复）、CHANGELOG。

## 验证记录

- 构建：`assembleDebug` ✅ / `testDebugUnitTest` ✅（JsonFileStoreTest 删除，EatsRepositoryImplTest 改用 SnapshotStore，新增 MockEatsRepositoryTest 4 例全绿）
- 模拟器实测（emulator-5554）：
  - 真实数据 9 家 → 列表页演示按钮（DEBUG）→ 确认对话框 → 进入演示：重启后横幅「演示数据中」出现，列表 11 家（mock 种子），首页卡片「猪脚饭 · 昨天 · 3 次」为相对时间种子 ✅
  - 点横幅退出：重启回真实 9 家，无横幅，真实数据零污染 ✅
  - 开发中修复三题：① `SharedPreferences.apply()` 异步落盘被 `exitProcess` 杀掉（偏好丢失）→ 改 `commit()`；② DemoBanner 贴屏幕顶被 edge-to-edge 状态栏盖住不可点 → `statusBarsPadding()`；③ ImageStore 接口 `file(): File?` 的空安全调用点修正。
- 存量兼容：磁盘格式未变（同 eats.json + .bak），老数据由 SnapshotStore 直接读起（真实数据在迁移后首次启动即正常加载）✅
