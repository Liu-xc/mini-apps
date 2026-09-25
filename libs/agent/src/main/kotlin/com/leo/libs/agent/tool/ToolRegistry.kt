package com.leo.libs.agent.tool

import com.leo.libs.agent.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 工具执行结果。[Ok]/[Error] 的差别是回喂给模型的措辞（it-002 §1）：
 * 模型看到后自行纠正，loop 不因此中断。
 */
sealed class ToolResult {
    data class Ok(val text: String) : ToolResult()
    data class Error(val text: String) : ToolResult()

    fun asText(): String = when (this) {
        is Ok -> text
        is Error -> text
    }

    companion object {
        fun ok(text: String) = Ok(text)
        fun error(text: String) = Error(text)
    }
}

/**
 * 工具注册表：spec 列表（喂给厂商）+ 执行分发。
 * 参数解析失败 / 未知工具名 / 处理器异常 → [ToolResult.error] 回喂，不抛（US-A3 自纠）。
 */
class ToolRegistry internal constructor(private val entries: List<Entry>) {

    internal class Entry(val spec: ToolSpec, val handler: suspend (JsonObject) -> ToolResult)

    /** 透传给 ChatRequest.tools 的厂商侧定义 */
    val specs: List<ToolSpec> get() = entries.map { it.spec }

    val isEmpty: Boolean get() = entries.isEmpty()

    suspend fun execute(name: String, argumentsJson: String): ToolResult {
        val entry = entries.find { it.spec.name == name }
            ?: return ToolResult.error("未知工具：$name（可用工具：${entries.joinToString { it.spec.name }.ifBlank { "无" }}）")
        val args = runCatching {
            Json.parseToJsonElement(argumentsJson.ifBlank { "{}" })
        }.getOrElse { return ToolResult.error("参数不是合法 JSON：${it.message}") }
        val obj = args as? JsonObject ?: return ToolResult.error("参数必须是 JSON 对象，实际为：$args")
        return runCatching { entry.handler(obj) }
            .getOrElse { e -> ToolResult.error(e.message?.ifBlank { null } ?: e.toString()) }
    }
}

class ToolRegistryBuilder internal constructor() {
    private val entries = mutableListOf<ToolRegistry.Entry>()

    fun tool(
        name: String,
        description: String,
        parameters: JsonElement,
        handler: suspend (JsonObject) -> ToolResult,
    ) {
        require(name.isNotBlank()) { "工具名不能为空" }
        require(entries.none { it.spec.name == name }) { "工具名重复：$name" }
        entries += ToolRegistry.Entry(ToolSpec(name, description, parameters), handler)
    }

    internal fun build() = ToolRegistry(entries.toList())
}

/** `toolRegistry { tool("search", "按条件搜索", jsonSchema { ... }) { args -> ... } }` */
fun toolRegistry(block: ToolRegistryBuilder.() -> Unit): ToolRegistry =
    ToolRegistryBuilder().apply(block).build()
