# 衣橱「AI 穿搭生图」模型调研与 Provider 抽象方案报告

> 日期：2026-09-30 ｜ 状态：**提案待拍板**（对应 it-077）
> 需求原文（Leo）：接入「图片+文本→图片」模型在应用内生成穿搭效果图；接入能力要做成抽象层（参考 OpenCode / cc-switch 的多 provider 架构），不锁死 GLM/MiMo；问答与生图各用各的模型；生图有两条触发路径——①用户在界面直接触发，②顾问对话流识别到需要时自动调用并在对话里展示。
> 调研方式：4 路并行子代理（通用图生图横评 / 专用虚拟试穿 / provider 抽象架构 / 仓库现状探查），价格以 2026-09 官方渠道为准并标注核实状态。

---

## 0. TL;DR

**模型选型（BYOK，用户自选，App 给默认推荐）**：

| 用途 | 首选 | 价格 | 理由 |
|---|---|---|---|
| 免费尝鲜/灵感图 | 硅基流动 Kolors | **0 元** | 单 Key 多模型、OpenAI images 兼容、同步 |
| **主力·上身效果图** | 阿里百炼 `qwen-image-edit-plus` | **¥0.2/张** | 同步、**base64 直传**（无需图床）、1–3 图参考、RPM 120 |
| 进阶·多件组合（≤14 图：人物+上装+下装+鞋+包） | 火山方舟 `doubao-seedream-5.0-lite`（或 4.0） | ¥0.22/张 | 参考图最多、同步、OpenAI images 兼容、个人实名可用 |
| 高保真试衣（二期） | 百炼 `aitryon-plus` 专用 VTON | ¥0.5/张（**400 张免费**） | 纹理/Logo/人脸保真最强，但**只收公网 URL**，需图床前置 |
| 兜底备援 | 硅基流动 Z-Image-Turbo | ¥0.1/张 | 聚合平台天然适合当 fallback |

**抽象方案一句话**：`libs/agent` 建成**统一模型目录 + 双能力轨**——一个 `ProviderSpec` 注册表同时声明各厂商的聊天模型与生图模型（`ModelSpec.capabilities` 分轨，OpenCode models.dev 思想），`ChatModel` 传输不动（新聊天厂商 = 纯数据扩容）+ 新 `ImageModel`（`SYNC` / `ASYNC_TASK` 两种生图交互显式建模，事件流对齐 `AgentEvent` 风格）；顾问聊天与穿搭生图各绑各的 provider+model（用途分轨路由），**同一厂商一把 Key 两轨共用**，密钥/用量/错误分类全复用现有 BYOK 设施；**模型专属参数声明式透出**（注册表声明各模型参数 schema，生成页动态渲染高级面板）。设计借鉴：结构抄 OpenCode（preset 数据化 + `provider/model` 二段 ID）、能力字段抄 models.dev + OpenRouter（capability 与输入/输出 modality 分开）、参数白名单抄 OpenRouter `supported_parameters`、回退抄 LiteLLM（重试→错误分轨 fallback→冷却熔断，移动端简化版）。**覆盖范围（拍板澄清）：provider 抽象同时覆盖聊天轨与生图轨**——聊天 preset 从 glm/mimo/custom 三个扩到 DeepSeek/Kimi/百炼 Qwen/豆包/硅基流动等全量国内主流（全部 OpenAI 兼容，零传输代码）。

**迭代拆分**：it-077 三阶段——A：SDK 生图轨（抽象+两协议适配器+假实现）；B：直达路径（W7 搭配详情生成 sheet 含高级参数面板 + W11 生图连接卡）；C：对话流路径（第 5 个顾问工具 + 生图卡片）。

**拍板记录（2026-09-30，Leo）**：应用主要自用、价格不敏感、不分发线上用户——开销类策略（月度护栏/生成前确认/成本拦截/隐私弹窗）全部不做；改为把各模型专属配置透出给使用者调。

---

## 1. 需求与场景

### 1.1 两条触发路径

- **路径 A · 界面直达**：用户在搭配详情页（W7）对着已组建的 Outfit 点「AI 试衣」→ 全屏生成页（自动装配：Outfit 内各单品的衣物照片 + 人物参考照）→ 出图 → 存入该 Outfit 的 `effectImages`。
- **路径 B · 顾问对话流**：用户在顾问对话（W13）里问「这套穿起来什么效果？」→ 顾问模型识别意图 → 调用第 5 个工具 `generate_outfit_image` → 对话流里出现「生成中…」→ 结构化卡片展示成图（it-075 卡片体系扩展一种新卡型）。

### 1.2 数据形态（仓库现状，探查确认）

