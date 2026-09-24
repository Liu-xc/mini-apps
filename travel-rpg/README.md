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

- Three.js 0.171 + Vite 6 + TS strict（ADR-001）；触屏摇杆 nipplejs。
- 全场景 MeshToonMaterial cel-shading + 顶点色（ADR-002）。
- 素材（全部 CC0，`tools/fetch-assets.sh` 可复现下载）：
  - 角色：**KayKit Adventurers `Knight.glb`**（76 段动画，Idle/Walking/Running/Jump 状态机；
    加载失败自动回落程序化斗笠旅人）；
  - 植被：**Kenney Nature Kit** 子集 20 款 GLB（8 秋色树/5 岩/2 灌木/2 草/3 花，InstancedMesh）；
  - 地面：**Poly Haven `leafy_grass`** 1K diffuse × 顶点色。
- 三站色板/光位迁自平面气氛稿 `reports/2026-09-24-travel-rpg-scenes/`。

## 调试钩子

- `window.__game.tick(frames, dtMs?)`：同步步进游戏循环（内嵌浏览器 RAF 挂起时的断言通道）。
- `window.__game.state()`：pos / onGround / model / camYaw / camDist / drawCalls / errors。
