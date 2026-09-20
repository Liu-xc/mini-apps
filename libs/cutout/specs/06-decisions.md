# 06 · cutout 决策记录

## ADR-001 · 引擎选型：u2netp + ONNX Runtime（2026-09-20，it-016）

- **备选否决**：
  - ML Kit Subject Segmentation——模型经 Google Play Services 装机后从 Google 服务器下载，国产无 GMS 设备不可用；模拟器上不工作（官方已知 issue），阻塞本仓库模拟器评审工作流。
  - TFLite——runtime 更小（~3MB）但需 onnx→tflite 转换 + 精度验证；记演进候选。
  - 传统算法（GrabCut/颜色分割）——仅纯色底可靠，任意背景误判严重。
- **结论**：u2netp（rembg 同款 onnx，4.4MB，纯 CPU 0.3~0.8s 量级/张，零转换直接推理）。模型 SHA-256 `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`。
- **演进**：更强模型（isnet-lite 等）= 同管线换 onnx 文件，接口不变。

## ADR-002 · 纯 Kotlin JVM 模块 + 双 runtime（2026-09-20，it-016）

- **决策**：SDK 为纯 JVM 模块，公开 API 只有 ByteArray/Int（不见 android.graphics.Bitmap，Bitmap 桥归消费方）；编译期与测试依赖**桌面版** onnxruntime，移动端运行时由消费方依赖 **onnxruntime-android**（两产物同一套 `ai.onnxruntime` Java API，版本须对齐——当前 1.20.0）。
- **动机**：SDK 可在桌面 JVM 用真模型完整自测（it-016 阶段 A 验收核心），不依赖模拟器/真机；与 libs/store 模块形态一致（避免 libs 首个 AGP Android library 的复杂度）。
- **后果**：消费方升级 onnxruntime-android 时须同步升 SDK 的 compileOnly 版本并回归测试；Bitmap↔RGBA 转换代码（copyPixelsToBuffer，ARGB_8888 即 RGBA 字节序）在消费方。

## ADR-003 · 模型分发：打包 assets、bytes 注入（2026-09-20，Leo 定）

- **备选否决**：首用联网下载（u2netp.onnx 官方源在 GitHub release，国内直连不稳；需下载/SHA 校验/多镜像/删除重下整块逻辑）。
- **结论**：模型放消费方 assets，SDK 经 `suspend () -> ByteArray` 注入（SDK 不持 Context、不落盘）；APK 增量 +4.4MB。
- **后果**：装完即用、全流程零网络依赖；模型升级随版本发版（个人应用发版成本低，可接受）。

## ADR-004 · 输出语义：常量掩码保守全 255（2026-09-20，it-016）

- **决策**：d0 掩码 min-max 拉伸后写 alpha;掩码全常量（模型认为无显著主体）输出全 255（完全不抠），而非全 0（整图透明）。
- **动机**：失败模式宁可不抠也不能把用户照片整张变透明；消费方 UI（对比预览+放弃）兜底剩余误判场景。
