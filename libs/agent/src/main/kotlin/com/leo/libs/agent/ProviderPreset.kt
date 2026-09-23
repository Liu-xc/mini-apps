package com.leo.libs.agent

/**
 * 厂商协议差异位标志——quirks 是数据不是代码分支（ADR-002）。
 */
data class Quirks(
    /** 推理内容走 delta.reasoning_content 字段（GLM 混合推理 / MiMo 推理档） */
    val reasoningField: Boolean = false,
    /** response_format json 支持度；Unknown/PromptFallback 时结构化输出走 prompt 兜底 + 解析 */
    val jsonMode: JsonModeSupport = JsonModeSupport.Unknown,
    /** 回喂时 tool_call_id 原样透传是否被接受（默认假定接受，待 M0 实证） */
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

/** 一等公民预设。标「待 M0 校准」的字段以实调回填为准（island it-001 先例）；key 由用户自带（BYOK，ADR-003） */
object Providers {

    /** 智谱 GLM：open.bigmodel.cn，路径无 /v1（第三方客户端强拼 /v1 的 404 坑与本 SDK 无关） */
    val glm = ProviderPreset(
        id = "glm",
        displayName = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            ModelInfo("glm-4.6", "旗舰档（待 M0 校准账号可用性）"),
            ModelInfo("glm-4.5-air", "轻量档"),
            ModelInfo("glm-4-flash", "免费档候选"),
        ),
        quirks = Quirks(reasoningField = true),
    )

    /** 小米 MiMo：开放平台 mimo.mi.com（2025-12 上线），OpenAI 兼容 /v1 风格；baseUrl 待 M0 校准回填 */
    val mimo = ProviderPreset(
        id = "mimo",
        displayName = "小米 MiMo",
        baseUrl = "", // TODO(M0)：以开放平台控制台文档为准回填（形如 https://…/v1）
        models = listOf(
            ModelInfo("mimo-v2.6-flash", "快速档（待 M0 校准）"),
            ModelInfo("mimo-v2.6-pro", "推理档"),
        ),
        quirks = Quirks(reasoningField = true),
    )
}
