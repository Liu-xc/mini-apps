package com.leo.libs.agent

import com.leo.libs.agent.testing.FakeChatModel
import com.leo.libs.agent.testing.fakeFailing
import com.leo.libs.agent.testing.fakeText
import com.leo.libs.agent.testing.fakeToolCalls
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FakeChatModelTest {

    @Test
    fun `文本回合打字机与请求记录`() = runBlocking {
        val model = FakeChatModel(listOf(fakeText("abcd")))
        val events = model.stream(ChatRequest(messages = listOf(Message.user("hi")))).toList()
        assertEquals("abcd", events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.text })
        val completed = events.last() as ChatEvent.Completed
        assertEquals("abcd", completed.completion.message.text)
        assertEquals(1, model.requests.size)
        assertEquals(listOf(Message.user("hi")), model.requests.single().messages)
    }

    @Test
    fun `工具回合`() = runBlocking {
        val model = FakeChatModel(listOf(fakeToolCalls(ToolCall("c1", "t", "{}"))))
        val events = model.stream(ChatRequest(messages = listOf(Message.user("q")))).toList()
        val completed = events.last() as ChatEvent.Completed
        assertEquals("tool_calls", completed.completion.finishReason)
        assertEquals("t", completed.completion.message.toolCalls.single().name)
    }

    @Test
    fun `错误回合抛出`() = runBlocking {
        val model = FakeChatModel(listOf(fakeFailing(AgentError.RateLimit(5))))
        try {
            model.stream(ChatRequest(messages = listOf(Message.user("q")))).toList()
            fail()
        } catch (e: AgentError.RateLimit) {
            assertEquals(5L, e.retryAfterSeconds)
        }
    }

    @Test
    fun `脚本耗尽抛 Provider`() = runBlocking {
        val model = FakeChatModel(emptyList())
        try {
            model.complete(ChatRequest(messages = listOf(Message.user("q"))))
            fail()
        } catch (_: AgentError.Provider) {
        }
    }
}
