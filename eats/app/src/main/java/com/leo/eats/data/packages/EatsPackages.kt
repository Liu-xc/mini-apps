package com.leo.eats.data.packages

import android.util.Log
import com.leo.eats.data.image.ImageFileStore
import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.EatsDiff
import com.leo.eats.domain.model.ImportMode
import com.leo.eats.domain.model.diffEats
import com.leo.eats.domain.model.mergeEats
import com.leo.eats.domain.model.referencedImages
import com.leo.eats.domain.model.remapImageRefs
import com.leo.eats.domain.model.validateEatsData
import com.leo.eats.domain.repository.EatsRepository
import com.leo.libs.store.PackageCodec
import com.leo.libs.store.PackageException
import com.leo.libs.store.SnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * 数据包服务（it-012，与 wardrobe it-024 同构）：
 * 导出 / 预检（含 eats 特有校验）/ 合并·替换导入。
 * 预检全过才动本地数据；导入终步 [EatsRepository.replaceAll] 单事务落盘。
 */
class EatsPackages(
    private val repo: EatsRepository,
    private val store: SnapshotStore<EatsData>,
    private val images: ImageFileStore,
    private val isDemo: Boolean,
) {
    companion object {
        private const val TAG = "Eats"
        private val APP_NAMES = mapOf("wardrobe" to "衣橱", "eats" to "吃啥")
        private val CURRENT_SCHEMA get() = EatsData().schemaVersion
    }

    private val codec = PackageCodec<EatsData>(
        appId = "eats",
        dataFileName = "eats.json",
        serializer = EatsData.serializer(),
        expectedSchemaVersion = CURRENT_SCHEMA,
    )

    data class ExportStats(val counts: Map<String, Int>, val imageCount: Int)

    data class ImportStats(val added: Int, val updated: Int)

    sealed interface Precheck {
        data class Ok(
            val pkg: PackageCodec.RawPackage<EatsData>,
            val data: EatsData,
            val diff: EatsDiff,
            val manifest: com.leo.libs.store.PackageManifest,
        ) : Precheck

        data class Rejected(val reasons: List<String>) : Precheck
    }

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
            readImage = { n -> images.file(n).takeIf { it.exists() }?.readBytes() },
            onProgress = onProgress,
        )
        return ExportStats(countsOf(data), names.size)
    }

    suspend fun precheck(file: File): Precheck = withContext(Dispatchers.IO) {
        if (isDemo) return@withContext Precheck.Rejected(listOf("演示模式下不可导入，请先在设置中退出演示模式"))
        val pkg = try {
            codec.read(file)
        } catch (e: PackageException) {
            return@withContext Precheck.Rejected(listOf(reasonOf(e)))
        }
        val reasons = mutableListOf<String>()
        reasons += validateEatsData(pkg.data)
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
        val diff = diffEats(repo.data.value, upgraded, pkg.manifest.exportedAt)
        Precheck.Ok(pkg, upgraded, diff, pkg.manifest)
    }

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
                val merged = mergeEats(local, incoming)
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

    private fun countsOf(d: EatsData): Map<String, Int> = mapOf(
        "places" to d.places.size,
        "visits" to d.visits.size,
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
