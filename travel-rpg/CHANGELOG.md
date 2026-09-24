# Changelog · travel-rpg（赤峰环线）

## 2026-09-24

- **it-004 · 精细度打磨（Leo：「继续打磨精细度」）**：分级升级（S 形对比+冷阴影/
  暖高光分离调+时变胶片颗粒）、天空 `backgroundIntensity 0.94` 压曝（像素复核地平线）、
  草场**阵风波**（沿 (1,0.6) 移动 gust 带，近圈/远圈同式）、花/三叶草/灌木分级随风摆
  （共享 swayTime 时钟）、树叶 emissive 透光、**激活入库未上场资产**（Petal 落瓣×46 +
  小碎石×44，scatter 47→54）、花粉光尘 Points×70 随玩家漂移。回归全绿（构建零错/
  errs=[]/行为断言/编辑器完好）。规格见 specs/iterations/it-004-fidelity-pass.md。
- **it-003 · 资产库沉淀 + 屏内场景编排器（Leo：「继续大搞特搞，尽量多沉淀资产，快速编排场景」）**：
  Quaternius MegaKit 扩到**全量 68 款**（156 文件 25MB）；PolyHaven 草原模型**精选 7 款 17.2MB**
  （API 权威 include 清单下载、18MB 预算）；**三时段 puresky HDRI**（day/dawn/sunset）；
  `catalog.json` 96 条 + `gen_catalog.py`；场景数据化（`scenes.ts`/`placer.ts`，
  营地 14 件迁移、避让区数据派生）；**`?edit=1` 编辑器**（分类面板/点地放置/拖拽/
  旋转缩放删除/localStorage 持久化/导出 SceneDef JSON/重置默认），controls 加 orbit 开关；
  新建 03-data-model + ADR-004 + US-5。回归全绿：普通模式构图一致 placement=14、
  行为断言过、编辑器冒烟过（14→15/导出合法/LS 持久化/重置回落）、构建零错。
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