- 衣物照片：`Item.imageFile` → `filesDir/images/` 下统一 WebP（1440px 上限、质量 82），演示模式在 `cacheDir/mock-images/`。
- 人物参考照：it-074 已提供（导出人物参考图，mock Leo 预置人台参考照）。
- 挂载点：`Outfit.effectImages: List<OutfitImage>` + `WardrobeRepository.addEffectImage()` **现成，零 schema 改动即可挂图**；it-068 的 `effectStale` 已处理「单品调整后成品图过期」。
- 对话卡片：it-075 `ToolResultCard` sealed interface + `parseToolResultCard` 的 kind 分支即扩展点，payload 只存 id 引用（ADR-029）。

### 1.3 生成输入的典型形态

「人物参考照（1 张）+ 衣物平铺/挂拍照（N 张，N=搭配件数）+ 文本（场景/风格描述或顾问总结的搭配说明）」→ 1–2 张上身效果图。这决定了模型门槛：**必须支持多图输入**（至少 1+N 张），纯文生图（智谱 cogview-4 / 百炼 qwen-image 文生图版）不满足主链路。

---

## 2. 国内模型调研结论

### 2.1 通用「图+文→图」编辑/生成模型横评（本场景相关子集）

| 厂商/模型 | 多图输入 | 计费（元/张） | API 形态 | 关键约束/亮点 | 核实状态 |
|---|---|---|---|---|---|
| **百炼 qwen-image-edit-plus** | 1–3 张 | **0.20** | **同步**（multimodal-generation） | **URL/base64 均可**；RPM 120；输出 URL 24h | ✅官方 |
| 百炼 qwen-image-edit（2509 旧版） | 1–3 张 | ≈0.30 | 同步 | 固定 1 张输出 | 部分核实 |
| 百炼 qwen-image-edit-max | 1–3 张 | 0.5 | 同步 | **RPM 仅 2**，尝鲜级 | ✅官方 |
| 百炼 qwen-image-3.0 | 多图 | 0.18 + 输入图 0.02/张 | 同步 | 输入图开始计费 | ✅官方 |
| **火山 doubao-seedream-5.0-lite** | **≤14 张** | **0.22**（4K 档） | **同步，OpenAI images 兼容** | base64(<10MB)/URL；IPM 500 宽松 | ✅多源一致 |
| 火山 doubao-seedream-4.0 | ≤14 张 | 0.20 | 同上 | 2025 旧价 0.06 已过时 | ✅现价 |
| 火山 doubao-seedream-4.5 / 5.0 | ≤14 张 | 0.25 / 0.22 | 同上 | 5.0-pro 输入图第 2 张起 0.02 | ✅ |
| 火山 doubao-seededit-3.0-i2i | 仅 1 张 | 未公开（三方 0.3） | 同步 | 单图编辑，不适合多件 | ❌存疑 |
| **硅基流动（聚合）** | 随模型 | Kolors **免费**；Z-Image-Turbo **0.10**；Qwen-Image(-Edit) 0.30 | OpenAI images 兼容，同步 | 单 Key 多模型，天然备援 | ✅官方定价页 |
| 腾讯 Hy Image 3.5 preview | ≤5 张 | 0.15 | TokenHub（token 计费） | 多轮对话编辑、只收最终输出费；2026-09 新发，观察 | ✅ |
| 腾讯老接口（aiart 混元生图） | 文生图 | 0.5 | 异步 | **默认并发 1**，不建议新接 | ✅ |
| 可灵 Kling Image 2.1 | 多图参考 | 0.4 | 异步任务 | 资源包预付费，偏贵 | ✅ |
| MiniMax image-01 | 仅 1 张人像参考 | **0.025** | 同步 | 多图融合不适用；价格地板 | ✅ |
| 智谱 cogview-4 / glm-image | **无图输入** | 0.06 / 0.10 | 同步 /v4/images/generations | 纯文生图，**不满足主链路**；已配 GLM key 用户可玩「灵感图」 | ✅ |
| 百度千帆 | 无多图参考 | 自有 0.05 / 转售 0.25–0.3 | v2 API | 边缘化，不推荐 | ✅ |

**要点**：①本场景真正可用且个人开发者可直接开通的「同步 + base64 + 多图」模型，集中在**百炼 qwen-image-edit 系与火山 Seedream 系**两条线；②硅基流动用一套 OpenAI images 兼容协议聚合了多家，是 BYOK 场景的「瑞士军刀」；③输出 URL 均 24h 失效——**App 必须立即下载转存**（本就要落 `images/` 目录，无额外成本）。

### 2.2 专用虚拟试穿（VTON）API

