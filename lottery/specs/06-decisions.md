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
