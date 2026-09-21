package com.leo.libs.store

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
private data class FakeData(val schemaVersion: Int = 1, val names: List<String> = emptyList())

class PackageCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val codec = PackageCodec<FakeData>("wardrobe", "wardrobe.json", FakeData.serializer(), expectedSchemaVersion = 1)
    private val json = kotlinx.serialization.json.Json { encodeDefaults = true; prettyPrint = true }

    /** 手工构造包（绕过 codec.write，用于构造非法/跨应用包） */
    private fun writePackage(
        data: FakeData = FakeData(names = listOf("a", "b")),
        images: Map<String, ByteArray> = mapOf("x.webp" to byteArrayOf(1, 2, 3)),
        manifestApp: String = "wardrobe",
        format: Int = 1,
        schema: Int = 1,
        dataJson: String? = null,
    ) = tmp.newFile("p-${System.nanoTime()}.zip").apply {
        val manifest = PackageManifest(
            packageFormat = format, app = manifestApp, schemaVersion = schema,
            exportedAt = 42L, generator = "test", counts = mapOf("names" to 2),
        )
        ZipOutputStream(outputStream()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry(PackageManifest.FILE_NAME, json.encodeToString(PackageManifest.serializer(), manifest).encodeToByteArray())
            entry("wardrobe.json", dataJson?.encodeToByteArray() ?: json.encodeToString(FakeData.serializer(), data).encodeToByteArray())
            images.forEach { (n, b) -> entry("images/$n", b) }
        }
    }

    @Test
    fun `write 后 read 往返一致`() = runTest {
        val out = tmp.newFile("roundtrip.zip")
        val data = FakeData(names = listOf("衬衫", "牛仔裤"))
        codec.write(
            out.outputStream(), data, exportedAt = 100L, generator = "wardrobe 0.6.0",
            counts = mapOf("names" to 2), imageNames = listOf("a.webp", "b.webp"),
            readImage = { n -> if (n == "a.webp") byteArrayOf(1) else byteArrayOf(2, 2) },
        )
        codec.read(out).use { pkg ->
            assertEquals(data, pkg.data)
            assertEquals(100L, pkg.manifest.exportedAt)
            assertEquals("wardrobe 0.6.0", pkg.manifest.generator)
            assertEquals(setOf("a.webp", "b.webp"), pkg.imageNames)
            assertTrue(pkg.hasImage("a.webp"))
            assertEquals(1, pkg.imageBytes("a.webp")!!.size)
            assertEquals(2, pkg.imageBytes("b.webp")!!.size)
        }
    }

    @Test
    fun `readImage 读不到的图片跳过不出错`() = runTest {
        val out = tmp.newFile("skip.zip")
        codec.write(out.outputStream(), FakeData(), 1L, "g", emptyMap(), listOf("a", "missing"), { n ->
            if (n == "a") byteArrayOf(9) else null
        })
        codec.read(out).use { pkg ->
            assertEquals(setOf("a"), pkg.imageNames)
            assertFalse(pkg.hasImage("missing"))
        }
    }

    @Test
    fun `跨应用包报 WrongApp`() {
        assertThrows(PackageException.WrongApp::class.java) {
            codec.read(writePackage(manifestApp = "eats")).use { }
        }.also { assertEquals("eats", it.actualApp) }
    }

    @Test
    fun `包格式版本不支持`() {
        assertThrows(PackageException.UnsupportedFormat::class.java) {
            codec.read(writePackage(format = 2)).use { }
        }
    }

    @Test
    fun `schema 高于当前报 NewerSchema`() {
        assertThrows(PackageException.NewerSchema::class.java) {
            codec.read(writePackage(schema = 3)).use { }
        }
    }

    @Test
    fun `非 zip 文件报 NotZip`() {
        val f = tmp.newFile("junk.zip").apply { writeText("这不是zip") }
        assertThrows(PackageException.NotZip::class.java) { codec.read(f).use { } }
    }

    @Test
    fun `缺 manifest 报 NotZip`() {
        val f = tmp.newFile("nomanifest.zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("wardrobe.json")); zip.write("{}".toByteArray()); zip.closeEntry()
        }
        assertThrows(PackageException.NotZip::class.java) { codec.read(f).use { } }
    }

    @Test
    fun `数据文件反序列化失败报 BadData`() {
        // names 应为数组，给字符串 → 解码失败
        assertThrows(PackageException.BadData::class.java) {
            codec.read(writePackage(dataJson = """{"schemaVersion":1,"names":"oops"}""")).use { }
        }
    }

    @Test
    fun `未知字段被忽略（与 SnapshotStore 同宽容度）`() {
        codec.read(
            writePackage(dataJson = """{"schemaVersion":1,"names":["a"],"futureField":123}"""),
        ).use { pkg -> assertEquals(listOf("a"), pkg.data.names) }
    }

    @Test
    fun `SnapshotStore upgrade 恒等迁移`() {
        val store = SnapshotStore<FakeData>(tmp.root, "f.json", FakeData.serializer(), { FakeData() }, { it.schemaVersion })
        assertEquals(FakeData(names = listOf("x")), store.upgrade(FakeData(names = listOf("x"))))
    }
}
