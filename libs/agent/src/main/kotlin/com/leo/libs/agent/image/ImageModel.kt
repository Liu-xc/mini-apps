package com.leo.libs.agent.image

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ApiKeyStore
import com.leo.libs.agent.ImageProtocol
import com.leo.libs.agent.ProviderSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

/**
 * 生图轨请求（it-077）。具名参数只做最小归一（prompt / 参考图 / resolution → size），
 * 其余模型专属开关经 [extra] 按目录 params 声明原样透传进请求体——不为每个厂商开关建抽象字段。
 * 注意：[extra] 与 [resolution] 均落 size 字段时 **extra 覆盖 resolution**（请求体 put 顺序，it-081 注明）。
 */
data class ImageGenRequest(
    /** ProviderSpec.models 里的模型 id */
    val model: String,
    val prompt: String,
    /** 参考图（人物照 / 衣物照），顺序即语义（首张通常为人物/底图，多图融合以最后一张定宽高比） */
    val images: List<ImageRef> = emptyList(),
    /** 分辨率档（透传 size；格式随厂商：火山 "2K" / 百炼 "1024*1024" / 硅基 "1024x1024"） */
    val resolution: String? = null,
    /** 高级面板收集的透传参数（key 见 ModelSpec.params） */
    val extra: Map<String, JsonElement> = emptyMap(),
)

/** 参考图入参：DataUri = data:image/jpeg;base64,…（本地图直传）；Url = 公网地址（aitryon 等只收这种） */
sealed interface ImageRef {
    val value: String

    data class DataUri(override val value: String) : ImageRef
    data class Url(override val value: String) : ImageRef
}

/** 生成结果：字节已在适配器内下载/解码完毕——App 不接触 24h 失效的临时 URL */
class GeneratedImage(
    val bytes: ByteArray,
    val mime: String,
    val revisedPrompt: String? = null,
)

/**
 * 生图事件流（ADR-005 事件流同构）：终态两种——[Completed] 或 [Failed]，之后 Flow 正常结束，
 * collector 无需 try/catch AgentError。同步/异步任务型协议的差异在适配器内消化：
 * 异步型把任务状态映射为 [Progress]，上层只见统一事件。
 */
sealed interface ImageGenEvent {
    data object Started : ImageGenEvent
    data class Progress(val message: String) : ImageGenEvent
    data class Completed(val images: List<GeneratedImage>, val imageCount: Int) : ImageGenEvent
    data class Failed(val error: AgentError) : ImageGenEvent
}

/**
 * 生图模型传输抽象（it-077 ADR-007）：与 ChatModel 平行的第二能力轨。
 * 唯一 HTTP 实现为 [OkHttpImageModel]（同步协议）与 [DashScopeTaskImageModel]（异步任务型），
 * 协议选择由 ModelSpec.imageProtocol 数据决定。
 */
interface ImageModel {
    val provider: ProviderSpec
    fun generate(request: ImageGenRequest): Flow<ImageGenEvent>

    companion object {
        /**
         * 协议路由工厂（it-081/O-2）：按模型声明的 [ImageProtocol] 选择实现——
         * 「选哪个适配器」是 SDK 职责，不泄漏给消费方；异步任务型返回 [DashScopeTaskImageModel]，
         * 其余（含未声明协议的自定义模型）返回 [OkHttpImageModel]。
         */
        fun of(
            provider: ProviderSpec,
            model: String,
            apiKeys: ApiKeyStore,
            client: OkHttpClient = OkHttpImageModel.defaultClient(),
        ): ImageModel = when (provider.model(model)?.imageProtocol) {
            ImageProtocol.DASHSCOPE_ASYNC_TASK -> DashScopeTaskImageModel(provider, apiKeys, client)
            else -> OkHttpImageModel(provider, apiKeys, client)
        }
    }
}
