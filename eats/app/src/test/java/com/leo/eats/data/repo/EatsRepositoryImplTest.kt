package com.leo.eats.data.repo

import com.leo.eats.data.FakeImageStore
import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.Visit
import com.leo.libs.store.SnapshotStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EatsRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val images = FakeImageStore()

    private fun store() = SnapshotStore(
        dir = tmp.root,
        fileName = "eats.json",
        serializer = EatsData.serializer(),
        default = { EatsData() },
        versionOf = { it.schemaVersion },
    )

    private fun repo() = EatsRepositoryImpl(store(), images).apply {
        now = { 1_000L }
    }

    private fun place(
        id: String = "pl1",
        links: List<PlaceLink> = emptyList(),
        photos: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ) = Place(id = id, name = "  巷子深火锅  ", kind = PlaceKind.RESTAURANT, links = links, photos = photos, tags = tags)

    @Test
    fun `upsert 归一化 - 名称去空白 标签去重 链接去空去重`() = runTest {
        val repo = repo()
        repo.upsertPlace(
            place(
                links = listOf(
                    PlaceLink(" https://a.meituan.com/x ", "套餐"),
                    PlaceLink("https://a.meituan.com/x", "重复"),
                    PlaceLink("   ", "空"),
                ),
                tags = listOf("辣", "辣", "火锅"),
            ),
        )
        val saved = repo.data.value.places.single()
        assertEquals("巷子深火锅", saved.name)
        assertEquals(listOf("辣", "火锅"), saved.tags)
        assertEquals(1, saved.links.size)
        assertEquals("https://a.meituan.com/x", saved.links[0].url)
    }

    @Test
    fun `删除食堂级联删除 Visit 与双方照片`() = runTest {
        val repo = repo()
        repo.upsertPlace(place(photos = listOf("p1.webp")))
        repo.addVisit(Visit(id = "v1", placeId = "pl1", at = 1L, photos = listOf("v1a.webp")))
        repo.addVisit(Visit(id = "v2", placeId = "pl1", at = 2L, photos = listOf("v2a.webp")))

        repo.deletePlace("pl1")

        assertTrue(repo.data.value.places.isEmpty())
        assertTrue(repo.data.value.visits.isEmpty())
        assertEquals(setOf("p1.webp", "v1a.webp", "v2a.webp"), images.deleted.toSet())
    }

    @Test
    fun `删除 Visit 只删其照片 食堂保留`() = runTest {
        val repo = repo()
        repo.upsertPlace(place(photos = listOf("p1.webp")))
        repo.addVisit(Visit(id = "v1", placeId = "pl1", at = 1L, photos = listOf("v1a.webp")))

        repo.deleteVisit("v1")

        assertEquals(1, repo.data.value.places.size)
        assertTrue(repo.data.value.visits.isEmpty())
        assertEquals(listOf("v1a.webp"), images.deleted)
    }

    @Test
    fun `更新食堂时被移除的照片物理删除`() = runTest {
        val repo = repo()
        repo.upsertPlace(place(photos = listOf("p1.webp", "p2.webp")))
        repo.upsertPlace(place(photos = listOf("p1.webp")))
        assertEquals(listOf("p2.webp"), images.deleted)
    }

    @Test
    fun `载入时清洗悬空 Visit 引用`() = runTest {
        // 直接构造带悬空引用的存储文件
        val store = store()
        store.commit(
            EatsData(
                places = listOf(place(id = "pl1")),
                visits = listOf(
                    Visit(id = "v1", placeId = "pl1", at = 1L),
                    Visit(id = "v2", placeId = "ghost", at = 2L),
                ),
            ),
        )
        val repo = EatsRepositoryImpl(store, images)
        assertEquals(1, repo.data.value.visits.size)
        assertEquals("v1", repo.data.value.visits[0].id)
    }

    @Test
    fun `addVisit 补齐 id 与 createdAt`() = runTest {
        val repo = repo()
        repo.upsertPlace(place())
        repo.addVisit(Visit(id = "", placeId = "pl1", at = 5L))
        val v = repo.data.value.visits.single()
        assertTrue(v.id.isNotBlank())
        assertEquals(1_000L, v.createdAt)
    }

    @Test
    fun `数据经 StateFlow 广播`() = runTest {
        val repo = repo()
        repo.upsertPlace(place())
        assertEquals(1, repo.data.value.places.size)
        assertNull(repo.data.value.places[0].location)
    }
}
