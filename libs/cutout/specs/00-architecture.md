# 00 · cutout 架构设计（主体抠图 SDK）

- **状态**：阶段 A 已实现 v0.1.0（2026-09-20，wardrobe it-016），JVM 真模型自测 14 用例全绿；wardrobe 接入 = it-016 阶段 B（待 wardrobe 并行功能落地后）
- **消费方**：wardrobe US-15（录入去背景）；接口 bytes 通用，其他 app 可复用

## 1. 定位与目标

各 app 通用的「主体抠图引擎」：任意背景照片 → 分割显著主体 → 输出仅更新 alpha 通道的同尺寸图像。

| 目标 | 含义 |
|---|---|
| 零业务概念 | 不知道衣物/餐厅/剪贴板，也不认识 Bitmap |
| 纯 Kotlin JVM | 不见 android.graphics / Context（ADR-002）；与 libs/store 模块形态一致，桌面 JVM 即可完整自测 |
| 全离线 | 模型打包进消费方 assets、bytes 注入（ADR-003），推理全本地，零网络 |
| 按需资源 | 会话惰性创建 + 空闲自动释放；.so 由消费方依赖的 onnxruntime-android 首次推理才加载 |

**非目标**：UI（预览/对比/笔刷归 app）、「人穿衣服只抠衣物」（human parsing，演进候选）、alpha matting 发丝级精修、批量异步队列。

## 2. 技术选型（详见 06-decisions.md）

**u2netp + ONNX Runtime**：u2netp 4.4MB 显著性分割模型（rembg 同款，SHA-256 `309c8469…f4ddd8`）；输入 1×3×320×320（双线性缩放 + ImageNet mean/std 归一化），输出 d0 320×320 掩码。否决 ML Kit（GMS 依赖 + 模拟器不可用）、TFLite（需转换验证，记演进候选）、传统算法（仅纯色底可靠）。

## 3. 数据流与模块

```
RGBA(w×h) ──U2NetPreprocessor──▶ 1×3×320×320 float32 NCHW
         ──SaliencyInferencer──▶ d0 320×320 掩码        （ai.onnxruntime 薄壳）
         ────MaskToAlpha────▶ RGBA(w×h) 仅 alpha 更新   （min-max 拉伸 + 双线性回原尺寸）
```

```
libs/cutout/src/main/kotlin/com/leo/libs/cutout/
├─ CutoutEngine.kt         公开接口 + CutoutReadiness + CutoutException
├─ OnnxCutoutEngine.kt     惰性 session · Mutex 串行 · 空闲自动释放
├─ SaliencyInferencer.kt   推理薄壳接口（注入缝）+ Onnx 实现（internal）
├─ U2NetPreprocessor.kt    纯函数：RGBA → NCHW 归一化
└─ MaskToAlpha.kt          纯函数：mask → alpha 写回
```

依赖铁律：不依赖 store/sync/carddeck；编译期 `compileOnly` 桌面版 onnxruntime（不泄漏 ai.onnxruntime 类型到公开 API）；coroutines-core 为唯一 api。

## 4. 核心 API

```kotlin
interface CutoutEngine {
    val readiness: StateFlow<CutoutReadiness>   // Idle → Preparing → Ready / Failed(可重试)
    suspend fun prepare()                        // 注入模型 bytes + 建 session,幂等,UI 可提前预热
    suspend fun cutout(rgba: ByteArray, width: Int, height: Int): ByteArray
                                                 // 未就绪自动补 prepare;返回同尺寸 RGBA 仅 alpha 变
    suspend fun release()                        // 手动立即释放(低内存回调),幂等
}

sealed interface CutoutReadiness { Idle; Preparing; Ready; Failed(cause) }
```

- `OnnxCutoutEngine(modelBytes: suspend () -> ByteArray, idleTimeout = 5min, inferenceContext, inferencerFactory)`。
- 消费方 Bitmap 桥（wardrobe 阶段 B）：`Bitmap.copyPixelsToBuffer`（ARGB_8888 = RGBA 字节序）进出，几行代码归 app。

## 5. 生命周期与并发

- **惰性**：首次 prepare()/cutout() 经 factory 建会话；Failed 态再次 prepare()/cutout() 即重试重建。
- **串行**：Mutex 保护（单 session 不并发），并发 cutout 排队（测试断言 maxInflight==1）。
- **空闲释放**：最后一次活动后 `idleTimeout`（默认 5 分钟，≤0 关闭）自动 close 会话，readiness 回 Idle;下次调用重建（数百 ms）。
- **线程**：推理在独立上下文执行,默认单守护线程 `cutout-inference`，不占公共调度池。

## 6. 测试策略（阶段 A 全部 JVM，无设备依赖）

| 层 | 用例 |
|---|---|
| 纯函数 | 归一化数值（ImageNet mean/std 逐通道）、320 恒等缩放、常量/全零掩码保守全 255、渐变掩码单调映射、RGB 通道不动 |
| 编排（假 inferencer） | 未 prepare 自动初始化、非法输入不建会话、Failed→重试恢复、并发串行（maxInflight==1）、空闲超时释放+重建、release 幂等（虚拟时间） |
| **真模型自测** | 桌面 JVM + u2netp.onnx 端到端：白底深蓝圆盘 640×640 → 主体 alpha=255 / 背景 alpha=0 / RGB 不动 |

**实测（2026-09-20，mac arm64 桌面 JVM）**：14 用例全绿；真模型端到端 1309ms（含首次建会话；纯推理数百 ms 量级）。移动端预算：中端 ARM CPU 0.3~0.8s/张（阶段 B 实测回填 wardrobe it-016）。

## 7. 消费方接入路径

- **wardrobe（it-016 阶段 B）**：`includeBuild("../libs/cutout")` + `implementation("com.leo.libs:cutout:0.1.0")` + `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")`（版本须与 SDK 编译期桌面版对齐，ADR-002）；模型复制进 `app/src/main/assets/u2netp.onnx`；ImageFileStore RGBA 桥 + US-15 UI。
- **eats / clips**：暂无需求;接口 bytes 通用,未来直接复用。
