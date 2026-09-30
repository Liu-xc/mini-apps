package com.leo.wardrobe.data.mock

import com.leo.libs.agent.ProviderSpec
import com.leo.libs.agent.image.GeneratedImage
import com.leo.libs.agent.image.ImageGenEvent
import com.leo.libs.agent.image.ImageGenRequest
import com.leo.libs.agent.image.ImageModel
import com.leo.libs.agent.image.ImageRef
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * it-077 · 演示模式的生图假实现：不出网、不计费。
 * 结果取首张参考图（人物照）的字节回放——演示生图链路的 UI 四态与落盘挂载，
 * 不承诺生成内容（演示模式的诚实边界，与 CachedMockChatModel 同族定位但更简单：
 * 生图不做「有 Key 才真实外呼」，演示一律走假实现）。
 */
class MockImageModel(override val provider: ProviderSpec) : ImageModel {

    override fun generate(request: ImageGenRequest): Flow<ImageGenEvent> = flow {
        emit(ImageGenEvent.Started)
        emit(ImageGenEvent.Progress("演示生成中…"))
        val bytes = request.images.asSequence()
            .filterIsInstance<ImageRef.DataUri>()
            .firstOrNull()
            ?.let { ref ->
                runCatching { Base64.getMimeDecoder().decode(ref.value.substringAfter("base64,", "")) }.getOrNull()
            }
            ?: TINY_PNG
        emit(ImageGenEvent.Completed(listOf(GeneratedImage(bytes, "image/jpeg")), 1))
    }

    private companion object {
        /** 1×1 灰点 PNG（纯文生图无参考图时的兜底） */
        val TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
        )
    }
}
