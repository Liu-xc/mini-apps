package com.leo.wardrobe.data.repo

import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 打卡数据不变量（it-018 阶段A，specs/03 级联规则） */
class WearLogRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val images = FakeImageStore()

    private fun newRepo(): WardrobeRepositoryImpl = WardrobeRepositoryImpl(
        SnapshotStore(
            dir = tmp.root,
            fileName = "wardrobe_${System.nanoTime()}.json",
            serializer = WardrobeData.serializer(),
            default = { WardrobeData() },
        ),
        images,
    )

    private suspend fun seed(r: WardrobeRepositoryImpl): Triple<Person, Item, Outfit> {
        val p = r.ensureDefaultPerson()
        val shirt = Item("i1", p.id, WardrobeCategory.TOP, "白衬衫", imageFile = "shirt.webp")
        r.upsertItem(shirt)
        val outfit = r.createOutfit(p.id, listOf("i1"))
        return Triple(p, shirt, outfit)
    }

    @Test
    fun addWearLogAppendsAndKeepsSameDayDuplicates() = runTest {
        val r = newRepo()
        val (_, _, outfit) = seed(r)
        r.addWearLog(outfit.id, 1_000L)
        r.addWearLog(outfit.id, 2_000L)
        assertEquals(2, r.data.value.wearLogs.size)
    }

    @Test
    fun addWearLogRejectsMissingOutfit() = runTest {
        val r = newRepo()
        seed(r)
        var thrown = false
        try {
            r.addWearLog("missing", 1_000L)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun deleteWearLogsOfRemovesOnlyRangeAndOutfit() = runTest {
        val r = newRepo()
        val (_, _, outfit) = seed(r)
        r.addWearLog(outfit.id, 1_000L)
        r.addWearLog(outfit.id, 5_000L)
        r.deleteWearLogsOf(outfit.id, 0L, 3_000L)
        val logs = r.data.value.wearLogs
        assertEquals(1, logs.size)
        assertEquals(5_000L, logs.single().at)
    }

    @Test
    fun deleteOutfitCascadesWearLogs() = runTest {
        val r = newRepo()
        val (_, _, outfit) = seed(r)
        r.addWearLog(outfit.id, 1_000L)
        r.deleteOutfit(outfit.id)
        assertTrue(r.data.value.wearLogs.isEmpty())
    }

    @Test
    fun deletePersonCascadesWearLogs() = runTest {
        val r = newRepo()
        val (person, _, outfit) = seed(r)
        r.addWearLog(outfit.id, 1_000L)
        r.deletePerson(person.id)
        assertTrue(r.data.value.wearLogs.isEmpty())
    }

    @Test
    fun cleanedDropsDanglingWearLogsOnLoad() = runTest {
        val r = newRepo()
        val (person, _, outfit) = seed(r)
        r.addWearLog(outfit.id, 1_000L)
        // 直接构造带悬空引用的 JSON 再加载：写入→改文件→重建仓库
        val dangling = WearLog("w9", person.id, "ghost-outfit", 1_000L)
        val broken = r.data.value.copy(wearLogs = r.data.value.wearLogs + dangling)
        val store = SnapshotStore(
            dir = tmp.root,
            fileName = "wardrobe_dangling.json",
            serializer = WardrobeData.serializer(),
            default = { WardrobeData() },
        )
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.io.File(tmp.root, "wardrobe_dangling.json").writeText(
                kotlinx.serialization.json.Json.encodeToString(WardrobeData.serializer(), broken),
            )
        }
        val reloaded = WardrobeRepositoryImpl(store, images)
        assertTrue(reloaded.data.value.wearLogs.none { it.outfitId == "ghost-outfit" })
        assertEquals(1, reloaded.data.value.wearLogs.size)
    }
}
