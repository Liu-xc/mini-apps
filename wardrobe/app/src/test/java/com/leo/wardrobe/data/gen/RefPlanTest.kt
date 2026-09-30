package com.leo.wardrobe.data.gen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * it-077 十一次修订：参考图取舍契约——纯文生图模型（上限 0）必须一张参考图都不带。
 * 回归背景：人物照曾无条件附带，Z-Image-Turbo（inputImages 0..0）收到 image 字段
 * 被 SF 拒 400 code 11235 "Input image error"（Leo 真机实报）。
 */
class RefPlanTest {

    @Test
    fun `纯文生图模型零上限一张不带`() {
        val plan = OutfitImageGenerator.refPlan(hasPerson = true, limit = 0)
        assertEquals(false, plan.attachPerson)
        assertEquals(0, plan.garments)
    }

    @Test
    fun `负上限同样零附带`() {
        val plan = OutfitImageGenerator.refPlan(hasPerson = false, limit = -1)
        assertEquals(false, plan.attachPerson)
        assertEquals(0, plan.garments)
    }

    @Test
    fun `有人物照时人物优先占一席`() {
        val plan = OutfitImageGenerator.refPlan(hasPerson = true, limit = 3)
        assertEquals(true, plan.attachPerson)
        assertEquals(2, plan.garments)
    }

    @Test
    fun `无人物照时全额给衣物`() {
        val plan = OutfitImageGenerator.refPlan(hasPerson = false, limit = 3)
        assertEquals(false, plan.attachPerson)
        assertEquals(3, plan.garments)
    }

    @Test
    fun `单图模型配人物照时不带衣物`() {
        val plan = OutfitImageGenerator.refPlan(hasPerson = true, limit = 1)
        assertEquals(true, plan.attachPerson)
        assertEquals(0, plan.garments)
    }
}
