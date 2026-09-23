package com.leo.libs.agent

import com.leo.libs.agent.internal.SseDecoder
import com.leo.libs.agent.internal.StreamAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SseDecoderTest {

    @Test fun `单行 data 空行派发`() {
        val d = SseDecoder()
        assertNull(d.onLine("event: add"))
        assertNull(d.onLine("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", d.onLine(""))
    }

    @Test fun `多行 data 按 SSE 规范拼接`() {
        val d = SseDecoder()
        d.onLine("data: {\"a\":")
        d.onLine("data: 1}")
        assertEquals("{\"a\":1}", d.onLine(""))
    }

    @Test fun `注释与 CRLF 忽略`() {
        val d = SseDecoder()
        assertNull(d.onLine(": keep-alive"))
        assertNull(d.onLine("data: x\r"))
        assertEquals("x", d.onLine("\r"))
    }

    @Test fun `无空行收尾 flush 兜底`() {
        val d = SseDecoder()
        assertNull(d.onLine("data: [DONE]"))
        assertEquals("[DONE]", d.flush())
    }

    @Test fun `连续事件互不串扰`() {
        val d = SseDecoder()
        d.onLine("data: a")
        assertEquals("a", d.onLine(""))
        d.onLine("data: b")
        assertEquals("b", d.onLine(""))
    }
}

class StreamAssemblerTest {

    private val quirks = Quirks(reasoningField = true)

    @Test fun `文本增量与终态装配`() {
        val a = StreamAssembler(quirks)
        val events = a.feed("""{"choices":[{"delta":{"content":"你"}}]}""") +
            a.feed("""{"choices":[{"delta":{"content":"好"}}]}""")
        assertEquals(
            listOf<ChatEvent>(ChatEvent.TextDelta("你"), ChatEvent.TextDelta("好")),
            events,
        )
        val done = a.completed() as ChatEvent.Completed
        assertEquals("你好", done.completion.message.text)
        assertTrue(done.completion.message.toolCalls.isEmpty())
        assertEquals(Usage(), done.completion.usage)
    }

    @Test fun `tool_calls 分片聚合`() {
        val a = StreamAssembler(quirks)
        a.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_menus","arguments":""}}]}}]}""")
        a.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"canteen"}}]}}]}""")
        a.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\":\"第一食堂\"}"}}]}}]}""")
        a.feed("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        val done = a.completed() as ChatEvent.Completed
        val calls = done.completion.message.toolCalls
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("get_menus", calls[0].name)
        assertEquals("""{"canteen":"第一食堂"}""", calls[0].argumentsJson)
        assertEquals("tool_calls", done.completion.finishReason)
    }

    @Test fun `reasoning_content 进事件不进消息`() {
        val a = StreamAssembler(quirks)
        val ev = a.feed("""{"choices":[{"delta":{"reasoning_content":"思考中"}}]}""")
        assertEquals(listOf<ChatEvent>(ChatEvent.ThinkingDelta("思考中")), ev)
        a.feed("""{"choices":[{"delta":{"content":"答"}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""")
        val done = a.completed() as ChatEvent.Completed
        assertEquals(Usage(10, 5, 15), done.completion.usage)
        assertEquals("答", done.completion.message.text)
    }

    @Test fun `reasoningField 关闭时忽略推理字段`() {
        val a = StreamAssembler(Quirks(reasoningField = false))
        val ev = a.feed("""{"choices":[{"delta":{"reasoning_content":"嗯","content":"答"}}]}""")
        assertEquals(listOf<ChatEvent>(ChatEvent.TextDelta("答")), ev)
    }

    @Test fun `未知字段与损坏载荷容错`() {
        val a = StreamAssembler(quirks)
        assertTrue(
            a.feed("""{"model":"x","system_fingerprint":"fp","choices":[{"index":0,"delta":{"content":"ok"},"logprobs":null}]}""")
                .isNotEmpty(),
        )
        assertTrue(a.feed("not-json{").isEmpty())
        assertTrue(a.feed("""{"unknown_top":1}""").isEmpty())
    }

    @Test fun `空流抛 Provider`() {
        val a = StreamAssembler(quirks)
        try {
            a.completed()
            fail("应抛 AgentError.Provider")
        } catch (e: AgentError.Provider) {
            assertEquals(-1, e.httpCode)
        }
    }
}