| 服务 | 入参 | 价格 | 形态 | 门槛 | 核实 |
|---|---|---|---|---|---|
| **百炼 AI试衣 aitryon-plus** | 人物正面全身照 + 上装 + 下装（免抠图免 mask，`restore_face` 保脸） | **0.5/张，国内免费 400 张** | 异步（提交+轮询，约 90s/张） | **仅收公网 URL 不收 base64**；北京地域；RPS 10/并发 5 | ✅ |
| 百炼 aitryon 基础版 | 同上 | 0.2/张 | 同上 | 同上 | ✅ |
| 快手可灵 kolors-virtual-try-on | 人物图+服装图（v1.5 上下装拼组合图） | ≈0.2/张 | 异步，JWT(AK/SK) 鉴权 | 官方页 JS 渲染，价格需控制台复核 | ⚠️ |
| 火山 图片换装 V1/V2 | 模特图+多件服装图 | 算点制（未公开） | — | **仅企业认证，个人不可申请** | ✅出局 |
| 腾讯 ChangeClothes | 模特图+**单件**服装 | 未标价 | 同步 | **一次一件 + 默认并发 1** | ✅出局 |
| 淘宝/京东 AI 试衣 | — | — | — | 无公开 API | 出局 |
| RunningHub / LiblibAI | ComfyUI 云工作流（可跑 IDM-VTON 等开源 VTON） | GPU 算力计费 | 平台型 | 需自搭工作流，运维复杂 | ⚠️观察 |

**「专用 VTON vs 通用编辑」结论**（对衣橱场景）：

| 维度 | 专用 VTON（aitryon） | 通用编辑（qwen-image-edit / Seedream） |
|---|---|---|
| 衣物纹理/Logo 保真 | **强**（设计目标就是这个） | 中等，抽卡 |
| 人物一致性 | **强**（只换衣不换人） | 2509 起可用，但仍抽卡 |
| 多件组合 | **上限上装+下装两件**；鞋/包无一家可用（阿里 shoemodel-v1 已下线） | **理论最优**（Seedream ≤14 图，实测「模特+外套+鞋包拼完整穿搭」） |
| 灵活性（换景/姿势） | 弱（锁死原图背景姿势） | 强 |
| 失败形态 | 硬失败可检测（直接报错） | 软失败难检测（成功但衣服不对） |
| 接入摩擦 | **只收公网 URL**（本地照片需先上图床） | base64 直传，App 友好 |

**判断**：衣橱的主数据是「用户自己衣橱里的衣物 + 想看多件组合效果」，通用编辑模型的多图组合能力与 base64 友好度更契合 MVP；aitryon 的保真优势值得要，但 URL-only 门槛让它成为**二期增强**（届时评估「轻量图床/OSS 直传」前置，或等百炼放开 base64）。

### 2.3 推荐组合（App 内置 preset 注册表 + 默认推荐）

1. **默认主力**：百炼 `qwen-image-edit-plus`（¥0.2，同步，base64，1–3 图）——覆盖「人物+上装+下装」的最常见穿搭。
2. **进阶多件**：火山 `doubao-seedream-5.0-lite`（¥0.22，≤14 图）——鞋、包、配饰全组合的「穿搭大片」。
3. **免费尝鲜**：硅基流动 Kolors（0 元）+ Z-Image-Turbo（¥0.1）——新用户零成本跑通链路，也当 fallback。
4. **二期高保真**：百炼 `aitryon-plus`（¥0.5，400 张免费）——「保真换装」档位。
5. 智谱 cogview-4（¥0.06）作为已配 GLM key 用户的可选「灵感参考图」（纯文生图），不进主链路。

**成本估算**：重度用户每天生成 2 次、每次 2 张候选（防抽卡），主力模型月成本 ≈ 2×2×30×¥0.2 = **¥24/月**；免费尝鲜为 0。BYOK 下全部由用户自己的 key 承担，App 只需把单价亮明白。

### 2.4 合规要点（自用定位，从简）

> **拍板（2026-09-30）**：应用不分发线上用户，合规负担按自用从简——不做首次确认弹窗、不做护栏拦截；保留以下两点的「自用也有价值」版本：

- **AI 生成标识**：保留「AI」视觉角标 + 文件级 `source` 元数据——自用时它也是「这张图是生成的还是我拍的」的唯一区分手段，成本近零。若未来分发，再补深度合成申报。
- **人脸隐私**：不做弹窗，生成 sheet 底部一行静态提示「照片将上传至 <厂商>」即可；优先 base64 通道（照片不经第三方图床）。
- 实名认证与商用条款：自用场景按各家个人实名开通即可；审核失败不计费（火山已明确），错误处理区分「审核拒绝」与「系统失败」。

