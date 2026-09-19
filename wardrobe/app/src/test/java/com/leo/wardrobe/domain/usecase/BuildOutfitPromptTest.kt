package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildOutfitPromptTest {

    private val builder = BuildOutfitPrompt()

    private val shirt = Item(
        id = "i1", personId = "p", category = WardrobeCategory.TOP,
        name = "白色牛津纺衬衫", color = "白色", desc = "宽松棉质、纽扣领", imageFile = "a.webp",
        tags = listOf("通勤", "简约"),
    )

    @Test
    fun itemLineCombinesFields() {
        assertEquals("上装：白色 白色牛津纺衬衫（宽松棉质、纽扣领）", builder.itemLine(shirt))
    }

    @Test
    fun itemLineSkipsBlankFields() {
        val plain = shirt.copy(color = "", desc = "")
        assertEquals("上装：白色牛津纺衬衫", builder.itemLine(plain))
    }

    @Test
    fun promptIncludesStyleTags() {
        val p = builder(listOf(shirt), styleTags = listOf("通勤", "简约"))
        assertTrue(p.contains("通勤·简约"))
        assertTrue(p.contains("- 上装：白色 白色牛津纺衬衫（宽松棉质、纽扣领）"))
        assertTrue(p.contains("保持颜色与款式一致"))
    }

    @Test
    fun promptWithoutTagsOmitsParens() {
        val p = builder(listOf(shirt), styleTags = emptyList())
        // 无风格标签时不出现「（xx风格）」；单品描述自身的括号不受影响
        assertTrue(!p.contains("风格）"))
        assertTrue(p.contains("效果图："))
    }
}
