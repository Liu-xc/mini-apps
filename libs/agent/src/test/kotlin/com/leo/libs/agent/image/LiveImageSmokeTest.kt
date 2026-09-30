package com.leo.libs.agent.image

import com.leo.libs.agent.Capability
import com.leo.libs.agent.ImageProtocol
import com.leo.libs.agent.InMemoryApiKeyStore
import com.leo.libs.agent.ModelSpec
import com.leo.libs.agent.ProviderSpec
import java.io.File
import java.util.Base64
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * it-077 · live 冒烟（默认跳过）：只有显式提供 SF_KEY 环境变量时才打真 API——
 * 「CI 永不打真 API」红线不变（无 Key assume 跳过）。验证 OkHttpImageModel 对硅基流动
 * 真实端点的完整链路：data-URI base64 上传 → images[].url 解析 → 预签名 URL 匿名下载。
 * 运行：SF_KEY=sk-… ./gradlew test --tests '*LiveImageSmokeTest*'
 */
class LiveImageSmokeTest {

    @Test
    fun `硅基流动 Qwen-Image-Edit 真端点冒烟`() = runBlocking {
        val key = System.getenv("SF_KEY") ?: run {
            assumeTrue("SF_KEY 未设置，跳过 live 冒烟（CI 红线：不打真 API）", false)
            return@runBlocking
        }
        val refPng = System.getenv("SF_REF")  // 可选：参考图绝对路径（缺省用内置 1px）
        val dataUri = if (!refPng.isNullOrBlank() && File(refPng).exists()) {
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(File(refPng).readBytes())
        } else {
            // 1×1 灰点（端点冒烟够用；内容质量不在本测试范围）
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
        }
        val provider = ProviderSpec(
            id = "siliconflow",
            displayName = "硅基流动",
            imageBaseUrl = "https://api.siliconflow.cn/v1",
            models = listOf(
                ModelSpec(
                    id = "Qwen/Qwen-Image-Edit",
                    capabilities = setOf(Capability.IMAGE_GEN),
                    imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC,
                    inputImages = 1..1,
                ),
            ),
        )
        val model = OkHttpImageModel(provider, InMemoryApiKeyStore().also { it.put("siliconflow", key) })

        val events = model.generate(
            ImageGenRequest(
                model = "Qwen/Qwen-Image-Edit",
                prompt = "把图中内容转换为摄影棚白底商品图，保持原样",
                images = listOf(ImageRef.DataUri(dataUri)),
                extra = mapOf("size" to kotlinx.serialization.json.JsonPrimitive("1328*1328")),
            ),
        ).toList()

        val completed = events.lastOrNull() as? ImageGenEvent.Completed
            ?: error("live 冒烟未成功：${events.lastOrNull()}")
        assertTrue("应返回至少 1 张图", completed.images.isNotEmpty())
        val bytes = completed.images.first().bytes
        val isPng = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        assertTrue("下载字节应为 PNG/JPEG（实际前 4 字节=${bytes.take(4)}）", isPng || isJpeg)
        println("live 冒烟 OK：${completed.images.size} 张，${bytes.size} bytes，mime=${completed.images.first().mime}")
    }
}
