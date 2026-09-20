package com.leo.eats.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * it-008 向后兼容：旧版本 eats.json（无 category/wishlistedAt/planAt 字段）
 * 反序列化后 == 吃分类 / 常规件 / 无安排，磁盘零迁移（同 wardrobe it-017 先例）。
 */
class PlaceCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `旧 JSON 缺新字段 - 反序列化为默认值`() {
        val legacy = """{"id":"p1","name":"巷子深火锅","kind":"RESTAURANT","createdAt":0,"updatedAt":0}"""
        val place = json.decodeFromString<Place>(legacy)
        assertEquals(PlaceCategory.EAT, place.category)
        assertNull(place.wishlistedAt)
        assertNull(place.planAt)
        assertEquals(false, place.isWish)
    }

    @Test
    fun `新 JSON 全字段往返`() {
        val place = Place(
            id = "p2",
            name = "敦煌大展",
            kind = PlaceKind.RESTAURANT,
            category = PlaceCategory.PLAY,
            wishlistedAt = 42L,
            planAt = 99L,
        )
        val roundtrip = json.decodeFromString<Place>(json.encodeToString(Place.serializer(), place))
        assertEquals(place, roundtrip)
    }

    @Test
    fun `EatsData 旧结构 - places 无新字段时整体加载成功`() {
        val legacy = """{"schemaVersion":1,"places":[{"id":"p1","name":"a","kind":"HOME"}],"visits":[]}"""
        val data = json.decodeFromString<EatsData>(legacy)
        assertEquals(1, data.places.size)
        assertEquals(PlaceCategory.EAT, data.places.first().category)
    }
}
