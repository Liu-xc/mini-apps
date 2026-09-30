# 04 — 架构与分层

## 分层（单模块 `app`，核心逻辑纯 JVM 可测）

```
ui/            Compose 表现层
  theme/       token、动效弹簧、触感
  generate/    W1 选号
  draw/        W2 开奖 + 剧场 overlay（含物理/编排）
  tickets/     W3 票夹 + W4 详情
  common/      空态、chip、球号渲染 BallView 等
core/          领域层（零 Android 依赖，单测主战场）
  Game.kt        玩法定义、档位、注数组合数
  Generator.kt   种子指纹 + 确定性出号（含复式）
  Issue.kt       开奖日历 → 期号 / 最新期
  DrawResult.kt  演示开奖数据（确定性）
  Verify.kt      官方奖级表 + 复式展开聚合
  SeedHash.kt    图片字节 → 指纹（接受外部喂 bytes，解码在 data 层）
data/          落盘与平台桥
  TicketStore.kt  tickets.json 原子读写（kotlinx-serialization）
  ImageSeed.kt    Photo Picker URI → 64×64 软件位图 → bytes
  ImageExporter.kt GraphicsLayer 录制 → PNG → MediaStore Pictures/拾彩
LotteryApp.kt  导航壳（三 Tab crossfade + W4 压栈 + snackbar）
LotteryViewModel.kt  单 ViewModel 汇聚三屏状态（demo 简化，不做多 VM）
MainActivity.kt
```

依赖方向：`ui → core`、`data → core`、`ui → data`（经 ViewModel）；**core 不知道 ui/data**。
`DrawRepository` 接口（core）由 `DemoDrawRepository` 实现——真实 API 是它的另一个实现
（ADR-003），本期不接线。

## 确定性原则（继承 darkroom ADR-005 同款）

所有「随机」都是**种子化的确定性映射**：出号、演示开奖号都由 SHA-256 种子驱动，
同一输入恒同一输出——这是可复现（US-1.3）、可测试（core 单测）、演示数据可信（UC-2.5）
的共同地基。

## 动画架构（playout 分离）

- **剧场 = 纯函数时间轴**：`ReplayTimeline` 由期号 + 票据推导各球出槽时刻表；
  `DrawReplay` 组件以单一时钟（`withFrameNanos` / `animateFloat` 进度）查询时刻表。
- **物理 = 固定步长模拟**：罐内球 `PhysicsWorld.step(1/60s)`（重力、壁/球碰撞、罐旋转
  摩擦驱动），状态存 `remember`，与 UI 进度解耦；跳过时直接冻结终态。
- 「在动」自带标志维护（LESSONS：官方 animation 跟踪不认手驱动画）。

## 构建

- 独立 Gradle 工程，settings 引根 `gradle/libs.versions.toml`（零新增依赖）。
- minSdk 31 / target 35 / Kotlin 2.1.21 / Compose BOM 2025.06.01 / M3 1.4.0。
- 设备分治：`lottery_*` AVD（`tools/emu.sh`），不裸跑 adb。
