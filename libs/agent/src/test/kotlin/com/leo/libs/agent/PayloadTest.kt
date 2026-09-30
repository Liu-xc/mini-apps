package com.leo.libs.agent

import com.leo.libs.agent.internal.WireMessage
import com.leo.libs.agent.internal.toWire
import com.leo.libs.agent.internal.wireJson
import com.leo.libs.agent.session.FileSessionStore
import com.leo.libs.agent.testing.FakeChatModel
import com.leo.libs.agent.testing.fakeText
import com.leo.libs.agent.testing.fakeToolCalls
import com.leo.libs.agent.tool.ToolResult
import com.leo.libs.agent.tool.jsonSchema
import com.leo.libs.agent.tool.toolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** it-075：工具结果结构化载荷——透传、落盘、旧文件兼容、wire 隔离 */
class PayloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val payload = buildJsonObject {
        put("kind", "items")
        put("total", 2)
        put("ids", buildJsonArray {
            add(JsonPrimitive("id-a")); add(JsonPrimitive("id-b"))
        })
    }

    @Test
    fun `ok 携带 payload 且 asText 不变`() {
        val ok = ToolResult.ok("命中 2 件", payload)
        assertEquals("命中 2 件", ok.asText())
        assertEquals(payload, (ok as ToolResult.Ok).payload)
        // 无 payload 的旧用法不受影响
        assertNull((ToolResult.ok("纯文本") as ToolResult.Ok).payload)
    }

    @Test
    fun `runner 完整回合——payload 落盘可读回，第二轮请求体内存消息同带`() {
        val dir = tmp.newFolder()
        val store = FileSessionStore(dir)
        val registry = toolRegistry {
            tool("search", "查衣橱", jsonSchema {}) { ToolResult.ok("命中 2 件", payload) }
        }
        val fake = FakeChatModel(
            listOf(
                fakeToolCalls(com.leo.libs.agent.ToolCall("c1", "search", "{}")),
                fakeText("已找到 2 件"),
            )
        )
        val runner = AgentRunner(fake, AgentConfig(), registry, store, "s1")
        runBlocking { runner.run(listOf(Message.user("有哪些外套"))).toList() }

        val persisted = runBlocking { store.messages("s1") }
        val toolMsg = persisted.first { it.role == Role.Tool }
        assertEquals("c1", toolMsg.toolCallId)
        assertEquals(payload, toolMsg.payload)

        // 第二轮请求里内存 tool 消息同样携带 payload（落盘副本另盖时间戳，两者内容一致）
        val second = fake.requests[1]
        val inFlight = second.messages.first { it.role == Role.Tool }
        assertEquals(payload, inFlight.payload)
        assertEquals(toolMsg.text, inFlight.text)
        assertEquals(toolMsg.toolCallId, inFlight.toolCallId)
    }

    @Test
    fun `旧会话文件无 payload 字段——读回 null 不崩`() {
        val dir = tmp.newFolder()
        File(dir, "legacy.json").writeText(
            """
            [
              {"role":"user","parts":[{"type":"text","text":"我有哪些外套"}]},
              {"role":"assistant","toolCalls":[{"id":"c1","name":"search_items","argumentsJson":"{}"}]},
              {"role":"tool","parts":[{"type":"text","text":"· 风衣"}],"toolCallId":"c1","createdAt":100}
            ]
            """.trimIndent(),
        )
        val toolMsg = runBlocking { FileSessionStore(dir).messages("legacy") }
            .first { it.role == Role.Tool }
        assertEquals("· 风衣", toolMsg.text)
        assertNull(toolMsg.payload)
    }

    @Test
    fun `payload null 落盘省略字段，非 null 完整往返`() {
        val plain = Message.toolResult("c1", "无载荷")
        val plainJson = Json.encodeToString(Message.serializer(), plain)
        assertFalse("null payload 应省略字段", "payload" in plainJson)

        val withPayload = Message.toolResult("c1", "有载荷", payload)
        val json = Json.encodeToString(Message.serializer(), withPayload)
        assertTrue(json.contains("\"payload\""))
        val back = Json.decodeFromString(Message.serializer(), json)
        assertEquals(payload, back.payload)
    }

    @Test
    fun `wire 不携带 payload——厂商请求体不受扩展影响`() {
        val msg = Message.toolResult("c1", "命中 2 件", payload)
        val wire: WireMessage = msg.toWire()
        val body = wireJson.encodeToString(WireMessage.serializer(), wire)
        assertFalse("wire 请求体不得出现 payload", "payload" in body)
        assertEquals("命中 2 件", wire.content!!.jsonPrimitive.content)
        // wireJson explicitNulls=false + encodeDefaults=false：其余字段形态不变
        val parsed = wireJson.parseToJsonElement(body).jsonObject
        assertEquals("tool", parsed["role"]!!.jsonPrimitive.content)
        assertEquals("c1", parsed["tool_call_id"]!!.jsonPrimitive.content)
    }
}
