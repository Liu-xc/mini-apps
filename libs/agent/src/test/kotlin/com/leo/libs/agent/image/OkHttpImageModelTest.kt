package com.leo.libs.agent.image

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ApiKeyStore
import com.leo.libs.agent.Capability
import com.leo.libs.agent.ImageProtocol
import com.leo.libs.agent.InMemoryApiKeyStore
import com.leo.libs.agent.ModelSpec
import com.leo.libs.agent.ProviderSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** it-077：同步生图传输（OpenAI images 兼容 + 百炼 multimodal-generation） */
class OkHttpImageModelTest {

    private lateinit var server: MockWebServer
    private lateinit var keys: ApiKeyStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        keys = InMemoryApiKeyStore()
        runBlocking { keys.put("t", "k-9") }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun openaiSpec(input: IntRange = 1..3) = ProviderSpec(
        id = "t",
        displayName = "测试厂",
        imageBaseUrl = server.url("/v1").toString(),
        models = listOf(
            ModelSpec("img-1", capabilities = setOf(Capability.IMAGE_GEN), imageProtocol = ImageProtocol.OPENAI_IMAGES_SYNC, inputImages = input),
        ),
    )

    private fun dashscopeSpec() = ProviderSpec(
        id = "t",
        displayName = "测试厂",
        imageBaseUrl = server.url("/ds").toString(),
        models = listOf(
            ModelSpec("edit-plus", capabilities = setOf(Capability.IMAGE_GEN), imageProtocol = ImageProtocol.DASHSCOPE_SYNC, inputImages = 1..3),
        ),
    )

    private fun dataUri(n: Int) = ImageRef.DataUri("data:image/jpeg;base64,AAA$n")

    private fun enqueueUrlResult() {
        val url = server.url("/gen-out.png").toString()
        server.enqueue(
            MockResponse().setBody("""{"created":1,"data":[{"url":"$url"}],"usage":{"image_count":1}}"""),
        )
        server.enqueue(MockResponse().setBody("PNGBYTES").addHeader("Content-Type", "image/png"))
    }

    private fun lastEvents(model: ImageModel, request: ImageGenRequest): List<ImageGenEvent> =
        runBlocking { model.generate(request).toList() }

    @Test
    fun `OpenAI 形 - 单图传字符串 - 下载字节返回`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        enqueueUrlResult()
        val events = model.generate(
            ImageGenRequest(model = "img-1", prompt = "试穿", images = listOf(dataUri(1)), resolution = "2K"),
        ).toList()

        assertTrue(events.last() is ImageGenEvent.Completed)
        val completed = events.last() as ImageGenEvent.Completed
        assertEquals(1, completed.imageCount)
        assertEquals("PNGBYTES", String(completed.images.single().bytes))
        assertEquals("image/png", completed.images.single().mime)

        val recorded = server.takeRequest()
        assertEquals("/v1/images/generations", recorded.path)
        assertEquals("Bearer k-9", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8().let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
        assertEquals("img-1", body["model"]!!.jsonPrimitive.content)
        assertEquals("试穿", body["prompt"]!!.jsonPrimitive.content)
        // 单图 = 字符串形态（非数组）
        assertEquals(dataUri(1).value, body["image"]!!.jsonPrimitive.content)
        assertEquals("2K", body["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun `多图走数组形态`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        enqueueUrlResult()
        model.generate(ImageGenRequest("img-1", "多图", listOf(dataUri(1), dataUri(2)))).toList()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"image\":["))
    }

