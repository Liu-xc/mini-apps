package com.leo.wardrobe.data.packages

import android.util.Log
import com.leo.libs.store.PackageCodec
import com.leo.libs.store.PackageException
import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.domain.model.ImportMode
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WardrobeDiff
import com.leo.wardrobe.domain.model.diffWardrobe
import com.leo.wardrobe.domain.model.mergeWardrobe
import com.leo.wardrobe.domain.model.referencedImages
import com.leo.wardrobe.domain.model.remapImageRefs
import com.leo.wardrobe.domain.repository.WardrobeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * 数据包服务（it-024）：导出 / 预检 / 合并·替换导入。
 * zip 结构与 manifest 契约由 store SDK 的 [PackageCodec] 承担；合并语义在 domain 纯函数。
 * 预检全过才动本地数据（D7 零改动承诺）；导入终步 [WardrobeRepository.replaceAll] 单事务落盘。
 */
class WardrobePackages(
    private val repo: WardrobeRepository,
    private val store: SnapshotStore<WardrobeData>,
    private val images: ImageFileStore,
    private val isDemo: Boolean,
) {
    companion object {
        private const val TAG = "Wardrobe"
        private val APP_NAMES = mapOf("wardrobe" to "衣橱", "eats" to "吃啥")
        private val CURRENT_SCHEMA get() = WardrobeData().schemaVersion
    }

    private val codec = PackageCodec<WardrobeData>(
        appId = "wardrobe",
        dataFileName = "wardrobe.json",
        serializer = WardrobeData.serializer(),
        expectedSchemaVersion = CURRENT_SCHEMA,
    )

    data class ExportStats(val counts: Map<String, Int>, val imageCount: Int)

    data class ImportStats(val added: Int, val updated: Int)

    /** 预检结果：Ok 携带已升级到当前版本的数据与差异摘要；Rejected 携带用户可读理由（本地零改动） */
    sealed interface Precheck {
        data class Ok(
            val pkg: PackageCodec.RawPackage<WardrobeData>,
            val data: WardrobeData,
            val diff: WardrobeDiff,
            val manifest: com.leo.libs.store.PackageManifest,
        ) : Precheck

        data class Rejected(val reasons: List<String>) : Precheck
    }

    /** 导出当前全量数据（演示模式下拒绝） */
    suspend fun export(
        out: OutputStream,
        generator: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportStats {
        check(!isDemo) { "演示模式下不可导出" }
        val data = repo.data.value
        val names = data.referencedImages().sorted()
        codec.write(
            out, data,
            exportedAt = System.currentTimeMillis(),
            generator = generator,
            counts = countsOf(data),
            imageNames = names,
            readImage = { n -> images.file(n)?.takeIf { it.exists() }?.readBytes() },
            onProgress = onProgress,
        )
        return ExportStats(countsOf(data), names.size)
    }

    /** 预检：结构（codec）→ 图片齐全可解码 → 差异摘要。任何失败整包拒绝，不动本地数据 */
    suspend fun precheck(file: File): Precheck = withContext(Dispatchers.IO) {
        if (isDemo) return@withContext Precheck.Rejected(listOf("演示模式下不可导入，请先在设置中退出演示模式"))
        val pkg = try {
            codec.read(file)
        } catch (e: PackageException) {
            return@withContext Precheck.Rejected(listOf(reasonOf(e)))
        }
        val reasons = mutableListOf<String>()
        val referenced = pkg.data.referencedImages().sorted()
        val missing = referenced.filterNot { pkg.hasImage(it) }
        if (missing.isNotEmpty()) {
            reasons += "缺失图片 ${missing.size} 张：${missing.take(3).joinToString(" / ")}" +
                if (missing.size > 3) " 等" else ""
        }
        val bad = mutableListOf<String>()
        referenced.forEach { name ->
            val bytes = pkg.imageBytes(name)
            if (bytes != null && !images.isDecodableImage(bytes)) bad += name
        }
        if (bad.isNotEmpty()) {
            reasons += "无法解码的图片 ${bad.size} 张：${bad.take(3).joinToString(" / ")}" +
                if (bad.size > 3) " 等" else ""
        }
        if (reasons.isNotEmpty()) {
            pkg.close()
            return@withContext Precheck.Rejected(reasons)
        }
        val upgraded = store.upgrade(pkg.data)
        val diff = diffWardrobe(repo.data.value, upgraded, pkg.manifest.exportedAt)
        Precheck.Ok(pkg, upgraded, diff, pkg.manifest)
    }

    /**
     * 执行导入（须先 [precheck] 通过）：
     * 包图片全部归一化落盘（新 uuid.webp + 引用重映射）→ 合并/替换 → 单事务落盘 → 回收不再引用的旧文件。
     * 中途失败：已落盘的孤儿图片无引用、无害。
     */
    suspend fun apply(
        ok: Precheck.Ok,
        mode: ImportMode,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportStats = withContext(Dispatchers.IO) {
        check(!isDemo) { "演示模式下不可导入" }
        val local = repo.data.value
        val localRefs = local.referencedImages()

        val refs = ok.data.referencedImages().sorted()
        val nameMap = HashMap<String, String>(refs.size)
        refs.forEachIndexed { i, name ->
            val bytes = ok.pkg.imageBytes(name)
                ?: throw IOException("图片缺失（预检后数据包被改动）：$name")
            val newName = images.putPackageImage(bytes)
                ?: throw IOException("图片处理失败：$name")
            nameMap[name] = newName
            onProgress(i + 1, refs.size)
        }
        val incoming = ok.data.remapImageRefs { old -> nameMap[old] ?: old }

        when (mode) {
            ImportMode.MERGE -> {
                val merged = mergeWardrobe(local, incoming)
                val keptRefs = merged.referencedImages()
                repo.replaceAll(merged)
                localRefs.filterNot { it in keptRefs }.forEach { runCatching { images.delete(it) } }
            }
            ImportMode.REPLACE -> {
                repo.replaceAll(incoming)
                localRefs.forEach { runCatching { images.delete(it) } }
            }
        }
        Log.i(TAG, "数据包导入完成 mode=$mode")
        ImportStats(added = ok.diff.addedTotal, updated = ok.diff.updatedTotal)
    }

    fun closeIfOk(precheck: Precheck) {
        if (precheck is Precheck.Ok) runCatching { precheck.pkg.close() }
    }

    private fun countsOf(d: WardrobeData): Map<String, Int> = mapOf(
        "persons" to d.persons.size,
        "items" to d.items.size,
        "outfits" to d.outfits.size,
        "notes" to d.notes.size,
        "wearLogs" to d.wearLogs.size,
        "wishItems" to d.wishItems.size,
        "wishOutfits" to d.wishOutfits.size,
    )

    private fun reasonOf(e: PackageException): String = when (e) {
        is PackageException.NotZip -> "文件不是有效的数据包（损坏或不是 zip 数据包）"
        is PackageException.WrongApp -> {
            val name = APP_NAMES[e.actualApp] ?: e.actualApp
            "这是「$name」的数据包，请在「$name」中导入"
        }
        is PackageException.UnsupportedFormat -> "数据包格式版本 ${e.format} 不受支持，请升级应用"
        is PackageException.NewerSchema -> "数据包来自更新版本（schema ${e.version} > ${e.expected}），请先升级应用"
        is PackageException.BadData -> "数据文件解析失败：${e.cause?.message ?: "格式不合法"}"
    }
}
