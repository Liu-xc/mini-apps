package com.leo.wardrobe.ui.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-075：工具结果 payload → 结构化卡片的解析与标签拼装 */
class ToolResultCardTest {

    private fun itemsPayload(
        total: Int = 3,
        ids: List<String> = listOf("a", "b", "c"),
        category: String = "外套",
        keyword: String = "",
    ) = buildJsonObject {
        put("kind", "items")
        put("total", total)
        put("category", category)
        put("keyword", keyword)
        put("ids", buildJsonArray { ids.forEach { add(it) } })
    }

    @Test
    fun `items payload 解析保留条件与全量 id`() {
        val card = parseToolResultCard(itemsPayload(category = "外套", keyword = "蓝")) as ToolResultCard.Items
        assertEquals(3, card.total)
        assertEquals(listOf("a", "b", "c"), card.ids)
        assertEquals("外套", card.category)
        assertEquals("蓝", card.keyword)
    }

    @Test
    fun `outfits payload 解析`() {
        val payload = buildJsonObject {
            put("kind", "outfits")
            put("total", 2)
            put("keyword", "通勤")
            put("ids", buildJsonArray { add("o1"); add("o2") })
        }
        val card = parseToolResultCard(payload) as ToolResultCard.Outfits
        assertEquals(2, card.total)
        assertEquals("通勤", card.keyword)
    }

    @Test
    fun `不认识的结构返回 null 不抛`() {
        assertNull(parseToolResultCard(null))
        assertNull(parseToolResultCard(Json.parseToJsonElement("\"文本\"")))
        // 其他工具的 payload / 缺 ids / 空 ids
        assertNull(parseToolResultCard(buildJsonObject { put("kind", "wear_stats") }))
        assertNull(parseToolResultCard(buildJsonObject { put("kind", "items"); put("total", 1) }))
        assertNull(parseToolResultCard(itemsPayload(ids = emptyList())))
        // total 缺失回退 ids.size
        val noTotal = buildJsonObject {
            put("kind", "outfits")
            put("ids", buildJsonArray { add("o1"); add("o2") })
        }
        assertEquals(2, (parseToolResultCard(noTotal) as ToolResultCard.Outfits).total)
    }

    @Test
    fun `查询条件摘要拼接`() {
        fun items(category: String, keyword: String) =
            ToolResultCard.Items(5, List(5) { "i$it" }, category, keyword)

        assertEquals("外套", items("外套", "").queryLabel())
        assertEquals("“蓝”", items("", "蓝").queryLabel())
        assertEquals("外套 · “蓝”", items("外套", "蓝").queryLabel())
        assertEquals("全部单品", items("", "").queryLabel())
        assertEquals(
            "找到 5 件 · 外套",
            items("外套", "").headerLabel(),
        )
        assertEquals(
            "找到 2 套穿搭 · “通勤”",
            ToolResultCard.Outfits(2, listOf("o1", "o2"), "通勤").headerLabel(),
        )
        assertEquals(
            "找到 2 套穿搭 · 穿搭",
            ToolResultCard.Outfits(2, listOf("o1", "o2"), "").headerLabel(),
        )
    }

    @Test
    fun `ids 为原始 JSON 字符串数组时逐个取出`() {
        val payload = Json.parseToJsonElement(
            """{"kind":"items","total":1,"category":"","keyword":"","ids":["x-1"]}""",
        )
        val card = parseToolResultCard(payload) as ToolResultCard.Items
        assertEquals(listOf("x-1"), card.ids)
    }

