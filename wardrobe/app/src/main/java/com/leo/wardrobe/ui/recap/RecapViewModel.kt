package com.leo.wardrobe.ui.recap

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.wardrobe.WardrobeApp
import com.leo.wardrobe.data.prefs.RecapReminderPrefs
import com.leo.wardrobe.data.packages.WardrobePackages
import com.leo.wardrobe.domain.model.ImportMode
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WardrobeDiff
import com.leo.wardrobe.domain.model.referencedImages
import com.leo.wardrobe.domain.usecase.WardrobeRecapRange
import com.leo.wardrobe.domain.usecase.wardrobeRecap
import com.leo.wardrobe.platform.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 衣橱回顾域 ViewModel（it-021 自全局 AppViewModel 拆出）：
 * 回忆提醒设置、年度/累计长图生成与导出。角色与数据状态仍以全局 AppViewModel 为 SSOT 出口
 * （屏幕同时持有两个 VM）；本 VM 只持有回顾域自己的容器依赖。
 */
class RecapViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container

    /** 「好久没穿」提醒设置（DataStore，跨启动保留） */
    val recapPrefs: StateFlow<RecapReminderPrefs> =
        container.recapPrefs.flow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapReminderPrefs())

    val isDemo: Boolean get() = container.isDemo

    fun setReminder(enabled: Boolean, days: Int) {
        viewModelScope.launch {
            container.recapPrefs.set(enabled, days)
            if (!container.isDemo) {
                ReminderScheduler.sync(getApplication(), enabled)
            }
        }
    }

    /** 应用启动时对齐提醒任务与开关（兜底重启/升级；演示模式恒取消）；失败仅记日志 */
    fun syncReminderSchedule() {
        viewModelScope.launch {
            runCatching {
                val prefs = container.recapPrefs.snapshot()
                ReminderScheduler.sync(
                    getApplication(),
                    prefs.enabled && !container.isDemo,
                )
            }.onFailure { android.util.Log.e("Wardrobe", "syncReminderSchedule failed", it) }
        }
    }

    /** 生成年终衣橱长图（按 personId），写 export 目录返回文件；无角色/空打卡数据返回 null */
    fun generateRecap(personId: String?, range: WardrobeRecapRange, now: Long, onReady: (File?) -> Unit) {
        viewModelScope.launch {
            if (personId == null) {
                onReady(null); return@launch
            }
            runCatching {
                val stats = container.repository.data.value.wardrobeRecap(personId, range, now)
                if (!stats.hasWearData) {
                    onReady(null); return@launch
                }
                val label = when (val r = range) {
                    is WardrobeRecapRange.Year -> "${r.year}"
                    WardrobeRecapRange.All -> "衣橱总账"
                }
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(java.util.Date(now))
                val renderer = WardrobeRecapLongImage(
                    stats, label, dateStr, photoFileOf = { name -> imageFileOf(name) },
                )
                onReady(renderer.renderTo(container.imageEditStore.exportDir()))
            }.onFailure {
                android.util.Log.e("Wardrobe", "generateRecap failed", it)
                onReady(null)
            }
        }
    }

    fun imageFileOf(name: String): File? = container.imageStore.file(name)?.takeIf { it.exists() }

    /** 存相册（Pictures/Wardrobe，复用导出门面） */
    fun saveRecapImage(file: File): Boolean = container.share.saveToGallery(file)

    /** 分享（复用导出门面） */
    fun shareRecapImage(file: File) = container.share.shareImage(file)

    // ---- 数据包导入导出（it-024）----

    /** 导入流程状态机（D3–D7）；Confirm 为唯一决策点 */
    sealed interface ImportUi {
        data object Idle : ImportUi
        data object Checking : ImportUi
        data class Confirm(
            val fileName: String,
            val generator: String,
            val exportedAt: Long,
            val diff: WardrobeDiff,
            val localCounts: Map<String, Int>,
            val packageCounts: Map<String, Int>,
            /** 本地/包内被引用图片数（it-026 O2：替换明细两侧对称补图数） */
            val localImages: Int,
            val packageImages: Int,
        ) : ImportUi

        data class Running(val done: Int, val total: Int) : ImportUi
        data class Done(val added: Int, val updated: Int) : ImportUi
        data class Rejected(val reasons: List<String>) : ImportUi
    }

    /** 导出流程状态机（D2） */
    sealed interface ExportUi {
        data object Idle : ExportUi
        data class Running(val done: Int, val total: Int) : ExportUi
        data class Done(val summary: String, val shareFile: File) : ExportUi
        data class Failed(val message: String) : ExportUi
    }

    private val _importUi = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val importUi: StateFlow<ImportUi> = _importUi.asStateFlow()

    private val _exportUi = MutableStateFlow<ExportUi>(ExportUi.Idle)
    val exportUi: StateFlow<ExportUi> = _exportUi.asStateFlow()

    /** 预检通过待确认的包（持有打开的 ZipFile，用完必须清理） */
    private var pendingOk: WardrobePackages.Precheck.Ok? = null
    private var importCacheFile: File? = null

    fun exportTo(uri: Uri) {
        if (_exportUi.value is ExportUi.Running) return
        viewModelScope.launch {
            _exportUi.value = ExportUi.Running(0, 0)
            try {
                val app = getApplication<Application>()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
                val cacheFile = File(File(app.cacheDir, "share").apply { mkdirs() }, "wardrobe-backup-$stamp.zip")
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "?"
                val stats = withContext(Dispatchers.IO) {
                    container.packages.export(cacheFile.outputStream(), generator = "wardrobe $version") { d, t ->
                        _exportUi.value = ExportUi.Running(d, t)
                    }
                }
                // 双写：SAF 目标为正本，cache/share 留一份供 [分享]（FileProvider 需要）
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    cacheFile.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("无法写入所选位置")
                _exportUi.value = ExportUi.Done(
                    summary = "已导出 · ${stats.counts["items"] ?: 0} 单品 · ${stats.counts["outfits"] ?: 0} 穿搭 · ${stats.imageCount} 图",
                    shareFile = cacheFile,
                )
            } catch (t: Throwable) {
                android.util.Log.e("Wardrobe", "export failed", t)
                _exportUi.value = ExportUi.Failed(t.message ?: "导出失败")
            }
        }
    }

    fun dismissExport() {
        _exportUi.value = ExportUi.Idle
    }

    /** SAF 选包入口（④a）：拷入缓存再预检（外授 Uri 即取即用） */
    fun startImportFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            runCatching {
                val f = File(app.cacheDir, "import-${System.currentTimeMillis()}.zip")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                } ?: error("无法读取所选文件")
                f to (queryDisplayName(uri) ?: f.name)
            }.onSuccess { (f, name) ->
                startImport(f, name)
            }.onFailure {
                _importUi.value = ImportUi.Rejected(listOf("无法读取所选文件：${it.message}"))
            }
        }
    }

    /** 系统直达入口（④b）与 SAF 共用：文件已在本地缓存 */
    fun startImport(file: File, displayName: String = file.name) {
        if (_importUi.value is ImportUi.Checking || _importUi.value is ImportUi.Running) return
        _importUi.value = ImportUi.Checking
        viewModelScope.launch {
            when (val r = container.packages.precheck(file)) {
                is WardrobePackages.Precheck.Rejected -> {
                    file.delete()
                    _importUi.value = ImportUi.Rejected(r.reasons)
                }
                is WardrobePackages.Precheck.Ok -> {
                    pendingOk = r
                    importCacheFile = file
                    _importUi.value = ImportUi.Confirm(
                        fileName = displayName,
                        generator = r.manifest.generator,
                        exportedAt = r.manifest.exportedAt,
                        diff = r.diff,
                        localCounts = countsOf(container.repository.data.value),
                        packageCounts = r.manifest.counts,
                        localImages = container.repository.data.value.referencedImages().size,
                        packageImages = r.data.referencedImages().size,
                    )
                }
            }
        }
    }

    fun applyImport(mode: ImportMode) {
        val ok = pendingOk ?: run {
            _importUi.value = ImportUi.Rejected(listOf("导入会话已失效，请重新选择数据包"))
            return
        }
        viewModelScope.launch {
            _importUi.value = ImportUi.Running(0, 0)
            try {
                val stats = container.packages.apply(ok, mode) { d, t ->
                    _importUi.value = ImportUi.Running(d, t)
                }
                cleanupImport()
                _importUi.value = ImportUi.Done(stats.added, stats.updated)
            } catch (t: Throwable) {
                android.util.Log.e("Wardrobe", "applyImport failed", t)
                cleanupImport()
                _importUi.value = ImportUi.Rejected(
                    listOf("导入失败：${t.message ?: t.javaClass.simpleName}", "本地数据未受影响"),
                )
            }
        }
    }

    fun dismissImport() {
        cleanupImport()
        _importUi.value = ImportUi.Idle
    }

    fun consumeImportDone() {
        _importUi.value = ImportUi.Idle
    }

    private fun cleanupImport() {
        pendingOk?.let { container.packages.closeIfOk(it) }
        pendingOk = null
        importCacheFile?.delete()
        importCacheFile = null
    }

    private fun countsOf(d: WardrobeData): Map<String, Int> = mapOf(
        "items" to d.items.size, "outfits" to d.outfits.size, "notes" to d.notes.size,
        "wearLogs" to d.wearLogs.size, "wishItems" to d.wishItems.size, "wishOutfits" to d.wishOutfits.size,
    )

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 导出完成后的 [分享] 动作（D2③）：FileProvider 经 cache/share 授权外发 */
    fun sharePackage(file: File): Boolean = runCatching {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(Intent.createChooser(send, "分享数据包").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
