package com.leo.wardrobe.data.repo

import com.leo.wardrobe.data.json.JsonFileStore
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun saveLoadRoundtrip() {
        val store = JsonFileStore(tmp.root)
        val data = WardrobeData(
            persons = listOf(Person("p1", "Leo", "👨")),
            items = listOf(Item("i1", "p1", WardrobeCategory.TOP, "白衬衫", imageFile = "a.webp", tags = listOf("通勤"))),
        )
        store.save(data)
        assertEquals(data, store.load())
    }

    @Test
    fun corruptedMainFallsBackToBak() {
        val dir = tmp.root
        val store = JsonFileStore(dir)
        store.save(WardrobeData(persons = listOf(Person("p1", "我"))))
        // 破坏主文件（bak 仍是上次保存的完整版本）
        File(dir, JsonFileStore.FILE_NAME).writeText("{ broken json !!!")
        val loaded = store.load()
        assertEquals(listOf("p1"), loaded.persons.map { it.id })
    }

    @Test
    fun bothCorruptedReturnsEmpty() {
        val dir = tmp.root
        File(dir, JsonFileStore.FILE_NAME).writeText("not json")
        File(dir, "${JsonFileStore.FILE_NAME}.bak").writeText("not json either")
        assertTrue(JsonFileStore(dir).load().isEmpty)
    }
}
