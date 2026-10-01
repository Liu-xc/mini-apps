package com.leo.wardrobe.data.mock

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ProviderSpec
import com.leo.libs.agent.image.GeneratedImage
import com.leo.libs.agent.image.ImageGenEvent
import com.leo.libs.agent.image.ImageGenRequest
import com.leo.libs.agent.image.ImageModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * it-078 · 演示模式的生图假实现：不出网、不计费，固定回放内置效果图样张。
 * 不回放输入长图（那只是衣物拼贴，不是生成结果）；样张未能读取时显式失败，
 * 绝不把 1×1 透明蓝像素伪装成候选图。
 */
class MockImageModel(
    override val provider: ProviderSpec,
    private val sampleImage: suspend () -> ByteArray?,
) : ImageModel {

    companion object {
        const val SAMPLE_IMAGE_FILE = "effect-weekend.png"
        private const val DEMO_GENERATION_DELAY_MS = 700L
    }

    override fun generate(request: ImageGenRequest): Flow<ImageGenEvent> = flow {
        emit(ImageGenEvent.Started)
        emit(ImageGenEvent.Progress("演示生成中…"))
        // 保留可见的生成态；否则本地样张读取过快，演示里看起来像没有 loading。
        delay(DEMO_GENERATION_DELAY_MS)
        val bytes = try {
            sampleImage()?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (bytes == null) {
            emit(ImageGenEvent.Failed(AgentError.Provider(-1, "演示效果图样张缺失，请重试")))
            return@flow
        }
        emit(ImageGenEvent.Completed(listOf(GeneratedImage(bytes, "image/png")), 1))
    }.flowOn(Dispatchers.IO)
}