---

## 3. Provider 抽象设计（参考 OpenCode / cc-switch / LiteLLM / OpenRouter / one-api）

### 3.1 业界机制对比（浓缩）

| | OpenCode | cc-switch | LiteLLM | OpenRouter | one-api/new-api |
|---|---|---|---|---|---|
| 形态 | CLI 内置 SDK 抽象 | 桌面切换器+本地代理 | Python SDK/网关 | 托管聚合 API | 自建网关 |
| 配置 | provider 声明**协议适配包** + baseURL/key；key 支持 `{env:}` 引用 | profile = {endpoint, key, model, 协议} 四元组 | model_list（别名分组+rpm/tpm） | 云端注册表零配置 | 渠道+令牌 |
| 能力声明 | models.dev：`tool_call/modalities/limit/cost` | 无 | model_info + cost map | `input_modalities/output_modalities/supported_parameters/pricing.image` | 倍率表 |
| 回退 | **无** | failover 队列+熔断+Rectifier 自动降级 | 重试→**按错误分三轨 fallback**→单部署冷却 | 无 | 成功率自动禁用 |
| 生图 | 非其场景 | — | image_generation 统一签名，**不支持 DashScope 异步任务型**（issue #28763 open） | chat+modalities；`pricing.image` 按张计价 | images 端点；同步等待 300s 必断的教训 |

**可借鉴清单**（本方案直接采用）：
1. **OpenCode**：preset 声明协议（而不是手写客户端）；内置静态模型注册表随包分发；`provider/model` 二段式 ID；key 与配置分离只存引用。
2. **models.dev + OpenRouter**：能力字段静态声明——`capabilities`（路由分轨用）与 `inputImages`（能不能吃图）**分开**；`supportedParams` 白名单避免发上游不认的参数；`cost.perImage` 按张计价字段。
3. **LiteLLM**：回退三段式（同 provider 指数退避重试 2 次 → 按错误类型走 fallback 链 → 连续 3 败冷却 5 分钟）；移动端简化为单层备援。
4. **cc-switch**：Rectifier 思想——请求前按能力声明预检（图数超限/不支持 base64 直接提示换模型，而不是等上游报错烧重试）。
5. **new-api 血泪教训**：异步任务型生图不能长阻塞同步等——但我们 App 内用挂起函数+事件流在前台交互场景是安全的（用户正盯着生成页/对话流），**限定前台交互，不做后台排队**。
6. **one-api**：反向架构（key 集中服务端），只借鉴渠道/重定向数据模型，不借鉴部署形态——我们是 BYOK，key 留在手机。

### 3.2 我们的设计：`libs/agent` 统一模型目录 + 双能力轨

**为什么不是两套 preset**：聊天与生图同属「BYOK 模型接入」域，且一个厂商往往两样都有（百炼=Qwen 聊天+生图、火山=豆包聊天+Seedream、硅基流动=聚合两者、智谱=GLM+CogView）——分成两套注册表会让「同一把 Key」被配置两遍、两处声明。统一目录后：**选了 dashscope 当聊天 provider，生图卡选 dashscope 时 Key 直接复用**。

**为什么放 `libs/agent` 而不是新 lib**：`ApiKeyStore`（Keystore 引用）、`AgentError` 错误分类、`UsageLedger` 用量、preset 数据化模式、mock 双命名空间全部直接复用；拆新 lib 反而要复制这套设施。agent SDK 已是 composite build 接入，wardrobe/eats/clips 未来都能用。

```
libs/agent
├── ChatModel.kt / OkHttpChatModel.kt        # 聊天轨传输：不动（ADR-002 继续有效）
├── ProviderCatalog.kt                       # 统一模型目录（新）：ProviderSpec/ModelSpec/能力与参数声明
│                                            #   —— 现有 Providers(glm/mimo/custom) 并入，quirks 数据原样保留
├── image/                                   # 生图轨（新）
│   ├── ImageModel.kt                        # interface + ImageGenRequest/Result/Event
│   ├── OkHttpImageModel.kt                  # 同步传输：OpenAI images 兼容（火山/硅基流动/智谱）+ 百炼同步编辑
│   └── DashScopeTaskModel.kt                # 异步任务型：提交+轮询（百炼 aitryon/wan，二期启用）
```

**核心接口草图**（对齐现有 `AgentEvent` 事件流风格，ADR-005 同构）：