    @Test
    fun `硅基 images 形响应可解析`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        val url = server.url("/sf-out.png").toString()
        server.enqueue(MockResponse().setBody("""{"images":[{"url":"$url"}]}"""))
        server.enqueue(MockResponse().setBody("SF"))
        val events = model.generate(ImageGenRequest("img-1", "t", listOf(dataUri(1)))).toList()
        val completed = events.last() as ImageGenEvent.Completed
        assertEquals("SF", String(completed.images.single().bytes))
    }

    @Test
    fun `b64_json 直返免下载`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        // "aGVsbG8=" = "hello"
        server.enqueue(MockResponse().setBody("""{"data":[{"b64_json":"aGVsbG8="}]}"""))
        val events = model.generate(ImageGenRequest("img-1", "t", listOf(dataUri(1)))).toList()
        val completed = events.last() as ImageGenEvent.Completed
        assertEquals("hello", String(completed.images.single().bytes))
        assertEquals(1, server.requestCount) // 无第二次下载请求
    }

    @Test
    fun `透传参数覆盖进请求体`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        enqueueUrlResult()
        model.generate(
            ImageGenRequest(
                "img-1", "t", listOf(dataUri(1)),
                extra = mapOf("watermark" to JsonPrimitive(false), "guidance_scale" to JsonPrimitive(3.5)),
            ),
        ).toList()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"watermark\":false"))
        assertTrue(body.contains("\"guidance_scale\":3.5"))
    }

    @Test
    fun `HTTP 401 归类 Auth`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"InvalidApiKey","message":"Invalid API-key"}}"""))
        val events = model.generate(ImageGenRequest("img-1", "t", listOf(dataUri(1)))).toList()
        val failed = events.last() as ImageGenEvent.Failed
        assertTrue(failed.error is AgentError.Auth)
    }

    @Test
    fun `图数超档位本地预检拦截 - 不发请求`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(input = 1..3), keys)
        val events = model.generate(
            ImageGenRequest("img-1", "t", listOf(dataUri(1), dataUri(2), dataUri(3), dataUri(4))),
        ).toList()
        val failed = events.last() as ImageGenEvent.Failed
        assertTrue(failed.error is AgentError.Schema)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `百炼同步 - content 顺序 image 后 text - parameters 合并`() = runBlocking {
        val model = OkHttpImageModel(dashscopeSpec(), keys)
        val url = server.url("/ds-out.png").toString()
        server.enqueue(
            MockResponse().setBody(
                """{"output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant",
                   "content":[{"image":"$url"}]}}]},"usage":{"image_count":2},"request_id":"r1"}""".trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("DS"))
        val events = model.generate(
            ImageGenRequest(
                model = "edit-plus",
                prompt = "穿上这套",
                images = listOf(dataUri(1), dataUri(2)),
                resolution = "1024*1024",
                extra = mapOf("n" to JsonPrimitive(2), "watermark" to JsonPrimitive(false)),
            ),
        ).toList()

        val completed = events.last() as ImageGenEvent.Completed
        assertEquals("DS", String(completed.images.single().bytes))
        assertEquals(2, completed.imageCount) // usage.image_count 优先于实际返回张数

        val recorded = server.takeRequest()
        assertEquals("/ds/api/v1/services/aigc/multimodal-generation/generation", recorded.path)
        // 原文断言：两段 image 在 text 之前（content 顺序即语义），parameters 合并透传
        val raw = recorded.body.readUtf8()
        val firstImage = raw.indexOf("\"image\":")
        val textIdx = raw.indexOf("\"text\":")
        assertTrue(firstImage in 0 until textIdx)
        assertTrue(raw.contains("\"size\":\"1024*1024\""))
        assertTrue(raw.contains("\"n\":2"))
        assertTrue(raw.contains("\"watermark\":false"))
    }

    @Test
    fun `无 Key 直接 Auth 失败`() = runBlocking {
        val emptyKeys = InMemoryApiKeyStore()
        val model = OkHttpImageModel(openaiSpec(), emptyKeys)
        val events = model.generate(ImageGenRequest("img-1", "t", listOf(dataUri(1)))).toList()
        val failed = events.last() as ImageGenEvent.Failed
        assertTrue(failed.error is AgentError.Auth)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `事件流终态语义 - Failed 后正常结束`() = runBlocking {
        val model = OkHttpImageModel(openaiSpec(), keys)
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))
        val events = model.generate(ImageGenRequest("img-1", "t", listOf(dataUri(1)))).toList()
        assertEquals(ImageGenEvent.Started, events.first())
        assertTrue(events.last() is ImageGenEvent.Failed)
    }
}
