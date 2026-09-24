# 06-decisions · 架构决策记录（ADR）

## ADR-001 Web 3D 技术选型：Three.js + Vite + TypeScript

- **状态**：已采纳（2026-09-24）
- **背景**：要求 PC web + mobile web 一套代码、主流开源、成熟生态。
- **备选**：
  - *Babylon.js*：全家桶（物理/GUI/音频内置），但社区素材与教程向 Three.js 倾斜，glTF 生态略逊。
  - *Godot 4 网页导出*：有编辑器与既有经验（slapback），但包体大、线程需 COOP/COEP 响应头、iOS Safari 兼容成本高，不适合「打开即玩」的 web 分发。
- **决策**：**Three.js（0.171.x）+ Vite 6 + TS 5 strict**。
  - 角色控制用运动学胶囊（自写），M0 不引入物理引擎；需要时首选 Rapier（wasm）。
  - 触屏摇杆用 nipplejs（主流、久经考验）。
  - `base: './'` 相对路径构建，可静态托管任意子路径。
  - 渲染管线（it-002 起）：**EffectComposer 后期链** = 描边渲染（OutlineEffect 包装的
    RenderPass 替身）→ UnrealBloom → OutputPass(ACES+sRGB) → 暗角/饱和 Grade → FXAA。
- **后果**：渲染/素材/教程全生态对齐 Three.js；升级需同步 `three` 与 `@types/three` 同 minor。

## ADR-002 素材与渲染策略（it-002 修订：A 套精细风格化 + PBR）

- **状态**：已采纳（2026-09-24，修订自同日 M0 版本）
- **背景**：M0/评审轮素材（Kenney 低模 + 手绘天云）被 Leo 三连否定
  （「好丑」「美工质量太低」「整体没有哪里是好看的」）；二次深度调研后
  Leo 拍板「上限最高」的 **A 套精细风格化** 路线。
- **决策（现行）**：
  - **渲染**：MeshStandardMaterial **PBR**（弃 cel toon 阶梯——三阶量化会吞阴影对比）
    + three 官方 **OutlineEffect 描边** + Bloom/Grade/FXAA 后期 + PCFSoft 4K 阴影
    + ACES 分级（曝光 1.15）。自定义天空 shader 已删，ACES/sRGB 由 OutputPass 统一。
  - **色彩纪律**：白昼体系——蓝调半球光/HDRI IBL 打底、暖光只给日光本体，
    阴影偏蓝；雾色与天空 HDRI 地平线像素级对齐（防亮带复发）。
  - **素材源**（全 CC0，`tools/fetch-assets.sh` 可复现，含 itch 四步 API 流程）：
    - 植被/岩石：**Quaternius Stylized Nature MegaKit** 36 款 glTF
      （itch 5.0 满分包；104MB 整包选型+贴图压 1024 → 入库 18MB）；
    - 地面：**Poly Haven leafy_grass 2K 三件套**（diff + normal + rough）；
    - 天空/IBL：**Poly Haven kloofendal puresky 2K HDRI**（真云背景与环境光同源）；
    - 角色：**KayKit Adventurers Knight.glb**（76 段动画；加载失败落程序化兜底）；
    - 营地道具：**Kenney Nature Kit 12 款**（帐篷/篝火/栅栏等，风格低模但功能叙事成立）；
    - 光斑：three 官方 Lensflare 纹理（MIT）。
  - **风动草场**（grass.ts 系统）：近圈 Quaternius 草真模型 ×3200
    （onBeforeCompile 注入顶点风、全套 PBR 受影）+ 远圈自建刀片卡片 ×10600
    （取包内 Grass.png 色列、自定义着色器与雾/分级管线对齐），共 13800 株。
- **淘汰清单**：Kenney 全部植被/岩石（退场仅留道具）、toon gradientMap、
  canvas 手绘云（clouds.ts）、手绘天空渐变（sky.ts）、手绘太阳辉光、锥体远景林带
  （改用真树 Pine_2 环）。
