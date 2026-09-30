package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** it-077：AI 试衣提示词组装（W7 sheet 与顾问工具共用口径） */
class BuildTryOnPromptTest {

    private fun item(name: String, color: String = "", category: WardrobeCategory = WardrobeCategory.TOP) =
        Item(id = name, personId = "p", category = category, name = name, color = color, imageFile = "$name.webp")

    @Test
    fun `单品清单与保真要求齐备`() {
        val prompt = BuildTryOnPrompt.build(listOf(item("白衬衫", "白色"), item("牛仔裤", "蓝色", WardrobeCategory.BOTTOM)), "", "")
        assertTrue(prompt.contains("白衬衫"))
        assertTrue(prompt.contains("牛仔裤"))
        assertTrue(prompt.contains("白色"))
        assertTrue(prompt.contains("严格保持每件衣物的版型、颜色、图案与材质细节"))
        assertFalse(prompt.contains("场景："))
        assertFalse(prompt.contains("人物描述："))
    }

    @Test
    fun `场景与人物描述按需附加`() {
        val prompt = BuildTryOnPrompt.build(listOf(item("风衣")), "城市街头", "30 岁男性，175cm")
        assertTrue(prompt.contains("场景：城市街头"))
        assertTrue(prompt.contains("人物描述：30 岁男性，175cm"))
    }

    @Test
    fun `空单品不输出清单行`() {
        val prompt = BuildTryOnPrompt.build(emptyList(), "", "")
        assertFalse(prompt.contains("衣物清单："))
    }
}
