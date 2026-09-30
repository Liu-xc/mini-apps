package com.leo.libs.agent.image

import com.leo.libs.agent.AgentError
import com.leo.libs.agent.Capability
import com.leo.libs.agent.ImageProtocol
import com.leo.libs.agent.InMemoryApiKeyStore
import com.leo.libs.agent.ModelSpec
import com.leo.libs.agent.ProviderSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** it-077：百炼异步任务型传输（aitryon 形态；v1 只测传输，UI 不放出） */
class DashScopeTaskImageModelTest {

    private lateinit var server: MockWebServer
    private lateinit var keys: InMemoryApiKeyStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        keys = InMemoryApiKeyStore()
        runBlocking { keys.put("t", "k-7") }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun spec() = ProviderSpec(
        id = "t",
        displayName = "测试厂",
        imageBaseUrl = server.url("/ds").toString(),
        models = listOf(
            ModelSpec(
                "aitryon-plus",
                capabilities = setOf(Capability.IMAGE_GEN),
                imageProtocol = ImageProtocol.DASHSCOPE_ASYNC_TASK,
                inputImages = 2..3,
                supportsBase64 = false,
            ),
        ),
    )

    private fun model(pollIntervalMs: Long = 0) =
        DashScopeTaskImageModel(spec(), keys, pollIntervalMs = pollIntervalMs, deadlineMs = 5_000)

    private fun submitBody(taskId: String = "t1") =
        """{"output":{"task_id":"$taskId","task_status":"PENDING"},"request_id":"r0"}"""

    @Test
    fun `提交 - 轮询至成功 - 下载`() = runBlocking {
        val resultUrl = server.url("/tryon-out.png").toString()
        server.enqueue(MockResponse().setBody(submitBody()))
        server.enqueue(MockResponse().setBody("""{"output":{"task_id":"t1","task_status":"RUNNING"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"output":{"task_id":"t1","task_status":"SUCCEEDED","results":[{"url":"$resultUrl"}]},
                   "usage":{"image_count":1}}""".trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("TRYON").addHeader("Content-Type", "image/png"))

        val events = model().generate(
            ImageGenRequest(
                model = "aitryon-plus",
                prompt = "（aitryon 不消费文本）",
                images = listOf(
                    ImageRef.Url("https://cdn.example.com/person.jpg"),
                    ImageRef.Url("https://cdn.example.com/top.jpg"),
                    ImageRef.Url("https://cdn.example.com/bottom.jpg"),
                ),
            ),
        ).toList()

        assertTrue(events.first() is ImageGenEvent.Started)
        val completed = events.last() as ImageGenEvent.Completed
        assertEquals("TRYON", String(completed.images.single().bytes))

        val submit = server.takeRequest()
        assertEquals("/ds/api/v1/services/aigc/image2image/image2image", submit.path)
        assertEquals("enable", submit.getHeader("X-DashScope-Async"))
        val raw = submit.body.readUtf8()
        assertTrue(raw.contains("\"person_image_url\":\"https://cdn.example.com/person.jpg\""))
        assertTrue(raw.contains("\"top_garment_url\""))
        assertTrue(raw.contains("\"bottom_garment_url\""))
        assertEquals("/ds/api/v1/tasks/t1", server.takeRequest().path)
        assertEquals("/ds/api/v1/tasks/t1", server.takeRequest().path)
    }

    @Test
    fun `任务 FAILED 携带厂商 code 与 message`() = runBlocking {
        server.enqueue(MockResponse().setBody(submitBody("t9")))
        server.enqueue(
            MockResponse().setBody(
                """{"output":{"task_id":"t9","task_status":"FAILED","code":"InvalidImage",
                   "message":"人物图必须为单人正面全身照"}}""".trimIndent(),
            ),
        )
        val events = model().generate(
            ImageGenRequest(
                model = "aitryon-plus",
                prompt = "",
                images = listOf(ImageRef.Url("https://x/p.jpg"), ImageRef.Url("https://x/t.jpg")),
            ),
        ).toList()
        val failed = events.last() as ImageGenEvent.Failed
        assertTrue(failed.error is AgentError.Provider)
        assertTrue(failed.error.userMessage.contains("InvalidImage"))
        assertTrue(failed.error.userMessage.contains("单人正面全身照"))
    }

    @Test
    fun `base64 入参在本地预检拦截`() = runBlocking {
        val events = model().generate(
            ImageGenRequest(
                model = "aitryon-plus",
                prompt = "",
                images = listOf(ImageRef.DataUri("data:image/jpeg;base64,AAA"), ImageRef.Url("https://x/t.jpg")),
            ),
        ).toList()
        val failed = events.last() as ImageGenEvent.Failed
        assertTrue(failed.error is AgentError.Schema)
        assertEquals(0, server.requestCount)
    }
}