```kotlin
enum class Capability { CHAT, IMAGE_GEN }       // 路由分轨标签
enum class ImageProtocol { OPENAI_IMAGES_SYNC, DASHSCOPE_SYNC, DASHSCOPE_ASYNC_TASK }

// 统一模型目录：一个 provider 声明它的全部模型，模型自带能力标签（models.dev 思想）
data class ProviderSpec(
    val id: String,                    // "dashscope" / "glm" / "mimo" / "deepseek" / "custom"
    val displayName: String,           // "阿里百炼"
    val chatBaseUrl: String?,          // 聊天端点（OpenAI 兼容；百炼=compatible-mode/v1）
    val imageBaseUrl: String?,         // 生图端点（协议不同则不同；该厂商无生图则为 null）
    val models: List<ModelSpec>,
)

data class ModelSpec(
    val id: String,                    // "qwen-image-edit-plus" / "glm-4.6" / "deepseek-chat"
    val name: String,
    val capabilities: Set<Capability>, // {CHAT} / {IMAGE_GEN}
    // —— CHAT 轨（字段对齐 models.dev）——
    val supportsToolCall: Boolean = false,  // 顾问 loop 硬门槛：W11 聊天下拉只列可用的
    val contextLimit: Int? = null,
    // —— IMAGE_GEN 轨 ——
    val imageProtocol: ImageProtocol? = null,
    val inputImages: IntRange? = null,      // 0..0=纯文生图；1..3；1..14
    val outputs: IntRange? = null,
    val supportsBase64: Boolean = true,     // aitryon=false → UI 禁用并说明
    val maxImageBytes: Long? = null,        // 火山 base64 <10MB
    val resolutions: List<String> = emptyList(),
    val params: List<ImageParamSpec> = emptyList(),  // 模型专属参数声明（拍板新增，见下）
    // —— 通用 ——
    val costPerImage: BigDecimal? = null,   // 生图：元/张快照（纯展示，不拦截）
)

// 模型专属参数声明式透出（对齐 OpenRouter supported_parameters 的「声明什么就透传什么」）
data class ImageParamSpec(
    val key: String,                   // 请求体字段名，如 "watermark" / "negative_prompt" / "restore_face"
    val label: String,                 // UI 显示名「去水印」「负向词」「保脸」
    val type: ParamType,               // BOOL / INT(range) / FLOAT(range) / ENUM(options) / TEXT
    val default: JsonElement,
    val passthrough: Boolean = false,  // true=直接进请求体；预留映射位
)

interface ImageModel {
    val preset: ProviderSpec
    suspend fun generate(request: ImageGenRequest): Flow<ImageGenEvent>
}
sealed interface ImageGenEvent {
    data object Started : ImageGenEvent
    data class Progress(val message: String) : ImageGenEvent        // "排队中/生成中…"（异步任务型映射任务状态）
    data class Completed(val images: List<GeneratedImage>) : ImageGenEvent
    data class Failed(val error: AgentError) : ImageGenEvent        // 复用错误分类
}

data class ImageGenRequest(
    val model: String,
    val prompt: String,
    val images: List<ImageInput> = emptyList(),   // 参考图：人物照+衣物照
    val count: Int = 2,                           // 默认候选张数（可被 params 面板覆盖）
    val resolution: String? = null,
    val extra: Map<String, JsonElement> = emptyMap(),  // 高级面板收集的透传参数
)
```

`ChatModel`/`ChatRequest`/`Message` 不动——聊天轨唯一的变化是 **`Providers` 并入 `ProviderCatalog` 且 preset 扩容**：新厂商（DeepSeek/Kimi/百炼/豆包/硅基流动）全部走 OpenAI 兼容端点，只是目录里多几条数据，`OkHttpChatModel` 一行不改（ADR-002 红利的兑现时刻）。现有 glm/mimo/custom 的 id 保持稳定，已存偏好与 Key 无缝迁移。

**关键机制**：

