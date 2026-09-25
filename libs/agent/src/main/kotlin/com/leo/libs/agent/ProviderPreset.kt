package com.leo.libs.agent

/**
 * 厂商协议差异位标志——quirks 是数据不是代码分支（ADR-002）。
 */
data class Quirks(
    /** 推理内容走 delta.reasoning_content 字段（GLM 混合推理 / MiMo 推理档） */
    val reasoningField: Boolean = false,
    /** response_format json 支持度；Unknown/PromptFallback 时结构化输出走 prompt 兜底 + 解析 */
    val jsonMode: JsonModeSupport = JsonModeSupport.Unknown,
    /** 回喂时 tool_call_id 原样透传是否被接受（M0 已实证：GLM / MiMo 均接受） */
    val toolCallIdStable: Boolean = true,
)

enum class JsonModeSupport { Native, PromptFallback, Unknown }

data class ModelInfo(
    val name: String,
    val tier: String = "",
    /** null = 待 M0 校准回填 */
    val contextTokens: Long? = null,
)

/**
 * 厂商预设：纯数据，新厂商 = 新增一条 preset，零代码分支（ADR-002）。
 * baseUrl 为完整前缀（传输层只追加 /chat/completions）——GLM 无 /v1、MiMo /v1 的路径差异天然由数据表达。
 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<ModelInfo>,
    val quirks: Quirks = Quirks(),
) {
    val defaultModel: String get() = models.first().name

    companion object {
        fun custom(
            id: String,
            displayName: String,
            baseUrl: String,
            models: List<String>,
            reasoningField: Boolean = false,
        ) = ProviderPreset(
            id = id,
            displayName = displayName,
            baseUrl = baseUrl,
            models = models.map { ModelInfo(it) },
            quirks = Quirks(reasoningField = reasoningField),
        )
    }
}

/** 一等公民预设。M0 校准结果（2026-09-25 实调）见各 preset 注释；key 由用户自带（BYOK，ADR-003） */
object Providers {

    /** 智谱 GLM：open.bigmodel.cn，路径无 /v1（第三方客户端强拼 /v1 的 404 坑与本 SDK 无关）。
     *  M0 实调（plan key）：glm-4-flash 非流式/流式/工具调用/回喂全通过；4.6/4.5-air/5/5.3 返回
     *  1113 余额不足——模型可用性取决于账户资源包，列表保留全档由 UI 呈现错误。 */
    val glm = ProviderPreset(
        id = "glm",
        displayName = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            ModelInfo("glm-4.6", "旗舰档"),
            ModelInfo("glm-4.5-air", "轻量档"),
            ModelInfo("glm-4-flash", "免费档"),
        ),
        quirks = Quirks(reasoningField = true),
    )

    /** 小米 MiMo · 按量付费（sk- key）：官方文档确认 host，M0 未实调（无 sk- key）。
     *  注意：tp-/ttp- key 不可混用此 host（401 Invalid API Key，M0 实证），token 套餐见 [mimoTokenPlan]。 */
    val mimo = ProviderPreset(
        id = "mimo",
        displayName = "小米 MiMo · 按量付费",
        baseUrl = "https://api.xiaomimimo.com/v1",
        models = listOf(
            ModelInfo("mimo-v2.6-flash", "快速档", contextTokens = 1_000_000),
            ModelInfo("mimo-v2.6-pro", "推理档", contextTokens = 1_000_000),
        ),
        quirks = Quirks(reasoningField = true),
    )

    /** 小米 MiMo · Token 套餐（tp-/ttp- key 专属 host，与按量 host 不可混用）。
     *  M0 实调（tp- key）：flash/pro 非流式/流式（delta.reasoning_content 确认）/工具调用/回喂全通过；
     *  pro-ultraspeed 此 host 返回 400 Not supported，未列入。
     *  quirk 备忘：thinking 默认 enabled，官方建议调工具时关闭（当前实测开着也能出 tool_calls，暂不加传输层参数）。 */
    val mimoTokenPlan = ProviderPreset(
        id = "mimo-tp",
        displayName = "小米 MiMo · Token 套餐",
        baseUrl = "https://token-plan-cn.xiaomimimo.com/v1",
        models = listOf(
            ModelInfo("mimo-v2.6-flash", "快速档", contextTokens = 1_000_000),
            ModelInfo("mimo-v2.6-pro", "推理档", contextTokens = 1_000_000),
        ),
        quirks = Quirks(reasoningField = true),
    )

    /** 设置页厂商列表（一等公民全量） */
    val all: List<ProviderPreset> = listOf(glm, mimo, mimoTokenPlan)
}
