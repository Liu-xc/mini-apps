package com.leo.libs.agent

import com.leo.libs.agent.session.DefaultContextPolicy
import com.leo.libs.agent.session.FileSessionStore
import com.leo.libs.agent.usage.FileUsageLedger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionAndPolicyTest {

    private fun tempDir(): File =
        Files.createTempDirectory("agent-test").toFile().also { it.deleteOnExit() }

    // ---------- FileSessionStore ----------

    @Test
    fun `文件会话序列化往返保序保真`() = runBlocking {
        val store = FileSessionStore(tempDir())
        val messages = listOf(
            Message.user("配一套通勤装"),
            Message.assistantToolCalls(listOf(ToolCall("c1", "search", """{"category":"上装"}"""))),
            Message.toolResult("c1", "命中3件"),
            Message.assistant("推荐A+B"),
        )
        messages.forEach { store.append("s1", it) }
        assertEquals(messages, store.messages("s1"))
        // 不存在的会话 → 空
        assertEquals(emptyList<Message>(), store.messages("missing"))
        store.clear("s1")
        assertEquals(emptyList<Message>(), store.messages("s1"))
    }

    @Test
    fun `sessionId 清洗不可路径穿越`() = runBlocking {
        val dir = tempDir()
        val store = FileSessionStore(dir)
        store.append("../evil/x", Message.user("hi"))
        val files = dir.listFiles()!!
        assertEquals(1, files.size)
        assertTrue(files[0].parentFile == dir) // 落在根目录，无子目录
        assertEquals(1, store.messages("../evil/x").size)
    }

    @Test
    fun `损坏文件按空会话处理不崩`() {
        val dir = tempDir()
        File(dir, "bad.json").writeText("{这不是json")
        val store = FileSessionStore(dir)
        assertEquals(emptyList<Message>(), runBlocking { store.messages("bad") })
    }

    // ---------- DefaultContextPolicy ----------

    private fun turn(q: String, a: String) = listOf(Message.user(q), Message.assistant(a))

    @Test
    fun `丢弃游离头部与 system`() {
        val policy = DefaultContextPolicy(maxTurns = 12)
        val history = listOf(
            Message.system("旧system"),
            Message.assistant("游离回答"),
            Message.toolResult("x", "游离工具"),
        ) + turn("q1", "a1")
        val result = policy.trim(history, null)
        assertEquals(listOf("q1", "a1"), result.map { it.text })
    }

    @Test
    fun `近 N 轮窗口保留最后 12 轮`() {
        val policy = DefaultContextPolicy(maxTurns = 12, maxEstimatedTokens = Int.MAX_VALUE)
        val history = (1..14).flatMap { turn("q$it", "a$it") }
        val result = policy.trim(history, null)
        assertEquals(24, result.size)
        assertEquals("q3", result.first().text) // 14 轮裁掉最早 2 轮（q1/q2）
        assertEquals("a14", result.last().text)
    }

    @Test
    fun `token 估算超窗从最老轮次裁`() {
        val policy = DefaultContextPolicy(maxTurns = 99, maxEstimatedTokens = 10)
        val big = "字".repeat(400) // 单条估 100 token
        val history = turn("旧问:$big", "旧答:$big") + turn("新问", "新答")
        val result = policy.trim(history, null)
        assertEquals(listOf("新问", "新答"), result.map { it.text })
    }

    @Test
    fun `单轮再大也保留当前问题`() {
        val policy = DefaultContextPolicy(maxTurns = 99, maxEstimatedTokens = 10)
        val big = "字".repeat(4000)
        val result = policy.trim(listOf(Message.user("大问:$big"), Message.assistant("大答:$big")), null)
        assertEquals(2, result.size)
    }

    @Test
    fun `裁剪不产生孤儿 tool 结果`() {
        val policy = DefaultContextPolicy(maxTurns = 2, maxEstimatedTokens = Int.MAX_VALUE)
        val history =
            listOf(Message.user("老问题")) +
                listOf(Message.assistantToolCalls(listOf(ToolCall("cOld", "search", "{}")))) +
                listOf(Message.toolResult("cOld", "老结果")) +
                turn("q2", "a2") +
                turn("q3", "a3")
        val result = policy.trim(history, null)
        // 只留最后 2 轮（q2/q3），连带老 tool 三件套整体被裁
        assertEquals(listOf("q2", "a2", "q3", "a3"), result.map { it.text })
        assertTrue(result.none { it.role == Role.Tool })
    }

    @Test
    fun `窗口内的 tool 结果保持配对完整`() {
        val policy = DefaultContextPolicy(maxTurns = 3, maxEstimatedTokens = Int.MAX_VALUE)
        val history =
            turn("q1", "a1") +
                listOf(Message.user("q2")) +
                listOf(Message.assistantToolCalls(listOf(ToolCall("c2", "search", "{}")))) +
                listOf(Message.toolResult("c2", "r2")) +
                turn("q3", "a3")
        val result = policy.trim(history, null)
        val toolIds = result.filter { it.role == Role.Tool }.map { it.toolCallId }
        val assistantCallIds = result.flatMap { it.toolCalls }.map { it.id }
        assertEquals(toolIds, assistantCallIds) // 每个 tool 结果都有配对的 assistant tool_calls
    }

    // ---------- FileUsageLedger ----------

    @Test
    fun `用量按厂商×模型累计且文件往返一致`() = runBlocking {
        val file = File(tempDir(), "usage.json")
        val ledger = FileUsageLedger(file)
        ledger.record("glm", "glm-4-flash", Usage(10, 5, 15))
        ledger.record("glm", "glm-4-flash", Usage(3, 2, 5))
        ledger.record("mimo-tp", "mimo-v2.6-flash", Usage(7, 1, 8))

        val totals = ledger.totals()
        assertEquals(Usage(13, 7, 20), totals["glm" to "glm-4-flash"])
        assertEquals(Usage(7, 1, 8), totals["mimo-tp" to "mimo-v2.6-flash"])

        // 新实例读同一文件
        val reopened = FileUsageLedger(file)
        assertEquals(totals, reopened.totals())
        assertEquals(Usage(7, 1, 8), reopened.totals()["mimo-tp" to "mimo-v2.6-flash"])
    }
}
