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
        assertTrue(prompt.contains("不要复制输入图里的文字、边框、白底卡片或拼贴排版"))
        assertTrue(prompt.contains("若没有人物则生成一位真人模特"))
        assertFalse(prompt.contains("第一张参考图（人物）"))
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

    @Test
    fun `纯文生图分支不提长图并带衣架负面约束`() {
        // it-083：inputImages=0 的模型拿不到长图，文案不得引导模型画拼贴陈列
        val prompt = BuildTryOnPrompt.build(listOf(item("白衬衫", "白色")), "", "", hasReferenceImage = false)
        assertFalse(prompt.contains("长图"))
        assertFalse(prompt.contains("拼贴"))
        assertTrue(prompt.contains("真人模特"))
        assertTrue(prompt.contains("衣物必须穿在真人身上"))
    }

    @Test
    fun `参考图分支强调衣物穿到真人身上`() {
        val prompt = BuildTryOnPrompt.build(listOf(item("风衣", "卡其")), "", "", hasReferenceImage = true)
        assertTrue(prompt.contains("穿到真人身上"))
        assertTrue(prompt.contains("挂在衣架上或平铺陈列"))
        assertTrue(prompt.contains("不得出现衣架、人台、假人模特、平铺陈列"))
    }
}
