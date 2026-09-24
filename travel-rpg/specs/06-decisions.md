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
- **后果**：渲染/素材/教程全生态对齐 Three.js；升级需同步 `three` 与 `@types/three` 同 minor。

## ADR-002 素材与渲染策略：CC0 成套素材 + cel-shading 藏拙

- **状态**：已采纳（2026-09-24）
- **背景**：自绘易丑；「塞尔达感」本质是 NPR 卡通渲染而非写实。
- **决策**：
  - 素材只用高口碑 CC0/许可干净来源：**Quaternius / Kenney / KayKit（模型与动画）、
    Poly Haven（HDRI/贴图）、Mixamo（动作，需注意其许可不可再分发）**；格式统一 glTF/GLB。
  - 全场景 **MeshToonMaterial（cel-shading，3 阶 gradientMap）+ 顶点色**，风格化掩盖低精度素材。
  - 天空/雾/三站色板**迁移平面气氛稿**（reports/2026-09-24-travel-rpg-scenes）的既定配色。
- **后果**：禁止写实 PBR 混搭；新素材入库前先核许可（CC0 可商用无署名，CC-BY 需在 README 署名）。
- **M0 注（it-001 已落地）**：角色用 **KayKit Adventurers `Knight.glb`**（CC0、
  jsDelivr 直链、76 段动画），植被用 **Kenney Nature Kit** 子集 20 款（CC0），
  地面用 **Poly Haven `leafy_grass`**（CC0）；下载与抽取清单固化在
  `travel-rpg/tools/fetch-assets.sh`。调研中 Quaternius 系因 Google Drive 无直链弃用、
  Michelle.glb 因缺走跑动画弃用。原计划的 three.js 示例 Soldier.glb 已不使用。
