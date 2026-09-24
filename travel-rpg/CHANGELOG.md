# Changelog · travel-rpg（赤峰环线）

## 2026-09-24

- **it-001 · M0 地基验证完成**：Vite 6 + TS strict + Three.js 0.171 工程从零立起；
  toon 地形（heightmap + PolyHaven 草地贴图 + 顶点色）、KayKit 骑士角色（76 段动画
  Idle/Walk/Run/Jump 状态机、缺剪辑回退、程序化兜底）、Kenney 20 款植被实例化
  （确定性布局）、第三人称弹性跟随镜头、键鼠 + 触屏（nipplejs 摇杆/跳按钮）双端控制；
  云端渐变天空 + 日轮光晕 + 三层布光 + 世界边缘无缝下沉。
  验证：构建零错误、双端行为断言全绿、控制台零报错；根因修复「天空 shader 缺 sRGB
  输出编码致地平线白带」。ADR-001/002 落档，素材下载脚本 `tools/fetch-assets.sh`。
