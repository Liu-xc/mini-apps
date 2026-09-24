# it-001 · M0 Spike：Three.js 地基验证

- **状态**：已实施（2026-09-24）
- **用户故事**：US-1
- **里程碑**：M0（详见 ../00-overview.md）

## 背景与动机

方向已拍板（ADR-001/002）：塞尔达式 3D 网页游戏，PC + mobile 一套代码。
M0 要用最小代价验证「项目成立」的四个假设：Three.js 双端跑得动、角色跑跳手感成立、
跟随镜头可玩、glTF 素材管线（下载→加载→动画混合）通。

## 验收标准

见 ../01-user-stories.md US-1 的 AC-1-1 ~ AC-1-5。

## 影响范围

- 新建应用目录 `travel-rpg/`（Vite 工程 + specs）。
- 新建 specs：00-overview、01-user-stories、06-decisions（ADR-001/002）、本文件。
- 02~05 常青 spec 暂缓至 M1/M2（见 00-overview 索引表）。
- 平面气氛稿降级为美术参考：`reports/2026-09-24-travel-rpg-scenes/`（不再迭代）。

## 验证记录

**2026-09-24 实施完成，AC-1-1 ~ AC-1-5 全部通过。**

### 构建（AC-1-5）

- `npm run build`（tsc strict + vite build）零错误；产物 dist/ 含 Knight.glb、
  20 个 Kenney 植被 GLB、leafy_grass 贴图。
- 浏览器 `window.__game.errors` 全程为空，页面错误角标未触发，控制台零报错。
- 已知项：主 chunk 639KB（gzip 166KB，three.js 本体）——超 500KB 警告，M1 再做分包。

### 行为断言（页面内 `__game.tick(n, dtMs)` 同步步进驱动）

> 环境坑：IAB 内嵌浏览器 RAF 挂起（`requestAnimationFrame` 探测 TIMEOUT，
> 截图时才泵帧），为此在 main.ts 留了同步步进钩子，断言确定性可复现。

| 断言 | 结果 |
|---|---|
| W 前进 70 帧（AC-1-1 移动） | pos z 0 → -4.84，相对镜头方向正确 |
| S + Shift 跑 50 帧 | 反向移动 ≈7.2 u/s（跑速 7.6 含加速段），相对旋转后的镜头方向正确 |
| 空格跳跃（AC-1-1/1-3） | y 0 → 1.45（onGround false）→ 落地 y 0（onGround true），重力弧线与落地正常 |
| 长距行走进起伏地形 | 420 帧后 y=-1.72 且 onGround=true，贴合谷地 |
| 鼠标拖拽环视（AC-1-4） | 合成 pointer 事件后 camYaw 0 → -0.5，阻尼跟随 |
| 滚轮缩放（AC-1-1） | camDist 7.5 → 3.5（下限钳制生效） |
| 移动端 390×844 `?touch=1`（AC-1-2） | body.touch=true，摇杆/跳按钮 display=block，提示文案切换；跳按钮 y 0→1.42→落地；首轮另验 stickVisible/jumpVisible/hint 三断言 |
| 模型与动画 | `model='knight'`（KayKit 加载成功，失败才落程序化角色）；Idle/Walking_A/Running_A/Jump_Idle 候选映射，缺剪辑自动回退 |
| 朝向 | 行走后截图确认背面朝镜头（头盔无面甲侧），无需镜像偏移 |

- 渲染规模：58 draw calls / 16.3 万三角形（含全部实例化植被），桌面流畅。
- 双端截图：桌面终版正常；移动端截图受 IAB 小视口影响（surface 超时/旧帧），
  触屏 UI 以 390×844 的 DOM 断言 + 早期轮截图（摇杆+骑士同框）为准。

### 素材落地（ADR-002，回应「避免自己搞得太丑」）

| 素材 | 来源/许可 | 接入结果 |
|---|---|---|
| Knight.glb（3.6MB，76 段动画） | KayKit Adventurers，CC0，jsDelivr 直链 | 主角，Idle/Walk/Run/Jump 状态机 |
| 20 × Kenney Nature Kit GLB（216KB） | Kenney，CC0，官方 zip 抽子集 | 8 树（全秋色系）+5 岩+2 灌木+2 草+3 花，InstancedMesh |
| leafy_grass_diff_1k.jpg | Poly Haven，CC0 | 地形漫反射 × 顶点色，repeat40 + anisotropy8 |

- 下载可复现：`tools/fetch-assets.sh`（含全部直链与抽取清单）。
- 散布随机数全部同步预计算，刷新布局完全一致。

### 过程中定位并修复的根因地基问题

1. **地平线白带**：自定义天空 ShaderMaterial 不经过 three 输出色彩空间转换，
   线性值直出比走正规管线的雾/暗一半 → 片元补 `pow(col, 1/2.2)`，
   像素取样确认地平线 (238,215,170) 与天空平滑过渡，接缝消失。
2. 世界边缘地形与底板接缝 → `terrainHeight` 96~116 半径平滑下沉至 UNDERLAY_Y，
   玩家加 90 半径圆钳制。
3. 背光黑剪影 → 主光 + 镜头侧补光 + 半球光三层布光。

### 环境坑速查（后续迭代复用）

- npmmirror 无 `@types/nipplejs`；nipplejs 1.0.4 自带类型，直接用。
- `raw.githubusercontent.com` SSL 抖动 → jsDelivr（`cdn.jsdelivr.net/gh/...`）镜像。
- IAB 截图旧帧/小视口 surface 超时 → 断言走 DOM + `__game.tick`，截图仅作辅证。
- 「石林/达里湖」字样的截图是平面气氛稿旧帧污染，属同会话双标签页截图串帧，
  看 URL/内容区分（气氛稿在 :8791，游戏在 :5199）。

### 遗留（进 M1）

- 草叶/花色调偏暗青，待 M1 与贴图统一调色；天空蓝顶需抬头视角验证。
- 程序化云仅一层 Sprite，M1 计划做体积感与投影扫地。
