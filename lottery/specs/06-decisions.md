# 06 — 架构决策记录（ADR）

## ADR-001 平台选型：Android 原生 Kotlin + Compose（非 WebView）

- 状态：接受 2026-09-30
- 背景：Leo 提出 WebView 便于跨端迁移 vs 原生体验。拍板：**先安卓原生**。
- 决策：单模块 Compose 应用，与 wardrobe/eats/darkroom 同栈同构（版本表、AVD 分治、
  emu.sh 全套复用）。
- 后果：动画（Canvas/物理/触感）与 MediaStore/Photo Picker 全走原生能力，一夜交付确定性
  最高；跨端留待原生验证后另立 ADR（届时 core 层纯 Kotlin 可平移是我们的注脚）。

## ADR-002 渲染引擎：Compose Canvas（Skia）+ 手写物理，不引第三方 3D/Lottie 引擎

- 状态：接受 2026-09-30
- 背景：Leo 希望「尽量用开源渲染/3D 引擎」把动画做足。候选：Sceneform/Filament（3D）、
  Lottie（AE 动画）、纯 Compose Canvas。
- 决策：**demo 用 Compose Canvas + 自研确定性物理（固定步长 + 弹簧编排）**。理由：
  ① 仓库零新依赖原则（版本表 6 处 pin 的教训）；② 摇奖罐是 2D 圆形碰撞问题，Skia +
  手写物理完全够，3D 引擎收益低、接线成本一夜兜不住；③ Lottie 需外部 AE 资产，无法
  代码内迭代；④ 确定性时间轴可单测、可跳过、可 reduce-motion 降级。
- 后果：动画质量取决于我们自己的编排功力（DESIGN §3 对表）；若后续要真 3D 质感再评估
  Filament，届时 core/时间轴层不动，只替换舞台渲染。

## ADR-003 开奖数据：DemoDrawRepository 本地确定性，接口留真实 API 插槽

- 状态：接受 2026-09-30
- 决策：`DrawRepository` 接口（core），本期唯一实现 `DemoDrawRepository`（SHA-256 种子
  按期号出号）；UI 全程「演示数据 · 非官方」标注。
- 后果：业务闭环不被网络/接口可用性卡死；真实数据上线时换实现 + 去标注即可，验票/剧场
  时间轴零改动。风险：演示号与真实号无关——诚实标注就是安全阀。

## ADR-004 验票奖级：官方奖级表逐注判定（SSQ 6 级 / DLT 9 级）

- 状态：接受 2026-09-30
- 决策：单式判一级；复式全展开逐注判（上限 50,000 注）聚合每级注数。只输出奖级与注数，
  **不输出金额**（浮动奖无法离线给准数，宁缺毋假）。
- 后果：奖级表是纯函数、单测逐级钉死；奖金/派奖留待接真实数据的迭代。

## ADR-005 票夹存储：kotlinx-serialization JSON 单文件 + tmp→rename

- 状态：接受 2026-09-30
- 决策：不上 libs/store（demo 不值当 composite 接线成本），但**复用其原子写模式**：
  写 tmp → rename，未知字段忽略保前向兼容。
- 后果：数据量小（票夹）单文件足够；若迭代膨胀或要多设备同步，再评估迁 libs/store
  （迁移时 JSON schema 不变，成本低）。

## ADR-006 开奖剧场渲染：接入 Filament，但交付默认 Canvas 2.5D（模拟器原生层不可验收）

- 状态：接受 2026-09-30（it-002）
- 背景：it-001 验收反馈「没用 3D 技术」，ADR-002 的 Canvas 取向被用户推翻。
- 决策：**接入 Filament 1.57.1（filament-android + filamat 运行时材料编译）**，
  完整实现留在 `ui/draw3d/`（UV 球网格、号码贴图、UNLIT 材料 `platform(MOBILE)`、
  编排导演、透明 SurfaceView 合成）；**但剧场默认渲染走 `ui/draw/CanvasStage.kt` 的
  2.5D 确定性舞台**。
- 原因：模拟器（SwiftShader + MTE arm64）上 Filament 路径在 buildBalls 附近间歇
  SIGSEGV（fault addr 为 Java 堆标签指针，换 1.71.5/1.57.1、float[]/double[] 均复现）；
  叠加该模拟器 screencap/uiautomator 高频旧帧，3D 无法在此环境验收。真机验证稳定后，
  ReplayOverlay 舞台实现切回 `DrawStage3D` 即一开关的事。
- 后果：APK 体积 +≈20MB（保留 native 库以便真机切换）；2.5D 舞台与 3D 共用时间轴常量
  （Stage3D.T_*），切换零语义漂移。

## ADR-007 默认三维物理舞台与预编译材质（取代ADR-006默认降级）

- 状态：接受，2026-09-30，用户确认it-003「实施」。
- 背景：编排正弦与UNLIT贴图无法提供重量、碰撞与材质；原生接入不能等同实际启用。
- 决策：固定步长三维模拟独立于渲染；默认Filament1.57.1 LIT球体/金属底座/透明球壳，TextureView透明合成，Canvas只承担机械管轨、阴影/反光和静止预览。时间轴唯一来源为DrawTiming。
- 修复：TransformManager以getInstance(entity)更新，避免实体号或重复create导致句柄失效；正的局部包围盒、单位网格按球半径缩放；View.viewport与TextureView实际缓冲分辨率一致，避免缩放裁切导致巨球/错位；透明SwapChain采用UiHelper.swapChainFlags。
- 材质：SDK版本保持1.57.1；新增源码`.mat`与同版matc编译的`.filamat`资产，移除`filamat-android`运行时依赖。开发重编使用tools/compile-materials.sh，正常构建无需matc。官方工具来源：[Filament v1.57.1](https://github.com/google/filament/releases/tag/v1.57.1)。
- 生命周期：只读GPU资产进程内复用；每场释放实体、灯光、相机、实例、Scene/View/Renderer/Surface与回调，Engine仅随进程终结销毁。首次GPU帧完成前显示静止Canvas球堆，不提前播放空壳。
- 后果：模拟内碰撞实际积分；开奖目标球捕获、导管与轨道仍为受控复现，以保持已有开奖号与连续旅程。玻璃不宣称精确折射。硬件模拟器、真机与软件模拟器性能必须分别记录，不能相互替代。
