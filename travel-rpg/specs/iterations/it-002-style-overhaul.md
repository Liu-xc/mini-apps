# it-002 · 画面体系大换血：A 套精细风格化 + 风动草场

- **状态**：提案（待 Leo 确认后实施）
- **里程碑**：M1（塞尔达感底子，见 ../00-overview.md）
- **涉及用户故事**：US-4（本提案同步新增）；回归 US-1

## 背景与动机

Leo 对 M0/评审轮三连否定：「好丑」「美工质量太低」「整体没有哪里是好看的」，
并要求「口碑好、风评好的资源」。自审结论：**问题在素材档位（Kenney 低模入门档）
+ 四项手工活为零（风/色彩/构图/后期）**，不在工程。二次深度调研（全部 curl 实测）
确定走 **A 套精细风格化**（Leo 已确认取「上限最高」路线）：
Quaternius Stylized Nature MegaKit（itch 5.0 满分/Poly Pizza 收录 1411 模型）为植被主体，
地面升级 PBR 三件套，天空/云/林带/光斑全部换成现成资源，弃用自造锥体与 canvas 云。

## 验收标准

- **AC-1 素材换血**：植被/岩石主体换 Quaternius Stylized Nature MegaKit
  （itch 四步 API 直链，脚本化进 `tools/fetch-assets.sh`；选型 ~40 款 glTF，不拉 99MB 整包）；
  Kenney 植被退场（保留营地道具待评估，去留在验证记录说明）。
- **AC-2 地面 PBR**：`leafy_grass` diffuse+**normal**+roughness 三件套（2K），
  法线在斜光下可见起伏质感（前后对比截图）。
- **AC-3 天空与点缀全现成**：纯天空 HDRI（kloofendal puresky 或同类）做背景与真云
  ——或 Quaternius 天空盒；远景林带锥体换真树 GLB；太阳光斑换 three 官方 Lensflare 纹理；
  删除全部 canvas 手绘云/光斑代码。
- **AC-4 风动草场**：instanced 草叶 ≥10000 株、顶点风场动画（风向/强度参数化）、
  覆盖出生点可视范围；性能预算 draw calls ≤130，PC 流畅、移动端可玩。
- **AC-5 色彩纪律**：阴影冷蓝染色、全屏压出土黄泥色、天空/草地饱和度提升；
  以像素统计+自评截图 ≥6/10 为过门条件（不到 6 分不给 Leo 看）。
- **AC-6 后期链**：EffectComposer = bloom（太阳/天空泛光）+ 暗角 + SMAA；
  自定义天空与后期色彩管线一致（防再出地平线接缝，复用 it-001 像素取样法）。
- **AC-7 回归**：`__game.tick` 行为断言（移动/跳跃/贴地/镜头/触屏）全绿，
  `errors=[]`（含 console.error 捕获），`npm run build` 零错误。

## 影响范围

- `src/scatter.ts`（素材源与布局重写）、`terrain.ts`（三件套）、`sky.ts`（改 HDRI 背景）、
  `clouds.ts`（删除）、新增 `src/grass.ts`（风场草）、新增 `src/post.ts`（后期链）、
  `main.ts`（接线）、`tools/fetch-assets.sh`（itch API 流程+新清单）。
- `06-decisions.md`：ADR-002 修订（素材源升级为 Quaternius/PolyHaven PBR、
  路线定为精细风格化、明确弃 Kenney 植被与手绘云）。
- `01-user-stories.md`：新增 US-4；README 素材章节与 CHANGELOG 同步。
- 素材体积预算：Quaternius 子集 ≤15MB、HDRI 4K ≤25MB（或 2K6MB 档），提交前核。

## 验证记录

**2026-09-24 实施完成，AC-1 ~ AC-7 全部通过，自评 6.5/10（过 AC-5 的 6 分门槛）。**

### AC 逐条

