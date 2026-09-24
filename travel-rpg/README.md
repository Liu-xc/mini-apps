# travel-rpg · 赤峰环线

塞尔达式 3D 网页 RPG：把赤峰环线自驾（石林 → 达里湖 → 乌兰布统）做成可探索的游戏世界。
PC web + mobile web 一套代码。specs 先行（AGENTS.md），当前进度见
[`specs/iterations/`](specs/iterations/)。

## 构建与运行

```bash
cd travel-rpg
npm install
npm run dev        # http://localhost:5199（host 模式，手机同 WiFi 可用局域网 IP 访问）
npm run build      # tsc --noEmit + vite build → dist/
npm run preview    # 预览生产构建
```

- 桌面：WASD/方向键移动（Shift 跑）、鼠标拖拽环视、空格跳、滚轮缩放、**T 换时段**
  （day/dawn/sunset，`?time=` 可直达）、**G 快速旅行/换站**（三站同世界可步行互通，`?scene=` 选出生站）、
  **E 上/下船**（深水可游泳）。
- 触屏：左下摇杆移动（推满跑）、右侧拖拽环视、右下「跳」按钮。
  桌面想自测移动 UI：URL 加 `?touch=1`。

## 技术与素材（it-002 现行）

- Three.js 0.171 + Vite 6 + TS strict（ADR-001）；**EffectComposer 后期链**：
  描边渲染（OutlineEffect）→ UnrealBloom → ACES 输出 → 暗角/饱和 → FXAA；
  PCFSoft 4K 阴影（太阳跟随玩家）；触屏摇杆 nipplejs。
- **画面路线 = A 套精细风格化（ADR-002）**，素材全 CC0、`tools/fetch-assets.sh` 一键重下
  （含 Quaternius itch 四步 API 流程）：
  - 植被/岩石：**Quaternius Stylized Nature MegaKit 36 款**（评分 5.0 的口碑包）；
  - 地面：**Poly Haven leafy_grass 2K 三件套**（diffuse+normal+roughness）；
  - 天空/IBL 同源：**Poly Haven puresky 2K HDRI**（真云背景 + 环境反射）；
  - 角色：**KayKit Adventurers Knight.glb**（76 段动画，失败回落程序化兜底）；
  - 营地道具：**Kenney 12 款**（帐篷/篝火/栅栏/木柴）；光斑：three 官方 Lensflare。
- **风动草场**：13800 株（近圈真模型+顶点风注入 / 远圈自建卡片取包内色列），probe 实测 60fps；
  走近 1.5m 草叶推开避让（it-005）。
- **交互与三站（it-008）**：游泳（深水浮游）、canoe 划船（上/下船+水面驾驶）、
  三站 preset（石林/达里湖/乌兰布统 = 场景摆放+时段+出生点）。
- **水岸与生命（it-005）**：达里湖湖湾（单 Mesh 水面着色器：水深混色/岸沫/波光，
  浅滩涉水减速）、营地→湖 RockPath 石径、岸线芦苇、程序化篝火（火舌/烟/火星/闪烁光）、
  蝴蝶×8+鸟群×6、脚步尘土/落地尘雾/涉水涟漪；地面反平铺双尺度采样。
  全部程序化或激活已入库 CC0 资产（ADR-005）。
- 三站色板/构图参考自平面气氛稿 `reports/2026-09-24-travel-rpg-scenes/`（ADR-003）。

## 场景编排（it-003）

- **`?edit=1` 屏内编辑器**：左侧资产面板（按 catalog 分类）→ 点地面放置 →
  拖动已放物体移动 → 点选后 `R` 旋转 / `+/-` 缩放 / `Del` 删除 / `Esc` 取消；
  空白处拖动环视。每次改动**即时写 localStorage，刷新所见即所得**；
  「导出 JSON」= 复制到剪贴板 + 下载 `<scene>.scene.json`，粘回 `src/scenes.ts` 即入库；
  「重置默认」清除本地覆盖、回落代码场景。
- 场景数据：`src/scenes.ts`（schema 见 `specs/03-data-model.md`）；
  资产目录：`public/assets/catalog.json`（`tools/gen_catalog.py` 再生成）。

## 调试钩子

- `window.__game.tick(frames, dtMs?)`：同步步进游戏循环。
  ⚠️ IAB 内嵌浏览器 RAF 会在调用间隙挂起、且后期链下 **批量 ≈20 帧/调用** 为上限
  （3 秒调用预算）；「派发输入 + 同步 tick」必须在同一次 evaluate。
- `window.__game.state()`：pos / onGround / model / camYaw / camDist / calls / errors
  （注：calls/tris 反映 composer 末尾全屏 pass，恒为 1，非场景统计）。
- `window.__game.probe()`：阴影/描边/散布/草场/背景/IBL 就绪状态与 fps。
- `window.__game.setOutline(bool)`：运行时开关描边（对照实验）。
- `errors` 同时捕获 `console.error`（shader 编译失败只走 console）。
