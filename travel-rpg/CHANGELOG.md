# Changelog · travel-rpg（赤峰环线）

## 2026-09-24

- **it-002 · 素材大换血 A 套 + 风动草场（Leo 确认实施）**：
  Quaternius Stylized Nature MegaKit 36 款植被/岩石（itch 四步 API 直链破解、
  脚本化 fetch、贴图压 1024 入库 18MB）替换 Kenney 植被；PolyHaven PBR 地面三件套
  （2K diff+normal+rough）；puresky HDRI 同时做真云背景与 IBL；官方 Lensflare 替代
  手绘光斑；sky.ts/clouds.ts 手绘天云代码删除、林带锥体换真树环；
  **风动草场 13800 株**（近圈真模型 onBeforeCompile 风注入 + 远圈色列卡片自定义着色器）；
  EffectComposer 后期链（描边渲染→Bloom→ACES 输出→暗角/饱和→FXAA，HalfFloat+MSAA×4）；
  白昼蓝影色彩纪律（4.1 日光/0.85 蓝半球/0.62 IBL）。自评 5.0→6.5 锁定；
  回归全绿（真实帧移动/同步 tick 跳跃/errs=[]/probe fps=60）。ADR-002 修订 + ADR-003。
- **it-001 · 视觉评审 + 高清化轮（回应「美工质量/高清」批评）**：
  系统性自审两轮（P0：零阴影/无分级/天空无层次/地面脏；P1：草花暗青/前景空/无描边）后落地——
  PCFSoft **4K 阴影**（太阳跟随玩家，toon 色阶拉开保住投影对比）、**ACES Filmic 分级**
  （天空手写同款曲线，include 方案编译失败致黑天已回退并记录）、three 官方 **OutlineEffect 描边**、
  Poly Haven **2K 草地贴图 + 2K HDRI 环境光（PMREM）**、**dpr=1 超采样 1.5×**、
  Kenney 扩充到 **52 款**（新增帐篷/篝火/栅栏/木柴/路牌/独木舟等营地道具 + 远景林带）、
  出生点营地框景、太阳光斑辉光、草花贴图染色；地平线亮带以像素取样对齐雾色消除；
  新增 `probe()/setOutline()` 评审探针与 console.error 捕获。
- **it-001 · M0 地基验证完成**：Vite 6 + TS strict + Three.js 0.171 工程从零立起；
  toon 地形（heightmap + PolyHaven 草地贴图 + 顶点色）、KayKit 骑士角色（76 段动画
  Idle/Walk/Run/Jump 状态机、缺剪辑回退、程序化兜底）、Kenney 20 款植被实例化
  （确定性布局）、第三人称弹性跟随镜头、键鼠 + 触屏（nipplejs 摇杆/跳按钮）双端控制；
  云端渐变天空 + 日轮光晕 + 三层布光 + 世界边缘无缝下沉。
  验证：构建零错误、双端行为断言全绿、控制台零报错；根因修复「天空 shader 缺 sRGB
  输出编码致地平线白带」。ADR-001/002 落档，素材下载脚本 `tools/fetch-assets.sh`。
