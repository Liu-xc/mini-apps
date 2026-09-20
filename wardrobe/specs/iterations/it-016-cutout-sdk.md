# it-016 · 录入去背景(抠图)——按需加载模块 SDK(libs/cutout)

- **状态**:已确认(2026-09-20,Leo 三项决策落定见 §7)。分两阶段:**阶段 A = libs/cutout SDK + JVM 真模型自测,不碰 wardrobe 代码**(并行功能冲突隔离);**阶段 B = wardrobe 接入**(阶段 A 自测通过后)
- **涉及用户故事**:新增 US-15(录入衣物时一键去除背景)
- **涉及 ADR**:wardrobe ADR-016(接入 libs/cutout,阶段 B 落笔);libs/cutout 自带 specs/06-decisions(引擎选型、纯 JVM 模块 + 双 runtime、模型 bytes 注入)

## 1. 背景与动机

录入衣物的照片大多带杂乱背景(床单、地板、购物 App 截图边框),卡片与合成图观感差。目标:录入预览时一键「去背景」,输出透明底 WebP。

约束:任意背景 + 离线可用 + 国内设备可用 → 传统图像算法(GrabCut/颜色分割)只在纯色底可靠,必须用端侧轻量显著性分割模型。选型 **ONNX Runtime + u2netp**(rembg 同款模型,4.7MB,纯 CPU 推理约 0.3~0.8s,中端机可用):

- 否决 ML Kit Subject Segmentation:依赖 Google Play Services 装机后从 Google 服务器下载模型(国产无 GMS 设备不可用),且模拟器上不工作(官方已知 issue),阻塞本仓库的模拟器评审工作流。
- 否决 TFLite 路线:runtime 更小(~3MB)但需模型转换 + 精度验证;onnx 可直接复用 rembg 验证过的模型,零转换。记为演进候选。

## 2. 「按需加载」的设计边界(关键说明)

Android 侧载分发(本仓库应用的实际分发方式)用不了 Play Dynamic Feature / App Bundle 按需交付,运行时从网络下载 .so 加载则脆弱且不可靠。因此「按需」拆成三层,动静分离:

| 层 | 策略 | 效果 |
|---|---|---|
| ① 原生库(libonnxruntime.so) | 由 wardrobe 依赖的 `onnxruntime-android` 打包进 APK,**首次推理才 System.loadLibrary**——SDK 编排层保证不提前触碰 | 冷启动零开销,内存按需占用 |
| ② 模型文件(u2netp.onnx, 4.7MB) | **打包进 assets**(2026-09-20 Leo 确认),`createSession(byte[])` 直接读入,不落盘 | 装完即用,**全流程零网络依赖**;免下载失败/镜像问题 |
| ③ 推理会话(OrtSession) | 首次推理时惰性创建,**空闲 5 分钟自动释放**(可配);推理在 Dispatchers.Default,内部 Mutex 串行 | 不常驻内存(~几十 MB 只在编辑期占用) |

ABI 策略:`debug` 全 ABI(x86_64/x86 保证模拟器评审可用);`release` abiFilters 仅 arm64-v8a + armeabi-v7a。APK 静态增量预算:.so 约 +25MB 量级(release 过滤后,以实测为准,验证记录回填)+ 模型 4.7MB(assets),合计约 +30MB 量级。

## 3. SDK 设计(libs/cutout)

### 3.1 定位与边界

- 纯「主体抠图引擎」:RGBA 字节数组进(宽高声明)、同尺寸 RGBA 出(仅 alpha 通道变化)。**不含 UI、不含业务概念、不见 android.graphics**——Bitmap ↔ RGBA 转换归 app 层(wardrobe 的 ImageFileStore 几行代码)。
- **纯 Kotlin JVM 模块**(与 store 同款,非 Android library):自测可在桌面 JVM 用桌面版 onnxruntime + 真模型跑完整推理,不依赖模拟器/真机;移动端运行时由 app 依赖 `onnxruntime-android`(与桌面版同一套 ai.onnxruntime Java API,版本对齐)。
- 零业务依赖:不依赖 store/sync/carddeck;编译期仅 `compileOnly` 桌面版 onnxruntime(SDK 不泄漏 ai.onnxruntime 类型),测试用 `testImplementation`。
- SDK 不持 Context:模型 bytes 经 `suspend () -> ByteArray` 注入(wardrobe 阶段 B 从 assets 读)。