- **上传前统一转码**：仓库照片是 WebP，各家对 WebP 支持不一——适配器内统一转 JPEG（质量 90，≤1440px 天然满足大小限制）再 base64。输出 URL 24h 失效——适配器在 `Completed` 前已把字节下载下来，App 只见 bytes。
- **用途分轨路由**（对齐 LiteLLM「model_name 分组」与 OpenCode「agent 绑定模型」）：`aiPresetId/aiModel`（聊天轨，现有）+ `aiImagePresetId/aiImageModel`（生图轨，新增），W11 两个连接卡各自独立配置、各自自检、各自用量。两轨彻底解耦——聊天用 GLM、生图用百炼，正是需求要的形态。
- **回退（v1 简化版）**：生图轨可选配一个备援（如主力百炼 + 备援硅基流动）。错误分轨复用 `AgentError`：Auth（不重试不回退，直接引导去看 Key）/ RateLimit·Network（同 provider 退避重试 2 次 → 备援）/ Provider·审核拒绝（直接提示重写描述，不烧重试）。冷却标记（连续 3 败 5 分钟跳过）存 Prefs。**v1 可以先不实现备援，接口留位**——拍板点之一。
- **模型专属参数透传**（拍板核心新增）：`ImageGenRequest` 里的具名参数（prompt/images/count/resolution）做归一映射，其余按 `ImageModelSpec.params` 声明**原样透传**进请求体——各模型的独特开关（Seedream 的 `watermark`、edit 系的 `negative_prompt`、aitryon 的 `restore_face`、张数 1–6 等）不必逐个建抽象字段，preset 数据声明即可暴露；UI 生成页据此动态渲染高级参数面板，选择按模型记忆（`aiImageLastParams`），自定义 preset 提供原始 JSON 编辑。
- **用量（纯信息）**：`Usage` 增加 `images: Int` 计数（JSON 向后兼容），W11 用量卡展示张数与估算费用；**不做任何护栏/拦截**（拍板：自用不敏感）。
- **一把 Key 双轨共用**：Key 按 providerId 存（`KeystoreApiKeyStore` 现机制不变）——聊天卡选了 dashscope、生图卡也选 dashscope 时，只配一把 Key；两卡选不同厂商则各配各的。配置里只有 provider 引用，密文永不出 Keystore（OpenCode `{env:}` 引用思想）；日志只出 `maskApiKey` 红线延续。
- **假实现**：`FakeImageModel`（脚本回放，CI 不打真 API），对齐 `FakeChatModel`。

### 3.3 统一目录 v1 内置（聊天 + 生图同注册表）

| provider id | 厂商 | 聊天模型（代表，全部 OpenAI 兼容） | 生图模型 | 一把 Key 双轨 |
|---|---|---|---|---|
| `glm` | 智谱（现有） | glm-4.6 / 4.7 系（tool call ✓） | cogview-4（纯文生图，标「灵感图」，v1 可选） | ✅ |
| `mimo` | 小米（现有） | mimo 系（现状不动） | — | — |
| `deepseek` | 深度求索 | deepseek-chat / deepseek-reasoner（tool call ✓） | — | — |
| `moonshot` | Kimi | kimi-k2 系（tool call ✓） | — | — |
| `dashscope` | 阿里百炼 | qwen3-max / plus / flash（`compatible-mode/v1`，tool call ✓） | **qwen-image-edit-plus（主力）**；aitryon（二期） | ✅ |
| `volc-ark` | 火山方舟 | doubao-seed 系（tool call ✓） | seedream-4.0 / 5.0-lite（多件组合） | ✅ |
| `siliconflow` | 硅基流动 | 聚合 DeepSeek/Qwen/GLM 等（tool call 视模型） | Kolors（免费）/ Z-Image-Turbo / Qwen-Image-Edit | ✅ |
| `custom` | 自定义（现有） | OpenAI 兼容 baseURL + 手输模型 id | OpenAI images 兼容 | ✅ |

模型 id 漂移快（各家季度级上新）——目录是**纯数据**，随版本更新即可；两轨下拉均保留「自定义模型 id」手输兜底，power user 不被注册表锁死。聊天下拉按 `supportsToolCall` 过滤（顾问 loop 依赖工具调用），纯对话模型不进可选列表。

---

## 4. 衣橱接入设计

### 4.1 架构接线

- `AppContainer`：+`imageModel(preset): ImageModel` 工厂（与 `chatModel` 工厂对称，同源 `ProviderCatalog`）；演示模式包 `MockImageModel`（不打网络，从预置样张按品类返回）。
- `PrefsStore`：+`aiImagePresetId`（默认 `siliconflow`）/`aiImageModel`/`aiImageLastParams`（上次参数选择按模型记忆）；聊天轨 `aiPresetId/aiModel` 语义不变（可选范围变宽）；演示模式 `mock_` 前缀双命名空间照旧。
- mock 资产：**预置 3–4 张「AI 效果图」样张**（按上装/下装品类组合挑选），**必须 bump `MOCK_ASSET_REVISION`**（it-059 教训）。

### 4.2 路径 A：W7 搭配详情「AI 试衣」

全屏生成 sheet（复用 it-040 W5 四态交互语言）：

