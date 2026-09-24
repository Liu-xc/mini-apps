# Changelog · travel-rpg（赤峰环线）

## 2026-09-24

- **it-010 · 站间路上内容与水花过渡**：**路牌+走廊散布**（三站出口 kenney sign、
  两条路线走廊沿中心线 ±8m 确定性撒布岩/灌木/花/枯木 ~40 件、中点巨石对+倒木
  小地标，营地 12m/水域自动避开）；**入水/出水水花**（onSwimChange 触发白沫
  纵向喷溅，probe splash 计数）；**区域雾色过渡**（STATIONS tint：石林暖沙/
  乌兰布统暖金，站域 25m 全量→60m 消退 lerp，实测三站雾色 hex 分化）。
  回归全绿（构建零错/errs=[]/行为断言/编辑器 world 48 摆放）。规格见
  specs/iterations/it-010-route-splash-regionfog.md。
- **it-009 · 游泳姿态、世界连通与手感**：**程序化蛙泳**（髋部枢轴俯卧 1.35rad +
  mixer 后骨骼覆写：双臂对称划水/双腿打腿——KayKit 无游泳剪辑的风格化方案，
  R1 修「屈臂 idle×俯卧=蜷球」改直身 idle 再压平）；**三站合并同世界**（placements
  44 件共存，石林/乌兰布统偏移入数据，站间可步行；G 改快速旅行瞬移无刷新，
  区域徽标站域 20m 切换）；船手感（MAX_FWD 4.4/TURN 1.9/骑乘镜头 +1.5/尾迹随速）；
  草避让 0.75/1.8。回归全绿（构建零错/errs=[]/快旅+移动+跳跃断言/probe+region·bones）。
  规格见 specs/iterations/it-009-swim-pose-world-feel.md。
- **it-008 · 游泳、划船与三站**：**游泳**（水深>1.0m 贴水面浮游+起伏+划水涟漪，
  KayKit 无游泳剪辑实证后 unarmed_idle 优雅回退，滞回回岸）；**划船**（canoe 载具化：
  近船 E 提示、上下船衔接游泳/涉水、W/S 推进 A/D 转向、碰岸回弹、搁浅推离、尾迹
  涟漪、泊位移离岸避开散布岩+模型长轴自动对齐）；**三站 preset**（STATIONS 表=
  场景+时段+出生点：石林白昼/达里湖湖湾/乌兰布统黄昏，?scene= 直达+G 循环，
  polyhaven boulder_01 等首次上场）。回归全绿（构建零错/errs=[]/骑乘位移与游泳
  切换断言/probe+station·swimming·boating·clipNames）。规格见
  specs/iterations/it-008-swim-boat-stations.md。
- **it-007 · 时段光照系统**：day/dawn/sunset 三档预设一键切换（T 键 / `?time=`）——
  三张 puresky HDRI 背景+IBL 旋转换装、雾色/雾距、日光色/强/仰角方位（黄昏 25° 长影）、
  半球/补光、环境强度、分级饱和度、草地/水体着色器全联动；山脊 4 频段细分。
  R1 修 TDZ 崩模块 + 黄昏仰角/饱和/HDRI 旋转三连调。回归全绿（fps 60/行为断言/
  probe+time）。规格见 specs/iterations/it-007-time-of-day.md。
- **it-006 · 大气与远景（it-005 遗留直入）**：**远山剪影环**（两层 fbm 山脊
  r=188/142，Basic 吃雾成剪影，地平线白带被群山替代）、**雾色重校**（#cfe0ec→#c3d5e2，
  地平线不再读作发光远海）、**云影漂移**（cloud.ts 共享 uCloudT，地形+草近/远圈
  着色器注入，大尺度噪声斑过境）、**太阳呼吸**（光强慢噪声轻起伏）。回归全绿
  （构建零错/errs=[]/行为断言/编辑器完好，probe+mountainLayers）。规格见
  specs/iterations/it-006-atmosphere-vista.md。
- **it-005 · 水岸与生命（Leo /goal：「持续迭代→review→再迭代，塞尔达级精细度」）**：
  **达里湖**（湖盆刻进 terrainHeight + 单 Mesh 水面着色器：水深混色/岸沫/太阳波光/
  菲涅尔天空/雾对齐，浅滩可涉水——地面钳制膝深减速）、**营地→湖石径**（RockPath
  全 10 款激活，扁平模型按水平最大边归一化防爆宽）、**岸线芦苇带** + canoe 入
  placements（激活未上场资产）、**地面反平铺**（世界坐标双尺度采样+宏观噪声混合）
  + 岸线湿沙环、**篝火全程序化**（不透明火舌+GPU 烟羽/火星+闪烁点光）、**环境生命**
  （蝴蝶×8 + 鸟群×6）、**草避让玩家** + 脚步尘土/落地尘雾/涉水涟漪。两轮视觉走查
  闭环（R1 三 P0 全修：石板爆宽/奶白水/火焰洗白）；回归全绿（构建零错/errs=[]/
  行为断言/编辑器 15 摆放完好/双模式截图）。ADR-005。规格见
  specs/iterations/it-005-shore-and-life.md。
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
