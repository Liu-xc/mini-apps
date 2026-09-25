package com.leo.libs.agent

import com.leo.libs.agent.session.InMemorySessionStore
import com.leo.libs.agent.testing.FakeChatModel
import com.leo.libs.agent.testing.fakeFailing
import com.leo.libs.agent.testing.fakeText
import com.leo.libs.agent.testing.fakeToolCalls
import com.leo.libs.agent.tool.ToolRegistry
import com.leo.libs.agent.tool.ToolResult
import com.leo.libs.agent.tool.jsonSchema
import com.leo.libs.agent.tool.toolRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunnerTest {

    private val registry: ToolRegistry = toolRegistry {
        tool("search", "按条件搜索衣橱", jsonSchema { string("category", "品类") }) { args ->
            ToolResult.ok("命中:${args["category"]?.toString() ?: "?"}")
        }
        tool("boom", "总是抛异常的工具", jsonSchema {}) {
            throw IllegalStateException("炸了")
        }
    }

    private fun run(runner: AgentRunner, history: List<Message>): List<AgentEvent> =
        runBlocking { runner.run(history).toList() }

    private fun seq(events: List<AgentEvent>): List<String> =
        events.filter { it !is AgentEvent.TextDelta && it !is AgentEvent.ThinkingDelta }
            .map { it::class.simpleName ?: "?" }

    @Test
    fun `单工具回喂后续跑——事件序与 system 常驻`() {
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("c1", "search", """{"category":"上装"}""")),
                fakeText("给你推荐三件上装"),
            )
        )
        val runner = AgentRunner(fake, AgentConfig(systemPrompt = "你是衣橱顾问"), registry)
        val events = run(runner, listOf(Message.user("配一套通勤装")))

        assertEquals(
            listOf(
                "StepStarted", "ToolRequested", "ToolFinished", "StepFinished",
                "StepStarted", "StepFinished", "Completed",
            ),
            seq(events),
        )
        val done = events.last() as AgentEvent.Completed
        assertEquals("给你推荐三件上装", done.message.text)

        // 第一次请求：system 常驻打头
        val first = fake.requests[0]
        assertEquals(Role.System, first.messages.first().role)
        assertEquals("你是衣橱顾问", first.messages.first().text)

        // 第二次请求：system 仍常驻打头；assistant(tool_calls) + tool 结果按序回喂；无收尾提示
        val second = fake.requests[1]
        assertEquals(Role.System, second.messages.first().role)
        val toolMsg = second.messages.first { it.role == Role.Tool }
        assertEquals("c1", toolMsg.toolCallId)
        assertTrue(toolMsg.text.contains("命中"))
        assertTrue(second.messages.none { it.text.contains("不要调用任何工具") })
    }

    @Test
    fun `多个 tool_calls 逐个执行回喂`() {
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(
                    ToolCall("c1", "search", """{"category":"上装"}"""),
                    ToolCall("c2", "search", """{"category":"下装"}"""),
                ),
                fakeText("搭配好了"),
            )
        )
        val runner = AgentRunner(fake, tools = registry)
        val events = run(runner, listOf(Message.user("配一套")))

        assertEquals(2, events.filterIsInstance<AgentEvent.ToolRequested>().size)
        assertEquals(listOf("c1", "c2"), events.filterIsInstance<AgentEvent.ToolRequested>().map { it.call.id })
        val toolMsgs = fake.requests[1].messages.filter { it.role == Role.Tool }
        assertEquals(listOf("c1", "c2"), toolMsgs.map { it.toolCallId })
        assertEquals("搭配好了", (events.last() as AgentEvent.Completed).message.text)
    }

    @Test
    fun `未知工具名回喂错误不中断`() {
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("x", "nope", "{}")),
                fakeText("换个方式回答"),
            )
        )
        val events = run(AgentRunner(fake, tools = registry), listOf(Message.user("hi")))
        val toolMsg = fake.requests[1].messages.first { it.role == Role.Tool }
        assertTrue(toolMsg.text.contains("未知工具"))
        assertTrue(events.last() is AgentEvent.Completed)
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().first()
        assertTrue(finished.result is ToolResult.Error)
    }

    @Test
    fun `工具抛异常回喂错误不中断`() {
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("b", "boom", "{}")),
                fakeText("重试成功"),
            )
        )
        val events = run(AgentRunner(fake, tools = registry), listOf(Message.user("hi")))
        val toolMsg = fake.requests[1].messages.first { it.role == Role.Tool }
        assertTrue(toolMsg.text.contains("炸了"))
        assertTrue(events.last() is AgentEvent.Completed)
    }

    @Test
    fun `maxSteps 熔断——工具回喂后去 tools 追问收尾`() {
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("c1", "search", """{"category":"鞋"}""")),
                fakeText("最终答案"),
            )
        )
        val runner = AgentRunner(fake, AgentConfig(maxSteps = 1), registry)
        val events = run(runner, listOf(Message.user("配鞋")))

        assertEquals(2, fake.requests.size)
        assertTrue(fake.requests[1].tools.isEmpty()) // 收尾轮不带 tools
        val closing = fake.requests[1].messages.last()
        assertEquals(Role.User, closing.role)
        assertTrue(closing.text.contains("不要调用任何工具"))
        // 收尾前工具结果已回喂（模型拿得到数据）
        assertTrue(fake.requests[1].messages.any { it.role == Role.Tool && it.toolCallId == "c1" })
        assertEquals(2, events.filterIsInstance<AgentEvent.StepStarted>().size)
        assertEquals("最终答案", (events.last() as AgentEvent.Completed).message.text)
    }

    @Test
    fun `收尾轮仍回 tool_calls 则就地截断`() {
        // maxSteps=1，收尾轮（无 tools）模型仍吐 tool_calls → 截断为 Completed
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("c1", "search", "{}")),
                fakeToolCalls(ToolCall("c2", "search", "{}")),
            )
        )
        val runner = AgentRunner(fake, AgentConfig(maxSteps = 1), registry)
        val events = run(runner, listOf(Message.user("配鞋")))
        assertTrue(events.last() is AgentEvent.Completed)
        assertTrue(events.none { it is AgentEvent.Failed })
    }

    @Test
    fun `传输错误发 Failed 且已产出历史不回滚`() = runBlocking {
        val store = InMemorySessionStore()
        store.append("s1", Message.user("hi")) // 调用方契约：用户输入发送时即入会话
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("c1", "search", "{}")),
                fakeFailing(AgentError.Auth("key 无效")),
            )
        )
        val runner = AgentRunner(fake, tools = registry, session = store, sessionId = "s1")
        val events = runner.run(store.messages("s1")).toList()

        assertTrue(events.last() is AgentEvent.Failed)
        assertTrue((events.last() as AgentEvent.Failed).error is AgentError.Auth)
        // 用户消息 + 第一步产出的 assistant + tool 结果都在会话里（失败不回滚）
        val kept = store.messages("s1")
        assertEquals(3, kept.size)
        assertEquals(Role.User, kept[0].role)
        assertEquals(Role.Assistant, kept[1].role)
        assertEquals(Role.Tool, kept[2].role)
    }

    @Test
    fun `失败后凭会话续跑`() = runBlocking {
        val store = InMemorySessionStore()
        store.append("s1", Message.user("hi")) // 调用方契约：用户输入发送时即入会话
        val failing = FakeChatModel(
            listOf(
                fakeToolCalls(ToolCall("c1", "search", "{}")),
                fakeFailing(AgentError.Network(RuntimeException("断网"))),
            )
        )
        AgentRunner(failing, tools = registry, session = store, sessionId = "s1")
            .run(store.messages("s1")).toList()

        val resumed = FakeChatModel(listOf(fakeText("恢复后的回答")))
        val events = AgentRunner(resumed, tools = registry, session = store, sessionId = "s1")
            .run(store.messages("s1")).toList()
        assertEquals("恢复后的回答", (events.last() as AgentEvent.Completed).message.text)
        // 续跑请求带上之前的历史（含用户问题与 tool 结果）
        assertTrue(resumed.requests[0].messages.any { it.role == Role.Tool })
        assertTrue(resumed.requests[0].messages.any { it.role == Role.User && it.text == "hi" })
    }

    @Test
    fun `取消即停不产生终态`() = runBlocking {
        val hangingModel = object : ChatModel {
            override val preset: ProviderPreset =
                ProviderPreset.custom("hang", "Hang", "http://localhost", listOf("m"))
            override suspend fun complete(request: ChatRequest) = error("不用")
            override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
                emit(ChatEvent.TextDelta("半截"))
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val firstDelta = CompletableDeferred<Unit>()
        val received = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRunner(hangingModel).run(listOf(Message.user("hi"))).collect {
                received += it
                if (it is AgentEvent.TextDelta) firstDelta.complete(Unit)
            }
        }
        firstDelta.await()
        job.cancel()
        job.join()
        assertTrue(received.none { it is AgentEvent.Completed || it is AgentEvent.Failed })
        assertEquals(1, received.filterIsInstance<AgentEvent.StepStarted>().size)
    }

    @Test
    fun `usage 跨步累计且 TextDelta 保序`() {
        val scripted = object : ChatModel {
            override val preset: ProviderPreset =
                ProviderPreset.custom("s", "S", "http://localhost", listOf("m"))
            val turns = ArrayDeque(
                listOf(
                    Triple("tool_calls", Message.assistantToolCalls(listOf(ToolCall("c1", "search", "{}"))), Usage(3, 1, 4)),
                    Triple("stop", Message.assistant("答"), Usage(10, 5, 15)),
                )
            )

            override suspend fun complete(request: ChatRequest) = error("不用")

            override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
                val (finish, msg, usage) = turns.removeFirst()
                emit(ChatEvent.TextDelta("片段"))
                emit(ChatEvent.Completed(ChatCompletion(msg, finish, usage)))
            }
        }
        val events = run(AgentRunner(scripted, tools = registry), listOf(Message.user("hi")))
        assertEquals(listOf("片段", "片段"), events.filterIsInstance<AgentEvent.TextDelta>().map { it.text })
        val done = events.last() as AgentEvent.Completed
        assertEquals(Usage(13, 6, 19), done.usage)
        val started = events.filterIsInstance<AgentEvent.StepStarted>().size
        val finished = events.filterIsInstance<AgentEvent.StepFinished>().size
        assertEquals(started, finished)
        assertEquals(2, started)
    }

    @Test
    fun `历史里的 system 被丢弃常驻位只留给 config`() {
        val fake = FakeChatModel(listOf(fakeText("ok")))
        val runner = AgentRunner(fake, AgentConfig(systemPrompt = "官方人设"), registry)
        run(runner, listOf(Message.system("冒牌人设"), Message.user("hi")))
        val msgs = fake.requests[0].messages
        assertEquals(1, msgs.count { it.role == Role.System })
        assertEquals("官方人设", msgs.first { it.role == Role.System }.text)
    }
}
