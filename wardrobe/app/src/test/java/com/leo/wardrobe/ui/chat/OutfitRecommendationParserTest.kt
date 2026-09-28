package com.leo.wardrobe.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitRecommendationParserTest {

    @Test
    fun `提取方案标题单品与理由并移除卡片协议行`() {
        val parsed = parseAssistantReply(
            """
            先给你一套稳妥的方案：

            ## 第一套 · 通勤

            **适合**：办公室 / 早秋

            - 上装：**牛津纺衬衫**
            - 下装：直筒牛仔裤
            - 鞋：板鞋

            **理由**：整体干净、通勤而不拘谨。
            """.trimIndent(),
        )

        assertEquals(1, parsed.recommendations.size)
        val outfit = parsed.recommendations.single()
        assertEquals("第一套 · 通勤", outfit.title)
        assertEquals(listOf("牛津纺衬衫", "直筒牛仔裤", "板鞋"), outfit.items.map { it.itemName })
        assertTrue(outfit.detailMarkdown.contains("**适合**"))
        assertTrue(outfit.detailMarkdown.contains("**理由**"))
        assertTrue(parsed.parts.first() is AssistantReplyPart.Markdown)
        assertTrue(parsed.parts.none { it is AssistantReplyPart.Markdown && it.text.contains("- 上装") })
    }

    @Test
    fun `非品类列表与普通二级标题继续作为 markdown`() {
        val parsed = parseAssistantReply(
            """
            ## 先看天气
            - 温度：18℃

            ## 第一套 · 休闲
            - 上装：棉质T恤
            - 下装：工装裤
            """.trimIndent(),
        )

        assertEquals(1, parsed.recommendations.size)
        val ordinary = parsed.parts.filterIsInstance<AssistantReplyPart.Markdown>().joinToString("\n")
        assertTrue(ordinary.contains("## 先看天气"))
        assertTrue(ordinary.contains("- 温度：18℃"))
    }

    @Test
    fun `没有可识别单品的方案不生成空卡片`() {
        val parsed = parseAssistantReply("## 第一套 · 通勤\n\n这是一段普通说明。")

        assertTrue(parsed.recommendations.isEmpty())
        assertEquals(1, parsed.parts.size)
        assertTrue((parsed.parts.single() as AssistantReplyPart.Markdown).text.contains("## 第一套"))
    }

    @Test
    fun `清理名称里的强调和链接语法`() {
        assertEquals("牛津纺衬衫", cleanInlineMarkdown("**牛津纺衬衫**"))
        assertEquals("查看衬衫", cleanInlineMarkdown("[查看衬衫](https://example.com)"))
    }
}