1. **装配态**：自动列出将上传的参考图（Outfit 各单品图 + 人物参考照，可勾选排除）；人物照缺失时引导去 it-074 人物参考。
2. **描述态**：预填 prompt 模板（由单品名称/颜色/品类自动拼装，可改）+ 场景快捷 chips（街拍/通勤/居家/纯色棚拍）+ **高级参数面板**（按 `ImageModelSpec.params` 动态渲染该模型的专属开关，按模型记忆上次选择；自定义 preset 显示 JSON 编辑框）。
3. **生成态**：进度事件流（同步模型也过 `Progress("生成中…")` 统一体验），可取消。
4. **结果态**：候选并排（张数随参数），选 1 → 转 WebP 落 `images/` → `addEffectImage(outfitId, file)` → 成品卡立即可见（带「AI」角标）+「重新生成」。sheet 底部一行静态小字注明照片上传去向（信息提示，无弹窗）。

### 4.3 路径 B：顾问对话流生图（W13）

- **第 5 个工具** `generate_outfit_image`（`WardrobeTools` 注册，只读门禁原则不变——生图写的是 `effectImages`，属成品图域，不算改衣橱主数据）：
  - 入参（JSON Schema）：`outfitId?` / `itemIds[]` / `sceneHint?` / `styleHint?`。
  - 行为：取单品图+人物参考照 → 拼装 prompt → `ImageModel.generate` → 落盘 + `addEffectImage` → 返回 `ToolResult.Ok(text, payload)`。
  - payload 协议（ADR-029 同款，只存引用）：`{kind:"outfit_image", outfitId, imageFile, model, costYuan}`。
- **新卡片** `ToolResultCard.GeneratedImage`：`parseToolResultCard` 加一个 kind 分支；卡片渲染成图（本地 `imageFile` 解析，不信任模型 URL——延续 it-054 边界）+ 「查看大图/保存确认」；生成中由 `Progress` 事件映射 typing 指示条「正在生成穿搭图…」。
- 系统提示词补一段：何时该调用该工具（用户问上身效果/想看试穿时；主动说明每次生成约 ¥X）。
- 失败态：审核拒绝/额度不足/超时（180s deadline）→ 工具返回带原因的错误文案，卡片显示重试按钮。

### 4.4 数据模型（最小增量）

`OutfitImage` 增加可选字段（JSON 向后兼容，缺省=手动添加）：

```kotlin
data class OutfitImage(
    val file: String,
    val addedAt: Long = 0L,
    val source: String = "manual",   // manual | ai
    val model: String? = null,       // "dashscope/qwen-image-edit-plus"
    val prompt: String? = null,
)
```

`effectStale` 联动不变（单品调整后 AI 图同样标过期）。导出/数据包：AI 图与手动成品图同目录同待遇，靠 `source` 字段区分——合规审计时可筛。

### 4.5 W11 设置页（两卡同源目录）

- **「模型连接」卡（聊天，现有卡升级）**：preset 下拉从 3 项扩到统一目录全量（glm/mimo/deepseek/moonshot/dashscope/volc-ark/siliconflow/自定义）；模型从手输改为目录下拉（按 `supportsToolCall` 过滤 + 「自定义 id」兜底）；Key 输入/清除逻辑不变。
- **「生图模型」卡（新增）**：同形制——preset 下拉 → 模型下拉（目录中 `IMAGE_GEN` 能力的模型，切换时高级参数面板随之更新）→ Key（mask 显示）→ 清除。与聊天卡选了同一 provider 时显示「已复用 <厂商> 的 Key」，不重复输入。
- **自检差异**：聊天卡保留 1-token ping；生图无免费 ping——保存仅格式校验，首次真实生成即验证（失败给明确引导）。
- 用量卡展示聊天 token 与生图张数/估算费用（纯信息展示，不拦截）。

---

## 5. 迭代拆分与 spec 影响

**it-077「AI 穿搭生图与多 Provider 生图网关」**，三阶段（详见 `wardrobe/specs/iterations/it-077-image-gen-provider-gateway.md`）：

| 阶段 | 内容 | 交付门槛 |
|---|---|---|
| A · SDK 统一目录与生图轨 | `ProviderCatalog`（聊天+生图同注册表、capability/toolCall/参数声明）+ 聊天 preset 扩容（纯数据）+ image 包（同步适配器/DashScope 异步传输/Fake）+ Usage.images | 全部单测绿（不打真 API） |
| B · 直达路径 | W11 聊天卡升级 + 生图卡（同源目录、共享 Key）+ W7 生成 sheet 四态（含动态高级参数面板）+ 保存/角标 + mock 样张 | 模拟器实测 + 真机一次端到端 |
| C · 对话流路径 | 第 5 工具 + GeneratedImage 卡片 + 进度/失败态 + 系统提示词 | 对话内端到端实测截图 |

