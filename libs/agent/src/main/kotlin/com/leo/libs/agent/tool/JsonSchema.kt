package com.leo.libs.agent.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 扁平 JSON Schema 构造 DSL（it-002）：只支持 object + 扁平 properties——
 * 真实工具参数都是扁平的；需要嵌套时再扩（只增不改名）。
 */
class JsonSchemaBuilder internal constructor() {

    private class Prop(val type: String, val description: String, val enumValues: List<String>?)

    private val props = LinkedHashMap<String, Prop>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = true, enum: List<String>? = null) =
        add(name, "string", description, required, enum)

    fun number(name: String, description: String, required: Boolean = true) =
        add(name, "number", description, required, null)

    fun boolean(name: String, description: String, required: Boolean = true) =
        add(name, "boolean", description, required, null)

    private fun add(name: String, type: String, description: String, required: Boolean, enum: List<String>?) {
        require(name.isNotBlank()) { "参数名不能为空" }
        require(name !in props) { "参数名重复：$name" }
        props[name] = Prop(type, description, enum)
        if (required) this.required += name
    }

    internal fun build(): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(props.map { (name, p) ->
            name to buildJsonObject {
                put("type", JsonPrimitive(p.type))
                put("description", JsonPrimitive(p.description))
                p.enumValues?.let { put("enum", JsonArray(it.map(::JsonPrimitive))) }
            }
        }.toMap()))
        if (required.isNotEmpty()) {
            put("required", JsonArray(required.map(::JsonPrimitive)))
        }
    }
}

/** `jsonSchema { string("category", "上装/下装/鞋") }` → OpenAI function parameters */
fun jsonSchema(block: JsonSchemaBuilder.() -> Unit): JsonElement =
    JsonSchemaBuilder().apply(block).build()