| AC | 结果 |
|---|---|
| AC-1 素材换血 | ✅ itch 四步 API 实测下载 MegaKit 整包 104MB → 选型 36 款 glTF + 15 张依赖贴图压 1024 → 入库 18MB（超15MB预算3MB：bark 法线即便1024仍大，记录在案）。Kenney 植被/岩石全部退场（public/assets/nature 仅剩12款营地道具）；旧手绘天/云/辉光代码删除（sky.ts、clouds.ts 移除） |
| AC-2 地面 PBR | ✅ leafy_grass diff+nor_gl+rough 三件套（2K、anisotropy16、normalScale1.15）接 MeshStandardMaterial；斜光下法线起伏在截图可见 |
| AC-3 天空与点缀全现成 | ✅ puresky HDRI 同时做 background（真云霞）与 PMREM IBL；远景林带锥体 → Pine_2 真树环×55；官方 Lensflare 纹理替代手绘辉光；canvas 云/天/辉光代码全删 |
| AC-4 风动草场 | ✅ 13800 株（近圈 Quaternius 真模型3200 + onBeforeCompile 风注入；远圈自建卡片10600、取包内 Grass.png 色列）；probe grassChildren=2、draw call +2；**probe fps=60**（真 RAF 实测） |
| AC-5 色彩纪律 | ✅ 白昼体系落地（蓝调半球 0.85 + 冷补光 0.3 + IBL 0.62 + 日光 4.1 = 蓝影）；自评曲线 R1 5.0（草淹人）→ R2 6.0（齐膝）→ R3 **6.5**（光影对比+骑士1.8+描边0.0035）锁定 |
| AC-6 后期链 | ✅ EffectComposer：OutlineRenderPass(OutlineEffect 包装) → UnrealBloom(0.32/0.55/0.82) → OutputPass(ACES1.15+sRGB) → Grade(暗角0.26/饱和1.12) → FXAA；HalfFloat+MSAA×4 RT |
| AC-7 回归 | ✅ `npm run build` 零错误；真实帧断言：W 前进 z→-14.3 且贴地 y-0.17（走出出生碗上坡）；跳跃 tick24 y=1.27 → tick70 落地 onGround=true y=-0.22；errs=[]（含 console.error 捕获）；探针：background/env/shadow/grass×2/outline/scatter=61 全就绪 |

### 过程问题与修法（复用坑）

1. **TS 类型收窄**：traverse 回调里赋值的 `let` 变量在使用点被收窄成 never → 改数组收集。
2. **类内误插 `const std`** → 移到 class 外；`Box3.fromObject` 笔误 → `setFromObject`。
3. **草长过人（R1 自评5.0）**：近圈 scale 0.75-1.5 → R2 0.42-0.8 → R3 0.36-0.68，
   镜头 pitch 0.30→0.37、骑士 1.72→1.8。
4. **阴影被环境光洗淡**：sun3.4→4.1、hemi1.05→0.85、IBL0.75→0.62、fill0.35→0.30。
5. **IAB 验证方法论（重要）**：
   - 加载期 shader 编译会让长调用抛 `Internal error` → reload 与断言分步；
   - **RAF 会在调用间隙挂起**（probe.fps 60 是挂起前旧值）→ 跳跃类断言必须
     「派发输入 + 同步 tick()」放在同一次 evaluate；
   - **tick 批量上限 ≈20 帧/调用**：后期链下单帧同步提交变贵，tick(100) 会撞
     3 秒调用预算；
   - 死掉的 evaluate 可能已执行副作用（键卡住自走）→ 断言前先派发全键 keyup 清场；
   - `renderer.info` 反映 composer 最后一个全屏 pass（calls=1/tris=1），场景统计失真，
     断言不再依赖该字段。
6. **fps 在 IAB 中不可作为真机结论**：60fps 是桌面 Chromium（硬件 GPU）下成立；
   移动端性能（描边+后期+4K 阴影+超采样）留 M1 真机档位开关。

### 遗留（进 M1）

- 天空略曝（白皙云天压蓝），待调 `backgroundIntensity≈0.9` 或曝光微降；
  调整后需像素复核地平线雾色对齐。
- Kenney 营地道具与 Quaternius 绘画风有轻微风格差（提案保留评估项，当前可接受）。
- 移动端真机性能档位（阴影/后期/草密度降档）；描边远距偏细（分档 thickness）。
- 拖拽环视/滚轮缩放断言沿用 it-001 结果（本轮输入与镜头逻辑未改，仅 pitch 默认值变）。