### 3.2 公开 API

```kotlin
interface CutoutEngine {
    val readiness: StateFlow<CutoutReadiness>   // Idle → Preparing → Ready / Failed(可重试)
    suspend fun prepare()                        // 注入模型 bytes + 创建 session,幂等(UI 可提前预热)
    suspend fun cutout(rgba: ByteArray, width: Int, height: Int): ByteArray
                                                 // Ready 前提下;返回同尺寸 RGBA,仅 alpha 更新
    suspend fun release()                        // 立即释放 session(低内存时 app 可调)
}

sealed interface CutoutReadiness {
    data object Idle : CutoutReadiness           // 未初始化
    data object Preparing : CutoutReadiness      // 读模 + 建 session 中
    data object Ready : CutoutReadiness
    data class Failed(val cause: Throwable) : CutoutReadiness
}
```

- 预处理归一化(RGBA→NCHW float32 1×3×320×320,ImageNet mean/std)与后处理(d0 掩码 min-max 拉伸 + 双线性回原尺寸写回 alpha)为**纯函数**,JVM 单测。
- 推理编排对 `SaliencyInferencer` 接口编程:真模型测试与假实现编排测试同套用例。

### 3.3 模块结构

```
libs/cutout/
├─ build.gradle.kts            kotlin("jvm");compileOnly+testImplementation 桌面版 onnxruntime
├─ settings.gradle.kts
├─ src/main/kotlin/com/leo/libs/cutout/
│   ├─ CutoutEngine.kt         接口 + CutoutReadiness
│   ├─ OnnxCutoutEngine.kt     惰性 session · Mutex 串行 · 空闲 5min 自动释放
│   ├─ SaliencyInferencer.kt   推理薄壳接口 + Onnx 实现(ai.onnxruntime)
│   ├─ U2NetPreprocessor.kt    纯函数(RGBA → NCHW 归一化)
│   └─ MaskToAlpha.kt          纯函数(d0 mask → alpha 写回)
├─ src/test/resources/u2netp.onnx   4.7MB,自测用(wardrobe assets 由阶段 B 复制)
└─ specs/00-architecture.md · 06-decisions.md
```

## 4. wardrobe 接入设计(阶段 B)

### 4.1 交互流程(US-15)

录入预览页(选图/拍照之后、保存之前)新增「去背景」按钮:

```
进入录入预览页 → 后台 prepare(读 assets + 建 session,几百 ms,UI 无感)
点击「去背景」→ 推理(loading ≤1s;若 prepare 未完则等待其完成)
     → 前后对比预览(透明棋盘格底)→ 保留抠图版 / 还原
保存 → 带 alpha 的 WebP(复用现有 compressWebp,格式不变)
```

- **原图生命周期(Leo 定,2026-09-20)**:编辑会话期间原图仅存内存(作为「还原」锚点与对比底图),用户确认保存后才以抠图版落盘,原图即弃——不落盘双份、无历史字段。
- 仅录入时提供,已录入衣物不补抠(演进候选)。
- 插入点:`ImageFileStore.importFromUri` 解码之后、compressWebp 之前(`ImageFileStore.kt:35`);engine 构造发生在功能首用,不进启动路径;wardrobe 侧新增 `implementation("com.microsoft.onnxruntime:onnxruntime-android")`(与 SDK 编译期桌面版 API 对齐)。

### 4.2 specs 影响(常青,阶段 B 落笔)

| 文件 | 更新 |
|---|---|
| 01-user-stories | 新增 US-15 及验收标准 |
| 02-wireframes | 录入预览页加「去背景」按钮 + 对比预览态 |
| 05-design-system | 推理 loading、棋盘格透明底、对比交互 |
| 06-decisions | ADR-016:接入 libs/cutout(引擎选型 + 模型打包 assets) |
| 03-data-model | 不变(仍为 uuid.webp,alpha 通道是格式内既有能力) |

## 6. 影响范围(按阶段)

**阶段 A(本步,先行)**
- 新增:`libs/cutout/`(纯 JVM SDK 全量 + specs + 测试模型 + 单测);wardrobe 目录仅新增本 it-016 文件,零代码接触