**spec 影响清单**：wardrobe 01（US-64a/b/c）、02（W7 生成 sheet、W11 生图卡、W13 图片卡）、03（OutfitImage 三字段）、04（data/ui 层生图轨）、05（生成 sheet 视觉与动效）、06（ADR-030 生图 provider 注册表与分轨路由）；libs/agent 00（架构图生图轨）、06（ADR-007 能力双轨与 ImageModel、ADR-008 SYNC/ASYNC 归一——若合并则 ADR-007 一条）；两处 CHANGELOG。

---

## 6. 风险与拍板记录

**风险**：
1. **软失败抽卡**（通用模型衣服纹理漂移）→ 默认多张候选 + 「重新生成」；prompt 模板里强调「严格保持参考衣物版型与颜色」。
2. **异步任务型 90s 生成**（仅二期 aitryon）→ 前台交互限定，进度事件流，180s deadline。
3. **上架合规** → 自用定位从简（AI 角标 + source 元数据保留）；若未来分发再补深度合成申报。
4. 价格波动（火山定价页 JS 渲染，个别数字多源交叉）→ `costPerImage` 只是展示快照，标注「以厂商账单为准」。

**拍板记录（2026-09-30，Leo）**：
- ✅ 应用主要自用、价格不敏感、不分发线上用户。
- ❌ 砍掉开销类策略：月度护栏、对话流生成前确认、成本拦截、首次隐私弹窗（sheet 一行静态提示替代）。
- ✅ 新增模型专属参数透出：`ModelSpec.params` 声明式 schema + 生成页动态高级面板 + 按模型记忆（`aiImageLastParams`）+ 自定义 preset JSON 透传 + 对话流工具 `extraParams` 覆盖。
- ✅ **二次澄清：provider 抽象覆盖聊天轨**——不是只给生图做多 provider；聊天 preset 同步扩容（deepseek/moonshot/dashscope/volc-ark/siliconflow，全部 OpenAI 兼容零传输代码），与生图共用统一 `ProviderCatalog`，一把 Key 双轨共用，聊天下拉按 tool call 能力过滤。

**默认执行项**（拍板方向下的一并简化，如有异议随时改）：默认主力排序照 §2.3（cogview「灵感图」不进 v1）；备援回退 v1 不做、接口留位；`OutfitImage` 三字段保留（AI 图区分与参数溯源都靠它）；aitryon 放二期（SDK 传输 v1 实现并单测、UI 不放）；对话流顾问直接生图无确认。

---

## 7. 参考来源（节选）

- 百炼：[qwen-image-edit API](https://help.aliyun.com/zh/model-studio/qwen-image-edit-api) / [aitryon-plus](https://www.alibabacloud.com/help/zh/model-studio/aitryon-plus-api) / [全量价格](https://www.alibabacloud.com/help/zh/model-studio/model-pricing)
- 火山方舟：[模型价格](https://www.volcengine.com/docs/82379/1099320)（JS 页需控制台复核）/ [模型列表](https://www.volcengine.com/docs/82379/1554521)
- 硅基流动：[定价](https://siliconflow.cn/pricing) / [images API](https://docs.siliconflow.cn/cn/api-reference/images/images-generations)
- 智谱：[cogview-4](https://docs.bigmodel.cn/cn/guide/models/image-generation/cogview-4) / glm-image
- 可灵：[图片定价](https://www.klingai.com/document-api/pricing/base/image)；腾讯：[混元生图计费](https://cloud.tencent.com/document/product/1729/105925)、[Hy Image 3.5](https://cloud.tencent.com/document/product/1668/124633)
- 抽象参考：[OpenCode providers](https://docs.opencode.ai/docs/providers) / [models.dev](https://models.dev/api.json) / [cc-switch](https://github.com/farion1231/cc-switch) / [LiteLLM routing](https://docs.litellm.ai/docs/routing)、[reliability](https://docs.litellm.ai/docs/proxy/reliability)、[DashScope 生图缺失 issue #28763](https://github.com/BerriAI/litellm/issues/28763) / [OpenRouter models](https://openrouter.ai/api/v1/models) / [one-api](https://github.com/songquanpeng/one-api)
- 实测参考：[掘金·Qwen-Image-Edit 2509 虚拟试穿](https://juejin.cn/post/7560911782143918130)、[掘金·Qwen-Image-2.1 多图穿搭](https://juejin.cn/post/7689030783365365786)、[fashn.ai 开源 VTON 对比](https://fashn.ai/blog/comparing-the-top-4-open-source-virtual-try-on-viton-models)

> 价格核实状态说明：标 ✅ 者经官方文档直接核实；⚠️ 为第三方转述需控制台复核；火山 seededit 单价、百炼国内免费额度确切张数未查到，已在上表标注。接入任一家前，在对应控制台再核一次刊例。
