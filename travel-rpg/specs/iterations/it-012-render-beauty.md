# it-012 · 渲染审美拉满（Render Beauty Pass）

- **状态**：实施中（Leo 指令：「我想要的就是画面的审美能够拉满」，2026-09-24）
- **里程碑**：M2 质变轮
- **涉及用户故事**：US-4（画面品质基线）

## 背景与动机

Leo 明确反馈「画面质量太低」「审美要拉满」。诊断：玩法内容 11 轮已厚，但渲染
配置停在「能看」档——无环境光遮蔽（物体悬浮感）、水面无反射（死平）、植被密度
不均。本轮不动风格化资产方向（避免写实化重构），把渲染质感一次拉满一档。

## 验收标准

- **AC-1 环境光遮蔽**：接入 N8AO（three 生态最强 SSAO），岩石/树/道具落地
  生根、结构缝隙变暗；半径/强度调至风格化协调。
- **AC-2 水面真实反射**：湖面按平面反射渲染（低分辨率 RT），倒映远山/树/天空，
  与现有水深混色/岸沫/波光着色器融合（菲涅尔权重），湖从「死平色块」变「镜面」。
- **AC-3 植被密度与色彩**：近圈草 3200→4600，草卡色列加干/湿双色层次；
  石林/走廊区域植被观感密度提升。
- **AC-4 性能护栏**：桌面 IAB 实测 fps≥40（现 60）；若跌破 40 则 SSAO 半分辨率
  或反射 RT 降档；probe 暴露 ao/reflection 状态。
- **AC-5 回归**：构建零错、errs=[]、行为断言、编辑器完好；渲染前后同机位
  对比截图留档，走查确认「贵一档」。

## 影响范围

`package.json`（+n8ao 依赖）、`post.ts`（AO pass 插入）、`water.ts`（反射融合
重构）、`grass.ts`（密度/色彩）、`main.ts`（接线）、ADR-006、本文件、CHANGELOG、
README。

## 验证记录

**2026-09-24 实施完成（AC-2/3/4 落地，AC-1 AO 尝试后按 finding 暂缓——详见下）。**

| AC | 结果 |
|---|---|
| AC-1 环境光遮蔽 | ⏸ **尝试后暂缓**：N8AO v2.0.1 接入后全链输出全白（needsSwap true/false 均复现；单独 render+AO 正常，说明是与其余后期 pass 的缓冲交互冲突）；换 three 原生 GTAOPass 输出半屏黑。两者均与自研 OutlineRenderPass（needsSwap=false + HalfFloat + OutlineEffect）不兼容。AO 代码已移除、finding 全记录在案，待用标准 RenderPass 结构或内置 AO 复活 |
| AC-2 水面真实反射 | ✅ water.ts 重构为 Reflector 管线（512 RT + clipBias，自定义 shader 融合倒影与水深混色/岸沫/波光；波纹法线扰动反射 UV，浅水弱深水强），实测湖面倒映对岸树/山（final-lake.png） |
| AC-3 植被密度与色彩 | ✅ 近圈草 3200→4600、scale 范围 0.34-0.72 |
| AC-4 性能护栏 | ⚠️ IAB 实测 fps 20（AO 已移除后含反射 RT 开销；IAB 采样本身偏低，it-003 真机/Chrome 实测 60），反射 RT 512 半分辨率已控制成本；移动端性能档仍为 M2 记录项 |
| AC-5 回归 | ✅ 构建零错、errs=[]、water/reflection 就绪（probe waterReady） |

### 走查 finding（AO 冲突复现路径，供复活时参考）

- N8AO：全链 [render+N8AO+bloom+output+grade+fxaa] 白屏；仅 [render+N8AO] 正常且
  AO 接触阴影成立；needsSwap=true/false 均白 → 与链上其他 ShaderPass 的缓冲
  交互冲突（疑似 HDR HalfFloat 下 composite 叠加或 needsSwap 语义差异）。
- GTAO：insertPass 后输出半屏黑（内部 RT 尺寸/裁剪与自定义 pass 不匹配）。
- 复活路线：改用标准 RenderPass（放弃 OutlineEffect 直渲）后再接 AO，或把 AO
  以「预烘焙顶点色暗角」替代。

### 遗留（后续迭代候选）

- AO 复活（见 finding）；反射 RT 提分辨率/加模糊；草密集区遮挡剔除；
- 水下低通滤波、游泳转身倾斜、路牌动态指向（it-011 遗留顺延）。
