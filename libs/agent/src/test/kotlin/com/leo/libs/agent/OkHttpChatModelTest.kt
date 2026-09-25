package com.leo.libs.agent

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpChatModelTest {

    private lateinit var server: MockWebServer
    private lateinit var model: OkHttpChatModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        model = modelOf("test")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun modelOf(id: String, reasoningField: Boolean = false): OkHttpChatModel =
        OkHttpChatModel(
            ProviderPreset.custom(id, "Test", server.url("/v1").toString(), listOf("test-model"), reasoningField),
            keysOf(id),
        )

    private fun keysOf(id: String, key: String = "k-123"): InMemoryApiKeyStore =
        runBlocking {
            InMemoryApiKeyStore().also { it.put(id, key) }
        }

    private fun completionBody(text: String) =
        """{"id":"c1","choices":[{"index":0,"message":{"role":"assistant","content":"$text"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}"""

    @Test
    fun `路径拼接与鉴权头`() = runBlocking {
        server.enqueue(MockResponse().setBody(completionBody("hi")))
        model.complete(ChatRequest(messages = listOf(Message.user("你好"))))
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer k-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `请求体含模型与消息`() = runBlocking {
        server.enqueue(MockResponse().setBody(completionBody("hi")))
        model.complete(
            ChatRequest(
                messages = listOf(Message.system("你是管家"), Message.user("你好")),
                model = "test-model-pro",
                maxTokens = 32,
                temperature = 0.7,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model-pro\""))
        assertTrue(body.contains("\"max_tokens\":32"))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"content\":\"你是管家\""))
    }

    @Test
    fun `tools 与 tool_calls 回喂强制携带 type=function`() = runBlocking {
        // encodeDefaults=false 会把等默认值的 type 省略——GLM 严格校验必填（it-041 实测 1214）
        server.enqueue(MockResponse().setBody(completionBody("ok")))
        model.complete(
            ChatRequest(
                messages = listOf(
                    Message.user("查"),
                    Message.assistantToolCalls(listOf(ToolCall("c1", "search", """{"q":"x"}"""))),
                    Message.toolResult("c1", "结果"),
                ),
                tools = listOf(
                    ToolSpec("search", "搜索", Json.parseToJsonElement("""{"type":"object"}""")),
                ),
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        val json = Json.parseToJsonElement(body).jsonObject
        val tool = json.getValue("tools").jsonArray[0].jsonObject
        assertEquals("function", tool["type"]?.jsonPrimitive?.content)
        assertTrue(tool.getValue("function").jsonObject.containsKey("parameters"))
        val assistant = json.getValue("messages").jsonArray
            .map { it.jsonObject }
            .first { it["role"]?.jsonPrimitive?.content == "assistant" }
        val call = assistant.getValue("tool_calls").jsonArray[0].jsonObject
        assertEquals("function", call["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `带图消息序列化为 parts 数组`() = runBlocking {
        server.enqueue(MockResponse().setBody(completionBody("hi")))
        model.complete(ChatRequest(messages = listOf(Message.user("这是什么", listOf("https://x/img.jpg")))))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"type\":\"image_url\""))
        assertTrue(body.contains("https://x/img.jpg"))
    }

    @Test
    fun `非流式解析`() = runBlocking {
        server.enqueue(MockResponse().setBody(completionBody("hi")))
        val completion = model.complete(ChatRequest(messages = listOf(Message.user("q"))))
        assertEquals("hi", completion.message.text)
        assertEquals("stop", completion.finishReason)
        assertEquals(Usage(3, 2, 5), completion.usage)
    }

    @Test
    fun `流式全链路 文本与工具分片与 usage`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                : keep-alive
                data: {"choices":[{"delta":{"content":"你"}}]}

                data: {"choices":[{"delta":{"content":"好"}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c9","type":"function","function":{"name":"t","arguments":"{\"a\":"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"1}"}}]}}]}

                data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}

                data: [DONE]

                """.trimIndent(),
            ),
        )
        val events = model.stream(ChatRequest(messages = listOf(Message.user("q")))).toList()
        assertTrue(events.contains(ChatEvent.TextDelta("你")))
        assertTrue(events.contains(ChatEvent.TextDelta("好")))
        assertTrue(events.any { it is ChatEvent.ToolCallDelta && it.id == "c9" })
        val completed = events.last() as ChatEvent.Completed
        assertEquals(Usage(1, 1, 2), completed.completion.usage)
        assertEquals(listOf(ToolCall("c9", "t", """{"a":1}""")), completed.completion.message.toolCalls)
        assertEquals("tool_calls", completed.completion.finishReason)
    }

    @Test
    fun `reasoning_content 映射 ThinkingDelta`() = runBlocking {
        val m = modelOf("r", reasoningField = true)
        server.enqueue(
            MockResponse().setBody(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"嗯\"}}]}\n\ndata: [DONE]\n\n",
            ),
        )
        val events = m.stream(ChatRequest(messages = listOf(Message.user("q")))).toList()
        assertTrue(events.contains(ChatEvent.ThinkingDelta("嗯")))
    }

    @Test
    fun `401 映射 Auth 且透出厂商错误码`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"code":"1000","message":"无效的 API Key"}}"""),
        )
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (e: AgentError.Auth) {
            assertTrue(e.message!!.contains("1000"))
        }
    }

    @Test
    fun `429 带 Retry-After`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("{}"))
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (e: AgentError.RateLimit) {
            assertEquals(7L, e.retryAfterSeconds)
        }
    }

    @Test
    fun `400 命中余额关键词映射 Quota`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"账户余额不足"}}"""))
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (_: AgentError.Quota) {
        }
    }

    @Test
    fun `400 常规参数错误映射 Schema`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"model not found"}}"""))
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (e: AgentError.Schema) {
            assertTrue(e.message!!.contains("model not found"))
        }
    }

    @Test
    fun `5xx 映射 Provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (e: AgentError.Provider) {
            assertEquals(500, e.httpCode)
        }
    }

    @Test
    fun `未配置 key 抛 Auth`() = runBlocking {
        val m = OkHttpChatModel(
            ProviderPreset.custom("x", "X", server.url("/v1").toString(), listOf("m")),
            InMemoryApiKeyStore(),
        )
        try {
            m.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (_: AgentError.Auth) {
        }
    }
}
