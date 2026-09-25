package com.leo.libs.agent

import com.leo.libs.agent.tool.ToolResult
import com.leo.libs.agent.tool.jsonSchema
import com.leo.libs.agent.tool.toolRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDslTest {

    @Test
    fun `jsonSchema 生成扁平 object 结构`() {
        val schema = jsonSchema {
            string("category", "品类")
            string("season", "季节", required = false, enum = listOf("春", "夏"))
            number("limit", "返回条数")
            boolean("onlyFav", "只看收藏", required = false)
        }.jsonObject

        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        val props = schema["properties"]!!.jsonObject
        assertEquals(4, props.size)
        assertEquals("string", props["category"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("品类", props["category"]!!.jsonObject["description"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("春", "夏"),
            props["season"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("number", props["limit"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("boolean", props["onlyFav"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("category", "limit"), required)
    }

    @Test
    fun `jsonSchema 参数重名抛错`() {
        try {
            jsonSchema { string("a", "x"); string("a", "y") }
            throw AssertionError("应当抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun `注册表重名工具抛错`() {
        try {
            toolRegistry {
                tool("t", "d", jsonSchema {}) { ToolResult.ok("1") }
                tool("t", "d", jsonSchema {}) { ToolResult.ok("2") }
            }
            throw AssertionError("应当抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun `execute 正常分发并拿到参数`() = runBlocking {
        var seen: String? = null
        val registry = toolRegistry {
            tool("hi", "打个招呼", jsonSchema { string("name", "名字") }) { args ->
                seen = args["name"]?.jsonPrimitive?.content
                ToolResult.ok("你好,$seen")
            }
        }
        assertEquals(listOf("hi"), registry.specs.map { it.name })
        val result = registry.execute("hi", """{"name":"Leo"}""")
        assertEquals("你好,Leo", result.asText())
        assertEquals("Leo", seen)
    }

    @Test
    fun `execute 未知工具返回错误`() = runBlocking {
        val registry = toolRegistry { tool("hi", "d", jsonSchema {}) { ToolResult.ok("1") } }
        val result = registry.execute("nope", "{}")
        assertTrue(result is ToolResult.Error)
        assertTrue(result.asText().contains("未知工具"))
    }

    @Test
    fun `execute 非法 JSON 与非对象参数返回错误`() = runBlocking {
        val registry = toolRegistry { tool("hi", "d", jsonSchema {}) { ToolResult.ok("1") } }
        val badJson = registry.execute("hi", "{oops")
        assertTrue(badJson is ToolResult.Error)
        assertTrue(badJson.asText().contains("JSON"))

        val notObject = registry.execute("hi", "[1,2]")
        assertTrue(notObject is ToolResult.Error)
        assertTrue(notObject.asText().contains("对象"))

        // 空参数 = "{}"
        assertEquals("1", registry.execute("hi", "").asText())
    }

    @Test
    fun `execute 处理器异常转错误回喂`() = runBlocking {
        val registry = toolRegistry {
            tool("boom", "d", jsonSchema {}) { throw RuntimeException("内部炸了") }
        }
        val result = registry.execute("boom", "{}")
        assertTrue(result is ToolResult.Error)
        assertTrue(result.asText().contains("内部炸了"))
    }

    @Test
    fun `空注册表 specs 为空`() {
        val registry = toolRegistry {}
        assertTrue(registry.isEmpty)
        assertTrue(registry.specs.isEmpty())
        assertFalse(registry.specs.any { it.name.isNotEmpty() })
    }

    @Test
    fun `spec 携带 schema 与描述可透传厂商`() {
        val registry = toolRegistry {
            tool("search", "搜索衣橱", jsonSchema { string("q", "关键词") }) { ToolResult.ok("ok") }
        }
        val spec = registry.specs.first()
        assertEquals("search", spec.name)
        assertEquals("搜索衣橱", spec.description)
        val props = spec.parametersSchema.jsonObject["properties"]!!.jsonObject
        assertEquals("string", props["q"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("关键词", props["q"]!!.jsonObject["description"]!!.jsonPrimitive.content)
    }
}
