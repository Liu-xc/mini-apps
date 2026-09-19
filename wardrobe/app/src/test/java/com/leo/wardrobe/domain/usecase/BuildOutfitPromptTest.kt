package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun dimensionLineJoinsOnlySelectedInPresetOrder() {
        val line = builder.dimensionLine(
            mapOf("shot" to "全身照", "scene" to "城市街头"),
        )
        // 按 PromptPresets.dimensions 顺序（场景在前、构图在后）
        assertEquals("场景：城市街头；构图：全身照", line)
    }

    @Test
    fun dimensionLineEmptyWhenNothingSelected() {
        assertEquals("", builder.dimensionLine(emptyMap()))
    }

    @Test
    fun imageChannelOmitsItemList() {
        val p = builder(listOf(shirt), selections = mapOf("scene" to "办公室"), personNote = "", includeItems = false)
        assertFalse(p.contains("单品："))
        assertTrue(p.contains("场景：办公室"))
        assertTrue(p.contains("完整的人物穿搭与环境场景信息"))
    }

    @Test
    fun textChannelIncludesItemListAndPersonNote() {
        val p = builder(
            listOf(shirt),
            selections = mapOf("mood" to "通勤简约", "season" to "早秋"),
            personNote = "175cm 偏瘦、短黑发男生",
            includeItems = true,
        )
        assertTrue(p.contains("单品：上装：白色 白色牛津纺衬衫（宽松棉质、纽扣领）"))
        assertTrue(p.contains("人物描述：175cm 偏瘦、短黑发男生"))
        assertTrue(p.contains("氛围：通勤简约"))
        assertTrue(p.contains("季节：早秋"))
    }

    @Test
    fun emptySelectionsOmitDimensionLine() {
        val p = builder(listOf(shirt), selections = emptyMap(), personNote = "", includeItems = false)
        assertFalse(p.contains("场景："))
        assertTrue(p.contains("这张长图"))
    }
}