**阶段 B(阶段 A 自测通过后,避开 wardrobe 并行功能)**
- 修改:wardrobe 录入预览 UI、`ImageFileStore`(RGBA 转换 + 抠图挂钩)、di(AppContainer 注入)、`wardrobe/settings.gradle.kts`(includeBuild libs/cutout)、依赖(onnxruntime-android)
- 不动:数据模型、存储格式、sync/carddeck、其他 app

## 5. 验收标准

**阶段 A(SDK,先行)**

1. JVM 单测全绿,含**真实 u2netp.onnx 推理自测**:构造纯色底 + 几何图形测试图,断言输出 alpha 中心高/角落低、形状覆盖率合理;另覆盖归一化、mask→alpha(全零/极值掩码)、引擎编排(假 inferencer:未 prepare 抛错、串行、空闲释放、Failed→重试恢复)。
2. 自测全程无 Android 依赖(纯 JVM),`libs/cutout` 目录自足构建。
3. specs(00-architecture + 06-decisions)+ README 落地,模型 SHA-256 记录在案。

**阶段 B(wardrobe 接入,阶段 A 通过后)**

4. 点击「去背景」到出图 ≤1s 量级(中端机);抠图全流程不发起任何网络请求;推理失败不影响原图保存。
5. 样张质量目测:白底商品图、平铺拍摄、挂拍三类合格;人穿衣服样张抠出「人+衣服」且可放弃还原(预期行为,如实告知)。
6. 冷启动无回归:启动路径不触碰 onnxruntime 类(启动耗时对比记录)。
7. 带 alpha 衣物在卡片、合成图导出长图中渲染正常(透明底不出现黑底/异常)。
8. 既有流程回归:录入/卡组/导出不受影响;`release` 构建 APK 增量实测回填(预算 +30MB 量级)。
9. specs 常青文件同步(01/02/05 + ADR-016)+ CHANGELOG 一行 + 提交规范。

## 7. 待确认决策(Leo)

1. ~~模型分发源~~ **已定(2026-09-20)**:模型打包进 assets,全离线,免下载与镜像问题。
2. **原图保留策略**:确认式覆盖(不留原图)vs 双图存储(可反悔/重抠)。我推荐前者,理由见 §4.1。
3. **迭代归属**:本迭代同时落地 SDK + wardrobe 接入(一个 it);若你想 SDK 先行单独验仓,可拆 two-step 提交,但同一 it 编号。

## 8. 验证记录

### 阶段 A · libs/cutout SDK(2026-09-20 完成)

- 构建:`wardrobe/gradlew -p libs/cutout test` BUILD SUCCESSFUL;`libs/cutout` 目录自足,零 wardrobe 代码接触
- 单测 **14/14 全绿**:纯函数 7(归一化逐通道数值、320 恒等缩放、常量/全零掩码保守全 255、渐变单调、RGB 不动)+ 引擎编排 6(未 prepare 自动初始化、非法输入不建会话、Failed→重试恢复、并发串行 maxInflight==1、空闲超时释放+重建、release 幂等,虚拟时间)+ **真模型自测 1**
- 真模型自测(mac arm64 桌面 JVM):u2netp 端到端 **1309ms**(含首次建会话);白底深蓝圆盘 640×640 → 主体 alpha=255、背景角落 alpha=0、RGB 通道不动
- 模型:u2netp.onnx 4,574,861B,SHA-256 `309c8469…f4ddd8`(libs/cutout/src/test/resources;阶段 B 复制进 wardrobe assets)
- **测试报告轮(同日追加)**:新增场景样张自测(`SceneSampleTest`,合成平铺/挂拍/低对比/人穿衣服 4 场景 → 推理 → 前后对比 PNG + 探针断言),用例 14→**15,全绿**;报告产物 `reports/2026-09-20-cutout-sdk-test/report.html`
- 报告发现并修复:掩码纯软边输出产生「背景色晕边」→ 新增**边缘锐化窗**(ADR-005:掩码 <0.35 判背景/>0.85 判主体),晕边消除;已知边界如实入档——零纹理印花被挖洞、人穿衣服场景头/臂残缺(产品结论:该形态建议放弃抠图)
- 待阶段 B 回填:移动端真机耗时、APK 增量实测(预算 +30MB 量级)、冷启动无回归、三类样张目测质量、带 alpha 导出合成图渲染

### 阶段 B · wardrobe 接入

(待 wardrobe 并行功能落地后执行)
