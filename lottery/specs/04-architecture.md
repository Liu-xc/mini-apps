# 04 — 架构与分层

## 分层（单模块 `app`，核心逻辑纯 JVM 可测）

```
ui/            Compose 表现层
  theme/       token、动效弹簧、触感
  generate/    W1 选号
  draw/        W2 开奖 + 剧场 overlay（编排/机械层）
  draw3d/      Filament球体/球壳/底座
  tickets/     W3 票夹 + W4 详情
  common/      空态、chip、球号渲染 BallView 等
core/          领域层（零 Android 依赖，单测主战场）
  Game.kt        玩法定义、档位、注数组合数
  Generator.kt   种子指纹 + 确定性出号（含复式）
  Issue.kt       开奖日历 → 期号 / 最新期
  DrawResult.kt  演示开奖数据（确定性）
  Verify.kt      官方奖级表 + 复式展开聚合
  SeedHash.kt    图片字节 → 指纹（接受外部喂 bytes，解码在 data 层）
  physics/       三维球群积分/接触、捕获/管轨与时间轴
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

## 动画架构（it-003，模拟/复现/渲染分离）

- `core/physics/DrawPhysics`：纯JVM三维球体状态，1/120秒固定步、4轮接触修正，种子化气流与阻力、重力、球壁/球球冲量及摩擦；入场先预沉降。
- `core/physics/DrawScene`：主/特号两个世界、统一`DrawTiming`、捕获及管轨受控复现；输出可复用VisualBall帧缓冲。模拟不会决定演示开奖号。
- `ReplayOverlay`：单一Compose帧时钟先推进模拟、后通知Canvas重绘；字幕/计数只在语义变化时重组，后台暂停，rememberSaveable恢复播放时间。跳过只将选中球安放终端，不补算整个时间轴。
- `ui/draw3d/Stage3D`：默认启用Filament LIT球体、号码贴图与旋转、透视相机、真实金属底座与透明Fresnel球壳；透明TextureView与Canvas机械层同一StageGeometry坐标；绘制只读取模拟，不能另开一套物理时钟。
- `ui/draw/CanvasStage`：静止预览/首次GPU就绪前的号码球；背景、导管、轨道、接触底影与前景高光，均与三维投影共享几何。
- 预编译`.filamat`存`app/src/main/assets/`，源码与重建脚本在`tools/materials/`、`tools/compile-materials.sh`；匹配Filament1.57.1，不再携带运行时filamat编译器（ADR-007）。
- 只读GPU材质/网格/贴图按进程缓存；场景实体、材质实例、Surface、相机/灯光组件、View/Scene/Renderer及回调逐场释放。TransformManager使用getInstance(entity)，不以实体号或重复create返回值更新。

## 构建

- 独立 Gradle 工程，settings 引根 `gradle/libs.versions.toml`（Filament渲染依赖见ADR-006/007）。
- minSdk 31 / target 35 / Kotlin 2.1.21 / Compose BOM 2025.06.01 / M3 1.4.0。
- 设备分治：`lottery_*` AVD（`tools/emu.sh`），不裸跑 adb。

## it-004独立兼容工具（非生产替换）

`tools/model-review/`验证真实GLB结构与桌面Jolt接触；`tools/android-probe/`为独立Gradle构建/包名，使用Godot4.5.2与独立`:stage`进程，通过内部事件和Activity结果连接原生宿主。重进前检查旧场景进程释放，避免终止中的native单例复用。OpenGL路径十轮通过；Vulkan/Mobile在当前模拟器未通过。生产依赖图和舞台仍按it-003；模型资产、完整机械物理、皮肤及移动端视觉通过前不替换。工具与事实边界见ADR-008及it-004报告。