    /** 工具产出 ↔ 解析 契约：search_items 的 payload 必能解析，ids 全量（>20 不截断，供抽屉） */
    @Test
    fun `search_items payload 契约——ids 全量且可解析`() = kotlinx.coroutines.runBlocking {
        val pid = "p1"
        val many = (1..25).map { i ->
            com.leo.wardrobe.domain.model.Item(
                id = "item-$i", personId = pid,
                category = com.leo.wardrobe.domain.model.WardrobeCategory.OUTERWEAR,
                name = "外套$i", imageFile = "f$i.png",
            )
        }
        val registry = wardrobeTools(
            data = { com.leo.wardrobe.domain.model.WardrobeData(persons = listOf(com.leo.wardrobe.domain.model.Person(pid, "Leo")), items = many) },
            personId = { pid },
        )
        val result = registry.execute("search_items", """{"category":"外套"}""") as com.leo.libs.agent.tool.ToolResult.Ok
        val card = parseToolResultCard(result.payload) as ToolResultCard.Items
        assertEquals(25, card.total)
        assertEquals(25, card.ids.size) // 文本 take(20)，payload ids 全量
        assertEquals("外套", card.category)
        assertEquals("找到 25 件 · 外套", card.headerLabel())
    }

    // ---------- it-077：AI 试衣成图卡 ----------

    private fun outfitImagePayload(
        outfitId: String = "o1",
        imageFile: String = "gen-1.webp",
        model: String = "dashscope/qwen-image-edit-plus",
    ) = buildJsonObject {
        put("kind", "outfit_image")
        put("outfitId", outfitId)
        put("imageFile", imageFile)
        put("model", model)
    }

    @Test
    fun `outfit_image payload 解析为成图卡`() {
        val card = parseToolResultCard(outfitImagePayload()) as ToolResultCard.GeneratedImage
        assertEquals("o1", card.outfitId)
        assertEquals("gen-1.webp", card.imageFile)
        assertEquals("dashscope/qwen-image-edit-plus", card.model)
        assertEquals("AI 生成 · 穿搭效果图", card.headerLabel())
    }

    @Test
    fun `outfit_image 缺关键字段返回 null 不抛`() {
        assertNull(parseToolResultCard(buildJsonObject { put("kind", "outfit_image"); put("outfitId", "o1") }))
        assertNull(parseToolResultCard(buildJsonObject { put("kind", "outfit_image"); put("imageFile", "f") }))
        assertNull(parseToolResultCard(buildJsonObject { put("kind", "outfit_image") }))
    }

    @Test
    fun `generate 工具按需注册并可执行`() = kotlinx.coroutines.runBlocking {
        val pid = "p1"
        var captured: Triple<String?, List<String>, String?>? = null
        val registry = wardrobeTools(
            data = { com.leo.wardrobe.domain.model.WardrobeData(persons = listOf(com.leo.wardrobe.domain.model.Person(pid, "Leo"))) },
            personId = { pid },
            imageGen = { requestedOutfitId, itemIds, sceneHint ->
                captured = Triple(requestedOutfitId, itemIds, sceneHint)
                com.leo.libs.agent.tool.ToolResult.ok("done", outfitImagePayload(outfitId = requestedOutfitId ?: "o1"))
            },
        )
        // 未注入 imageGen 的旧调用方没有该工具（上面 search_items 契约用例即两参形态）
        val result = registry.execute(
            "generate_outfit_image",
            """{"outfitId":"o9","sceneHint":"办公室"}""",
        ) as com.leo.libs.agent.tool.ToolResult.Ok
        assertEquals(Triple("o9", emptyList<String>(), "办公室"), captured)
        val card = parseToolResultCard(result.payload) as ToolResultCard.GeneratedImage
        assertEquals("o9", card.outfitId)
    }

    @Test
    fun `generate 工具无入参时友好报错`() = kotlinx.coroutines.runBlocking {
        val pid = "p1"
        val registry = wardrobeTools(
            data = { com.leo.wardrobe.domain.model.WardrobeData() },
            personId = { pid },
            imageGen = { _, _, _ -> com.leo.libs.agent.tool.ToolResult.ok("unreachable") },
        )
        val result = registry.execute("generate_outfit_image", "{}")
        assertTrue(result is com.leo.libs.agent.tool.ToolResult.Error)
    }
}

