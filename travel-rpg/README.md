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

- 桌面：WASD/方向键移动（Shift 跑）、鼠标拖拽环视、空格跳、滚轮缩放。
- 触屏：左下摇杆移动（推满跑）、右侧拖拽环视、右下「跳」按钮。
  桌面想自测移动 UI：URL 加 `?touch=1`。

## 技术与素材

- Three.js 0.171 + Vite 6 + TS strict（ADR-001）；描边 three 官方 OutlineEffect；
  触屏摇杆 nipplejs；色调 ACES Filmic + PCFSoft4K 阴影（太阳跟随玩家）。
- **高清规格**：地面 Poly Haven **2K** 贴图（anisotropy16）+ HDRI **2K** 环境光（PMREM）
  + dpr=1 设备 **1.5× 超采样**渲染；全场景 MeshToonMaterial cel-shading + 顶点色（ADR-002）。
- 素材（全部 CC0，`tools/fetch-assets.sh` 可复现下载）：
  - 角色：**KayKit Adventurers `Knight.glb`**（76 段动画，Idle/Walking/Running/Jump 状态机；
    加载失败自动回落程序化斗笠旅人）；
  - 环境：**Kenney Nature Kit 52 款**（14 树/9 岩/4 灌木/6 草/7 花/12 营地道具
    ——帐篷、篝火、栅栏、木柴、路牌、独木舟……InstancedMesh）；
  - 地面：**Poly Haven `leafy_grass` 2K** × 顶点色 + 大尺度明暗斑块；
  - 环境光：**Poly Haven `spruit_sunrise` 2K HDRI**。
- 出生点营地（帐篷+篝火+栅栏+木柴）+ 远景林带剪影 + 太阳光斑辉光；
  三站色板/光位迁自平面气氛稿 `reports/2026-09-24-travel-rpg-scenes/`。

## 调试钩子

- `window.__game.tick(frames, dtMs?)`：同步步进游戏循环（内嵌浏览器 RAF 挂起时的断言通道）。
- `window.__game.state()`：pos / onGround / model / camYaw / camDist / drawCalls / errors。
- `window.__game.probe()`：阴影贴图/描边开关/散布加载数/HDRI 状态。
- `window.__game.setOutline(bool)`：运行时开关描边（对照实验）。
- `errors` 现已同时捕获 `console.error`（shader 编译失败只走 console，曾漏检导致黑天）。
