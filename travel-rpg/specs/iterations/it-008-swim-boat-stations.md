# it-008 · 游泳、划船与三站（Swim · Boat · Stations）

- **状态**：实施中（Leo /goal 常设指令：「持续迭代→review→再迭代」，2026-09-24；
  it-007 遗留两项直入）
- **里程碑**：M1 交互层 → M2 三站地基
- **涉及用户故事**：US-2/US-3（移动/探索交互延伸）、US-5（场景编排）

## 背景与动机

it-005 的湖只有「膝深隐形地板」——塞尔达式的水域应该能游；canoe 修好尺度后仍是
岸饰。同时世界只有一个营地：石林/达里湖/乌兰布统三站的情绪差异（石阵白昼/湖湾
清晨/林地黄昏）尚未成形。本轮补水域交互与三站骨架。

## 验收标准

- **AC-1 游泳**：水深 >1.0m 进入游泳（贴水面浮游、速度降、身体起伏、涟漪尾迹），
  岸边自动回行走；水深 <1.0m 保持 it-005 涉水；Knight 无游泳剪辑时用现有剪辑优雅
  回退（probe 暴露 clipNames 核实后映射）。
- **AC-2 划船**：靠近 canoe 出现「E 上船」提示；上船后水面驾驶（W/S 推进、A/D
  转向、拖拽减速、浅水/岸线阻挡、波浪起伏+艏摇），再按 E 下船（优先岸边）；
  下船即衔接游泳/涉水状态；编辑模式不生效。
- **AC-3 三站 preset**：`STATIONS` 表 = 场景摆放集 + 时段 + 出生点：
  **shilin 石林**（石阵白昼，激活 Poly Haven boulder_01/rock_07 等未上场资产）、
  **dali 达里湖**（营地湖湾）、**wulan 乌兰布统**（林地黄昏，树阵+栅栏）；
  `?scene=` 直达、`G` 键循环（带参跳转，资产走缓存快切），出生点随站。
- **AC-4 回归**：构建零错、errs=[]、行为断言（移动/跳跃/上船/下船/游泳切换）、
  编辑器 camp 默认不受影响、probe 扩展（swimming/boating/station/clipNames）、
  截图走查（游泳视角/划船视角/三站各一张）。

## 影响范围

新增 `src/canoe.ts`；修改 `player.ts`（游泳状态）、`scenes.ts`（两站 placements +
STATIONS 表）、`main.ts`（接线/键位/hint/probe）；本文件验证记录、CHANGELOG、
README。

## 验证记录

**2026-09-24 实施完成，AC-1 ~ AC-4 全部通过。**

| AC | 结果 |
|---|---|
| AC-1 游泳 | ✅ 深水(>1.0m) swimming=true、贴水面 y=-1.57（=水位-0.22）带起伏、SWIM_SPEED 2.6、划水涟漪尾迹（stride 1.1m）；滞回退出（<0.85m）回涉水/行走；KayKit 实证无游泳剪辑（全 76 段清单核过）→ 回退 unarmed_idle（垂臂踩水）；probe hasSwimClip/clipNames 暴露 |
| AC-2 划船 | ✅ 近船「E 上船」提示条（#prompt，触屏可点）→ 骑乘玩家隐身、W/S 推进 A/D 转向、泊位(37.5,-26.5)推离入水后实驾 5m 航程+转向、碰岸回弹、尾迹涟漪、「E 下船」落船侧衔接涉水；R1 修三处：①「仅水面可登船」把滩上泊位锁死→移除；②搁浅推离按帧衰减(×0.5/帧=急刹)→按 dt；③泊位恰有随机散布巨石遮挡→移至离岸浮位+模型长轴自动对齐艏向 |
| AC-3 三站 | ✅ STATIONS 表：shilin 石林·白昼（polyhaven boulder_01/rock_07/rock_09/moss/tree_stump 首次上场+石阵摆位）、dali 达里湖·湖湾（camp）、wulan 乌兰布统·黄昏（林地营地+sunset）；?scene= 直达+G 循环；hint 加站名徽标；canoe 从 placements 移除（转正为载具，14 件） |
| AC-4 回归 | ✅ 构建零错；errs=[]；行为断言过（登船/驾驶位移/下船/游泳切换/移动）；probe +station/swimming/boating/hasSwimClip/clipNames；三站+游泳+骑乘截图（s-swim/b2-riding/st-*） |

### 走查备注

- 游泳姿态为静帧回退（KayKit 无游泳剪辑），众目可接受；若换混合动画需重导模型。
- 骑乘时 hint 与 prompt 分工清楚（hint=操作说明，prompt=情境交互）。
- 编辑器：canoe 移出 placements 后营地 14 件不受影响；新增两站 placements 均可被
  `?scene=shilin&edit=1` 编排（数据通路共用）。

### 遗留（后续迭代候选）

- 游泳动画（换带游泳剪辑的模型或程序化上身摆动）；船速/转向手感调参；
- 站点间步行连通（M2 世界拼合）、每站独立天空盒色调微调；
- 船上岸即锁死的边界艺术化处理（搁浅倾斜）。