- **后果**：素材包升级走 fetch 脚本而非手拷；Quaternius 下载依赖 itch API
  流程（失效时记录到脚本注释）；PBR 全场后移动性能压力上升（描边+后期+4K 阴影，
  M1 需真机性能档位开关）。

## ADR-003 平面气氛稿定位

- **状态**：已采纳（2026-09-24）
- `reports/2026-09-24-travel-rpg-scenes/` 平面 Canvas 稿仅为三站色板/构图参考，
  不再迭代、不作为交付物（是否入库由 Leo 另行拍板）。

## ADR-004 场景数据与屏内编辑器

- **状态**：已采纳（2026-09-24，it-003）
- **背景**：Leo 要求「尽量多沉淀资产，后面可以比较快速编排我的场景」。
- **决策**：
  - 场景摆放数据化：`SceneDef/Placement`（03-data-model.md），营地 hero 由硬编码迁移为
    `src/scenes.ts` 数据；**placements 派生草场/散布避让区**（唯一事实源）。
  - **屏内编辑器**（`?edit=1`，`src/editor.ts`）：资产面板来自 `catalog.json`（96 条，
    `tools/gen_catalog.py` 生成）；点地放置/拖拽/点选/R 旋转/± 缩放/Del 删除；
    **localStorage 即时持久化**（`travel-rpg:scene:<id>`，覆盖代码场景，刷新所见即所得）；
    导出 SceneDef 同构 JSON（剪贴板+下载）粘回 `src/scenes.ts` 即入库。
  - 编辑器内嵌于游戏页而非独立工具：所见即最终渲染（描边/后期/草场全在），零导出管线。
- **备选**（弃）：Blender/外部编辑器（割裂、要导出管线）；运行时写回仓库（需后端，过度工程）。
- **后果**：编辑只覆盖单场景 placements，环境散布仍为种子随机（要固化散布需扩 schema）；
  无撤销栈（重置默认兜底）；导出 JSON 需人工粘回代码库完成「入库」闭环。

## ADR-005 程序化水/火/生命（零新资产）

- **状态**：已采纳（2026-09-24，it-005）
- **背景**：塞尔达级精细度的三大结构性缺口（无水体、无可读路径、世界无生命），
  开源资产库无可信的水面/火焰/小动物资产；下载第三方质量参差且破 CC0 纪律。
- **决策**：全部程序化着色器/几何实现——
  - **达里湖**：湖盆刻进 `terrainHeight`（`lakeRadiusAt` 角向噪声 = 岸线唯一事实源，
    地形/水面/芦苇三处共用）；水面 = 单 Mesh 自定义着色器，CPU 预烤逐顶点水深
    属性（aDepth）驱动深浅混色/岸沫/波幅，无深度纹理依赖。
  - **篝火**：噪声上升流火焰交叉面片（NormalBlending，白昼可读；加法混合实测被
    亮背景洗白弃用）+ GPU 循环烟羽/火星（Points 顶点算生命周期，零 CPU 粒子）+
    闪烁点光（不投影）。
  - **石径**：贝塞尔弧长等距摆 RockPath 石板（**按水平最大边归一化**——扁平模型
    按「目标高度」归一化会把宽度放大 4-6 倍，首轮实机实锤后修正）。
  - **生命**：蝴蝶/鸟群全程序化（Lissajous 巡游 + M 形剪影盘旋）。
- **后果**：水面无真实反射（菲涅尔+波光近似，风格化可接受）；篝火非物理火焰；
  生命无 AI 状态机（纯轨迹）。性能全为实例化/Points，draw call 增量 <15。

### ADR-005 追记（it-006）

- 远山剪影环与云影同为程序化：山脊 = 角向 fbm 高度带（Basic 材质吃雾，零纹理）；
  云影 = 共享 uniform uCloudT + 可注入 GLSL 片段（cloud.ts），只覆盖地形与草场
  （散布树/道具不参与——全覆盖需逐材质注入，性价比低，风格化取舍）。
