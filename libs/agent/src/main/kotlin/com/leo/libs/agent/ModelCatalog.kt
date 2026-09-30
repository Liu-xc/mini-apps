package com.leo.libs.agent

import kotlinx.serialization.json.JsonPrimitive

/**
 * 模型能力分轨标签（it-077 ADR-007）：路由按用途选轨（顾问聊天 / 穿搭生图各绑各的），
 * UI 按 capability 过滤可选模型。
 */
enum class Capability { CHAT, IMAGE_GEN }

/**
 * 生图交互协议（it-077）：LiteLLM 至今没归一异步任务型（issue #28763），国内厂商又普遍存在，
 * 这里显式建模——同步/异步差异在适配器内消化，上层只见事件流。
 */
enum class ImageProtocol {
    /** OpenAI images 兼容：POST {base}/images/generations 同步返回（火山 / 硅基流动 / 智谱 CogView） */
    OPENAI_IMAGES_SYNC,

    /** 百炼 multimodal-generation 同步端点（qwen-image-edit 系，支持 base64 直传） */
    DASHSCOPE_SYNC,

    /** 百炼异步任务型：X-DashScope-Async 提交 + 轮询 tasks/{id}（aitryon / wan 系，仅收公网 URL） */
    DASHSCOPE_ASYNC_TASK,
}

/** 高级参数面板控件类型（it-077 拍板①：模型专属配置透出给使用者调） */
enum class ParamType { BOOL, INT, FLOAT, ENUM, TEXT }

/**
 * 模型专属参数声明（it-077 拍板①）：声明什么就透传什么（OpenRouter supported_parameters 思想），
 * 适配器把面板收集的值原样放进请求体，不为每个厂商开关建抽象字段。
 */
