package com.leo.eats.data.json

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = EatsData(
        places = listOf(
            Place(
                id = "pl1", name = "巷子深火锅", kind = PlaceKind.RESTAURANT, cuisine = "火锅",
                location = GeoLoc(31.22, 121.44), address = "某某路12号", rating = 4,
                tags = listOf("辣", "重口味"), photos = listOf("uuid1.webp"),
                links = listOf(PlaceLink("https://s.dianping.com/abcd", "双人套餐")),
                notes = "排队严重", createdAt = 1L, updatedAt = 2L,
            ),
        ),
        visits = listOf(
            Visit(id = "v1", placeId = "pl1", at = 100L, rating = 5, cost = 128.0, text = "毛肚绝了", createdAt = 100L),
        ),
    )

    @Test
    fun `保存后可完整读回`() {
        val store = JsonFileStore(tmp.root)
        store.save(sample)
        assertEquals(sample, store.load())
    }

    @Test
    fun `主文件损坏时回退到 bak`() {
        val store = JsonFileStore(tmp.root)
        store.save(sample)
        // 再次保存，使 bak 也为有效数据；随后破坏主文件
        store.save(sample.copy(schemaVersion = 1))
        tmp.root.resolve(JsonFileStore.FILE_NAME).writeText("{ 不是 json")
        assertEquals(sample, store.load())
    }

    @Test
    fun `首次保存也会补 bak`() {
        val store = JsonFileStore(tmp.root)
        store.save(sample)
        assertTrue(tmp.root.resolve("${JsonFileStore.FILE_NAME}.bak").exists())
    }

    @Test
    fun `空目录返回空数据`() {
        val store = JsonFileStore(tmp.root)
        assertTrue(store.load().isEmpty)
    }
}
