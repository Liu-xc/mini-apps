package com.leo.wardrobe.ui.chat

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---- it-055 US-56/58：协议单品 → 衣橱匹配（tile 可点与导出素材的共同真源）----

    @Test
    fun `匹配按名称规范化进行——对上的给 Item 对不上的保留 null`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 通勤",
            items = listOf(
                OutfitRecommendationItem("上装", "牛津纺衬衫"),
                OutfitRecommendationItem("下装", "不存在的裤子"),
                OutfitRecommendationItem("鞋", "板鞋"),
            ),
            detailMarkdown = "",
        )
        val wardrobe = listOf(
            Item("i1", "p1", WardrobeCategory.TOP, "牛津纺 衬衫", imageFile = "a.webp"),
            Item("i2", "p1", WardrobeCategory.BOTTOM, "直筒牛仔裤", imageFile = "b.webp"),
            Item("i3", "p1", WardrobeCategory.SHOES, "板鞋", imageFile = "c.webp"),
        )

        val matched = matchRecommendationItems(recommendation, wardrobe)

        assertEquals(3, matched.size)
        assertEquals("i1", matched[0].second?.id)
        assertNull(matched[1].second)
        assertEquals("不存在的裤子", matched[1].first.itemName)
        assertEquals("i3", matched[2].second?.id)
    }

    @Test
    fun `匹配保持协议顺序且衣橱多件同名时取首件`() {
        val recommendation = OutfitRecommendation(
            title = "第二套 · 休闲",
            items = listOf(
                OutfitRecommendationItem("上装", "白T恤"),
                OutfitRecommendationItem("下装", "工装裤"),
            ),
            detailMarkdown = "",
        )
        val wardrobe = listOf(
            Item("t1", "p1", WardrobeCategory.TOP, "白T恤", imageFile = "1.webp"),
            Item("t2", "p1", WardrobeCategory.TOP, "白T恤", imageFile = "2.webp"),
            Item("b1", "p1", WardrobeCategory.BOTTOM, "工装裤", imageFile = "3.webp"),
        )

        val matched = matchRecommendationItems(recommendation, wardrobe)

        assertEquals(listOf("上装", "下装"), matched.map { it.first.categoryLabel })
        assertEquals("t1", matched[0].second?.id)
        assertEquals("b1", matched[1].second?.id)
    }

    // ---- it-056/it-057：导出面板五维预选提取 ----

    @Test
    fun `预设命中优先于自由回填且氛围按前缀放宽`() {
        val parsed = parseAssistantReply(
            """
            ## 第一套 · 通勤

            - 上装：牛津纺衬衫

            **适合**：办公室 / 早秋
            """.trimIndent(),
        )
        val recommendation = parsed.recommendations.single()

        val selections = extractRecommendationSelections(recommendation)

        // 「办公室」出现在适合行 → 选预设而非回填「通勤」；场景短语「通勤」经前缀放宽命中「通勤简约」
        assertEquals("办公室", selections["scene"])
        assertEquals("早秋", selections["season"])
        assertEquals("通勤简约", selections["mood"])
    }

    @Test
    fun `季节最长选项优先命中且场景短语自由回填`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 早春踏青",
            items = listOf(OutfitRecommendationItem("上装", "白T恤")),
            detailMarkdown = "",
        )

        val selections = extractRecommendationSelections(recommendation)

        assertEquals("早春", selections["season"])
        assertEquals("早春踏青", selections["scene"])
    }

    @Test
    fun `无预设命中时场景回填短语原文其余维度为空`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 音乐节造型",
            items = listOf(OutfitRecommendationItem("上装", "白T恤")),
            detailMarkdown = "**理由**：醒目、方便活动。",
        )

        val selections = extractRecommendationSelections(recommendation)

        assertEquals("音乐节造型", selections["scene"])
        assertNull(selections["mood"])
        assertNull(selections["season"])
        assertNull(selections["light"])
        assertNull(selections["shot"])
    }

    @Test
    fun `标题与理由分散命中可合并多维度`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 海边度假",
            items = listOf(OutfitRecommendationItem("上装", "白T恤")),
            detailMarkdown = "**理由**：运动活力，出片选全身照。",
        )

        val selections = extractRecommendationSelections(recommendation)

        assertEquals("海边", selections["scene"])
        assertEquals("运动活力", selections["mood"])
        assertEquals("全身照", selections["shot"])
    }

    // ---- it-057：自由场景与氛围放宽 ----

    @Test
    fun `超长场景短语截断到八字`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 周末休闲逛街遛娃看展",
            items = listOf(OutfitRecommendationItem("上装", "白T恤")),
            detailMarkdown = "",
        )

        assertEquals("周末休闲逛街遛娃", extractRecommendationSelections(recommendation)["scene"])
    }

    @Test
    fun `氛围前缀两字即可命中且季节光线不放宽`() {
        val recommendation = OutfitRecommendation(
            title = "第一套 · 约会晚餐",
            items = listOf(OutfitRecommendationItem("上装", "白T恤")),
            detailMarkdown = "**理由**：整体温柔一些。",
        )

        val selections = extractRecommendationSelections(recommendation)

        assertEquals("温柔知性", selections["mood"])
        // 枚举维度不接受半截词（「温柔」不含任何季节/光线前缀）
        assertNull(selections["season"])
        assertNull(selections["light"])
    }
}