data class ImageParamSpec(
    /** 请求体字段名（watermark / negative_prompt / batch_size …） */
    val key: String,
    /** UI 显示名 */
    val label: String,
    val type: ParamType,
    /** ENUM 取值域 */
    val options: List<String> = emptyList(),
    val default: JsonPrimitive,
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * 统一目录的模型条目（it-077）：聊天与生图共用一个 ModelSpec，能力/门槛/参数静态声明
 * （字段语义对齐 models.dev 与 OpenRouter 模型目录的并集，按需裁剪）。
 */
data class ModelSpec(
    val id: String,
    /** 档位/用途短注（UI 下拉的次行） */
    val tier: String = "",
    val capabilities: Set<Capability>,

    // ---- CHAT 轨 ----

    /** 顾问 loop 的硬门槛：下拉只列可用的（不支持工具调用的模型不进顾问可选列表） */
    val supportsToolCall: Boolean = false,
    val contextTokens: Long? = null,

    // ---- IMAGE_GEN 轨 ----

    val imageProtocol: ImageProtocol? = null,
    /** 参考图张数域；0..0 = 纯文生图；null = 非生图模型 */
    val inputImages: IntRange? = null,
    /** 单次输出张数域 */
    val outputs: IntRange = 1..1,
    /** false = 只收公网 URL（aitryon），本地图 base64 通道应在 UI 层禁用并说明 */
    val supportsBase64: Boolean = true,
    /** 可选分辨率档（透传 size 字段；取值以厂商文档为准） */
    val resolutions: List<String> = emptyList(),
    val params: List<ImageParamSpec> = emptyList(),

    /** 元/张 快照（纯展示标注「以厂商账单为准」，不做拦截——it-077 拍板①） */
    val costPerImage: Double? = null,
    val note: String = "",
)

/**
 * 统一厂商条目（it-077 ADR-007）：一个厂商声明它的聊天端点与生图端点，模型自带能力标签。
 * 聊天传输不动 ADR-002——[chatPreset] 继续喂 OkHttpChatModel，新厂商 = 一条数据零代码；
 * Key 按本 id 存取（消费方 ApiKeyStore 现机制），同一厂商聊天/生图一把 Key 双轨共用。
 */
data class ProviderSpec(
    val id: String,
    val displayName: String,
    /** OpenAI 兼容聊天端点（null = 该厂商无聊天） */
    val chatPreset: ProviderPreset? = null,
    /** 生图端点前缀（null = 该厂商无生图） */
    val imageBaseUrl: String? = null,
    val models: List<ModelSpec> = emptyList(),
) {
    /**
     * 聊天模型目录：显式声明优先；未声明时从 [chatPreset].models 派生
     * （glm/mimo 的 M0 校准档位继续由 preset 承载，目录不重复维护）。
     */
    val chatModels: List<ModelSpec>
        get() = models.filter { Capability.CHAT in it.capabilities }.ifEmpty {
            chatPreset?.models?.map { m ->
                ModelSpec(
                    id = m.name,
                    tier = m.tier,
                    capabilities = setOf(Capability.CHAT),
                    // 派生条目默认可工具调用：一等公民 preset 的档位均已实测或官方支持
                    supportsToolCall = true,
                    contextTokens = m.contextTokens,
                )
            }.orEmpty()
        }

    /** 生图模型目录 */
    val imageModels: List<ModelSpec> get() = models.filter { Capability.IMAGE_GEN in it.capabilities }

    fun model(id: String): ModelSpec? = models.find { it.id == id }

    companion object {
        /**
         * 自定义生图厂商（OpenAI images 兼容 baseUrl + 手输模型 id）。
         * id 独立用 custom-image——与聊天轨的 custom 分属不同 Keystore 槽位，
         * 两个自定义端点的 Key 不会互相覆盖。
         */
        fun customImage(baseUrl: String, model: String): ProviderSpec = ProviderSpec(
            id = "custom-image",
            displayName = "自定义",
            imageBaseUrl = baseUrl.trimEnd('/'),
            models = listOf(
                ModelSpec(
                    id = model.trim(),
                    tier = "自定义模型",
                    capabilities = setOf(Capability.IMAGE_GEN),
                    imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                    inputImages = 0..9,
                ),
            ),
        )
    }
}

/**
 * 统一模型目录（it-077）：聊天与生图共用一张注册表，全部纯数据（ADR-002 延续）。
 * 模型 id 漂移快（各家季度级上新）——目录随版本更新，两轨 UI 均保留「自定义模型 id」兜底。
 * 价格与档位为 2026-09 调研快照（reports/2026-09-30-wardrobe-imagegen-research），接入前以控制台刊例为准。
 */
object ModelCatalog {

    /** 智谱：聊天沿用 [Providers.glm]（M0 实调档位）；生图 CogView-4 纯文生图（无图片输入，仅「灵感图」定位） */
    val glm = ProviderSpec(
        id = "glm",
        displayName = "智谱 GLM",
        chatPreset = Providers.glm,
        imageBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            ModelSpec(
                id = "cogview-4",
                tier = "灵感图 · 纯文生图",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 0..0,
                resolutions = listOf("1024x1024", "768x1344", "864x1152", "1344x768", "1152x864", "1440x720", "720x1440"),
                costPerImage = 0.06,
                params = listOf(
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("1024x1024", "768x1344", "864x1152", "1344x768", "1152x864", "1440x720", "720x1440"), JsonPrimitive("1024x1024")),
                ),
            ),
        ),
    )

    /** 小米 MiMo：现状不动（两档 preset 原样并入目录） */
    val mimo = ProviderSpec(id = "mimo", displayName = "小米 MiMo · 按量付费", chatPreset = Providers.mimo)

    val mimoTokenPlan = ProviderSpec(id = "mimo-tp", displayName = "小米 MiMo · Token 套餐", chatPreset = Providers.mimoTokenPlan)

    /** DeepSeek：OpenAI 兼容；reasoner 档不保证工具调用（目录声明 false，UI 过滤兜底） */
    val deepseek = ProviderSpec(
        id = "deepseek",
        displayName = "DeepSeek",
        chatPreset = ProviderPreset(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            models = listOf(ModelInfo("deepseek-chat"), ModelInfo("deepseek-reasoner")),
        ),
        models = listOf(
            ModelSpec("deepseek-chat", "对话档", setOf(Capability.CHAT), supportsToolCall = true, contextTokens = 128_000),
            ModelSpec("deepseek-reasoner", "推理档", setOf(Capability.CHAT), supportsToolCall = false, contextTokens = 128_000),
        ),
    )

    /** Kimi（月之暗面）：OpenAI 兼容 */
    val moonshot = ProviderSpec(
        id = "moonshot",
        displayName = "Kimi",
        chatPreset = ProviderPreset(
            id = "moonshot",
            displayName = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            models = listOf(ModelInfo("kimi-k2-turbo-preview"), ModelInfo("kimi-k2-0905-preview")),
        ),
        models = listOf(
            ModelSpec("kimi-k2-turbo-preview", "快速档", setOf(Capability.CHAT), supportsToolCall = true, contextTokens = 256_000),
            ModelSpec("kimi-k2-0905-preview", "旗舰档", setOf(Capability.CHAT), supportsToolCall = true, contextTokens = 256_000),
        ),
    )

    /** 阿里百炼：聊天走 compatible-mode OpenAI 兼容端点；生图 qwen-image-edit 系同步 + aitryon 异步（二期） */
    val dashscope = ProviderSpec(
        id = "dashscope",
        displayName = "阿里百炼",
        chatPreset = ProviderPreset(
            id = "dashscope",
            displayName = "阿里百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf(ModelInfo("qwen3-max"), ModelInfo("qwen-plus"), ModelInfo("qwen-flash")),
        ),
        imageBaseUrl = "https://dashscope.aliyuncs.com",
        models = listOf(
            ModelSpec("qwen3-max", "旗舰档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec("qwen-plus", "均衡档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec("qwen-flash", "快速档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec(
                id = "qwen-image-edit-plus",
                tier = "主力 · 图文编辑",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.DASHSCOPE_SYNC,
                inputImages = 1..3,
                outputs = 1..6,
                resolutions = listOf("auto", "1024*1024", "832*1248", "1248*832", "928*1664", "1664*928", "1080*1920", "1920*1080"),
                costPerImage = 0.2,
                params = listOf(
                    ImageParamSpec("n", "张数", ParamType.INT, default = JsonPrimitive(2), min = 1.0, max = 6.0),
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("auto", "1024*1024", "832*1248", "1248*832", "928*1664", "1664*928", "1080*1920", "1920*1080"), JsonPrimitive("auto")),
                    ImageParamSpec("negative_prompt", "负向词", ParamType.TEXT, default = JsonPrimitive("")),
                    ImageParamSpec("prompt_extend", "提示词改写", ParamType.BOOL, default = JsonPrimitive(true)),
                    ImageParamSpec("watermark", "水印", ParamType.BOOL, default = JsonPrimitive(false)),
                    ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(0), min = 0.0, max = 2147483647.0),
                ),
            ),
            ModelSpec(
                id = "qwen-image-edit",
                tier = "图文编辑 · 单张",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.DASHSCOPE_SYNC,
                inputImages = 1..3,
                outputs = 1..1,
                costPerImage = 0.3,
                params = listOf(
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("auto", "1024*1024", "832*1248", "1248*832", "928*1664", "1664*928"), JsonPrimitive("auto")),
                    ImageParamSpec("negative_prompt", "负向词", ParamType.TEXT, default = JsonPrimitive("")),
                    ImageParamSpec("watermark", "水印", ParamType.BOOL, default = JsonPrimitive(false)),
                ),
            ),
            ModelSpec(
                id = "aitryon-plus",
                tier = "AI 试衣 · 二期（仅公网 URL）",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.DASHSCOPE_ASYNC_TASK,
                inputImages = 2..3,
                supportsBase64 = false,
                resolutions = listOf("1024", "720*1280"),
                costPerImage = 0.5,
                note = "保真试衣专用（人物+上装+下装）；只收公网 URL，本地照片需图床前置——二期开放",
                params = listOf(
                    ImageParamSpec("restore_face", "保留原人脸", ParamType.BOOL, default = JsonPrimitive(true)),
                ),
            ),
        ),
    )

    /** 火山方舟：聊天豆包 seed 系；生图 Seedream 多图参考（个人实名可用；模型 id 以方舟控制台为准） */
    val volcArk = ProviderSpec(
        id = "volc-ark",
        displayName = "火山方舟",
        chatPreset = ProviderPreset(
            id = "volc-ark",
            displayName = "火山方舟",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            models = listOf(ModelInfo("doubao-seed-1-6-250615"), ModelInfo("doubao-seed-1-6-flash-250828")),
        ),
        imageBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf(
            ModelSpec("doubao-seed-1-6-250615", "旗舰档", setOf(Capability.CHAT), supportsToolCall = true, contextTokens = 256_000),
            ModelSpec("doubao-seed-1-6-flash-250828", "快速档", setOf(Capability.CHAT), supportsToolCall = true, contextTokens = 256_000),
            ModelSpec(
                id = "doubao-seedream-4-0-250828",
                tier = "多图参考 ≤14 张",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 0..14,
                resolutions = listOf("1K", "2K", "4K"),
                costPerImage = 0.2,
                params = listOf(
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("1K", "2K", "4K"), JsonPrimitive("2K")),
                    ImageParamSpec("watermark", "水印", ParamType.BOOL, default = JsonPrimitive(true)),
                    ImageParamSpec("guidance_scale", "贴合度", ParamType.FLOAT, default = JsonPrimitive(2.5), min = 0.0, max = 10.0),
                    ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(-1)),
                ),
            ),
            ModelSpec(
                id = "doubao-seedream-5-0-lite",
                tier = "多图参考 ≤14 张 · 4K",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 0..14,
                resolutions = listOf("2K", "3K", "4K"),
                costPerImage = 0.22,
                note = "模型 id 以方舟控制台为准",
                params = listOf(
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("2K", "3K", "4K"), JsonPrimitive("2K")),
                    ImageParamSpec("watermark", "水印", ParamType.BOOL, default = JsonPrimitive(true)),
                    ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(-1)),
                ),
            ),
        ),
    )

    /** 硅基流动：聚合平台——一把 Key 同时覆盖聊天与生图（Kolors 免费），BYOK 的瑞士军刀 */
    val siliconflow = ProviderSpec(
        id = "siliconflow",
        displayName = "硅基流动",
        chatPreset = ProviderPreset(
            id = "siliconflow",
            displayName = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            models = listOf(
                ModelInfo("deepseek-ai/DeepSeek-V3.1"),
                ModelInfo("Qwen/Qwen3-32B"),
                ModelInfo("Qwen/Qwen3-8B"),
            ),
        ),
        imageBaseUrl = "https://api.siliconflow.cn/v1",
        models = listOf(
            ModelSpec("deepseek-ai/DeepSeek-V3.1", "旗舰档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec("Qwen/Qwen3-32B", "均衡档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec("Qwen/Qwen3-8B", "免费档", setOf(Capability.CHAT), supportsToolCall = true),
            ModelSpec(
                id = "Kolors/Kolors",
                tier = "文生图 · 免费",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 0..0,
                costPerImage = 0.0,
                params = listOf(
                    ImageParamSpec("batch_size", "张数", ParamType.INT, default = JsonPrimitive(1), min = 1.0, max = 4.0),
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("1024x1024", "960x1280", "768x1344", "1440x720", "1248x832"), JsonPrimitive("1024x1024")),
                    ImageParamSpec("negative_prompt", "负向词", ParamType.TEXT, default = JsonPrimitive("")),
                    ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(-1)),
                ),
            ),
            ModelSpec(
                id = "Z-Image-Turbo",
                tier = "文生图 · 快速",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 0..0,
                costPerImage = 0.1,
                params = listOf(
                    ImageParamSpec("batch_size", "张数", ParamType.INT, default = JsonPrimitive(1), min = 1.0, max = 4.0),
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("1024x1024", "864x1152", "1152x864", "1440x720", "720x1440"), JsonPrimitive("1024x1024")),
                ),
            ),
            ModelSpec(
                id = "Qwen/Qwen-Image-Edit",
                tier = "图文编辑",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                inputImages = 1..3,
                costPerImage = 0.3,
                params = listOf(
                    ImageParamSpec("size", "尺寸", ParamType.ENUM, listOf("auto", "1328*1328", "1664*928", "928*1664"), JsonPrimitive("auto")),
                    ImageParamSpec("seed", "种子", ParamType.INT, default = JsonPrimitive(0)),
                ),
            ),
        ),
    )

    /** 设置页厂商列表（统一目录全量；聊天轨按 chatPreset 非空过滤展示） */
    val all: List<ProviderSpec> = listOf(glm, mimo, mimoTokenPlan, deepseek, moonshot, dashscope, volcArk, siliconflow)

    fun byId(id: String): ProviderSpec? = all.find { it.id == id }
}
