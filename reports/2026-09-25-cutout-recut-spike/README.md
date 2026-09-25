# 补抠 spike 报告（it-040 阶段 A）· 2026-09-25

**问题**：原图即弃（ADR-016）后，已抠图（透明底 WebP q82）能否作为输入再次过 u2netp 补抠/重抠？
**结论：放行**——三道门（透明区 RGB 保真 / 二次分割质量 / 代际损失）全部通过，进入阶段 B（W5 接入）。

## 方法

1. **输入**：it-016 报告的 4 组真抠图样张（`2026-09-20-cutout-sdk-test/samples/*-cutout.png`，RGBA 720×900）。
2. **WebP 往返**（python PIL/libwebp，quality=82 模拟 `ImageFileStore` 的 `WEBP_LOSSY`）：gen0 → 编码 → 解码 = gen1，连做 3 代；另做 `exact=True` 对照组。
3. **二次分割**（libs/cutout 真模型 u2netp，桌面 JVM）：分别对 gen0 / gen1 / gen3 跑 `engine.cutout`，比较输出掩码。
4. 产物：`webp-roundtrip-stats.json`（第 2 步全量统计）、`recut-seg-results.txt`（第 3 步 IoU 表）、`lowContrast-recut-gen{0,1}.png`（最差样张目测对比）。

## 结果

### 门 1：透明区 RGB 保真（libwebp 默认，4 场景 × 3 代）

| 指标 | 结果 |
|---|---|
| 透明区纯黑占比 | **0.0000（全部场景、全部代际）**——RGB 未被 encoder 清零 |
| 透明区 RGB 均值 | 保留原始背景色（如 flatLay 147/106/102），逐代漂移 <2 |
| exact 对照组 | 与默认完全一致 → Skia 是否 exact 不敏感 |
| alpha 掩码代际 IoU | **1.0000（gen1/gen2/gen3 vs gen0 全部）**——lossy WebP 的 alpha 是独立无损平面 |

### 门 2：二次分割质量（u2netp，输出掩码 IoU）

| 场景 | 输入掩码 IoU | 二次分割 IoU（gen1） | 三代后（gen3） | 覆盖率 gen0→gen1 |
|---|---|---|---|---|
| flatLay | 1.0000 | 0.9995 | 0.9993 | 0.403 → 0.403 |
| hanger | 1.0000 | 0.9996 | 0.9995 | 0.440 → 0.440 |
| **lowContrast**（最差） | 1.0000 | **0.9454** | 0.9445 | 0.414 → 0.436 |
| onBody | 1.0000 | 0.9996 | 0.9996 | 0.451 → 0.451 |

- 无塌缩（覆盖率 sanity 断言 0.01~0.99 全过）；最差的 lowContrast 是低对比样张，边界略移但目测（对比 PNG）服装完整、无挖洞扩面。

### 门 3：代际损失

- 可见区 PSNR vs gen0：gen1 ≈ 39.1–41.8 dB → gen3 ≈ 38.6–41.7 dB（**三代累计 ≤0.7 dB**）。
- 单代间 PSNR 47–57 dB（很高）；结合门 2，三代后二次分割 IoU 仍 ≥0.944。
- **不设次数限制**，阶段 B 以「预览 + 还原」兜底（it-040 决策 2）。

## 残余风险（如实记录）

1. **编码器差异**：本实验用 libwebp（PIL），Android 实际走 Skia `WEBP_LOSSY`；两者底层同为 libwebp，且 exact 对照无差异，风险低——阶段 B 模拟器实测会自然覆盖（预览即验证）。
2. **u2netp 分布外输入**：透明底合成图上的二次分割在本 4 场景成立；人穿衣服等 it-016 已知边界形态不因补抠改变（仍建议放弃抠图，UI 如实呈现）。

## 复现

- 第 2 步脚本：PIL `img.save('WEBP', quality=82)` 往返 + numpy 统计（方法见 it-040 验证记录）。
- 第 3 步：一次性 `RecutSpikeTest`（libs/cutout，跑完已删，逻辑=读 /tmp/recut-spike PNG → `OnnxCutoutEngine.cutout` → IoU/sanity → 写 out PNG）。
- 输入样张：`reports/2026-09-20-cutout-sdk-test/samples/`。
