package com.leo.libs.agent.testing

import com.leo.libs.agent.ProviderSpec
import com.leo.libs.agent.image.GeneratedImage
import com.leo.libs.agent.image.ImageGenEvent
import com.leo.libs.agent.image.ImageGenRequest
import com.leo.libs.agent.image.ImageModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 生图假实现（it-077，对齐 FakeChatModel 定位）：CI 永不打真 API。
 * [script] 按请求给出结果字节（可断言入参），[error] 非空时走 Failed 终态。
 */
class FakeImageModel(
    override val provider: ProviderSpec,
    private val script: suspend (ImageGenRequest) -> List<GeneratedImage> = { emptyList() },
    private val error: com.leo.libs.agent.AgentError? = null,
) : ImageModel {

    val requests = mutableListOf<ImageGenRequest>()

    override fun generate(request: ImageGenRequest): Flow<ImageGenEvent> = flow {
        emit(ImageGenEvent.Started)
        requests += request
        error?.let {
            emit(ImageGenEvent.Failed(it))
            return@flow
        }
        emit(ImageGenEvent.Progress("测试生成中…"))
        val images = script(request)
        if (images.isEmpty()) {
            emit(
                ImageGenEvent.Failed(
                    com.leo.libs.agent.AgentError.Provider(-1, "FakeImageModel 未提供结果"),
                ),
            )
        } else {
            emit(ImageGenEvent.Completed(images, images.size))
        }
    }
}
