package com.leo.eats.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.eats.EatsApp
import com.leo.eats.data.prefs.RecapReminderPrefs
import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.domain.model.Visit
import com.leo.eats.domain.model.newId
import com.leo.eats.domain.model.referencedImages
import com.leo.eats.domain.model.statsOfAll
import com.leo.eats.domain.usecase.RecapRange
import com.leo.eats.domain.usecase.SpinFilter
import com.leo.eats.domain.usecase.recap
import com.leo.eats.platform.LinkOpener
import com.leo.eats.platform.ReminderScheduler
import com.leo.eats.ui.recap.RecapLongImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 带撤销动作的提示（it-008 拔草撤销）：actionLabel 非空时 snackbar 出按钮 */
data class ToastAction(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/**
 * 全局 ViewModel（SSOT 出口，specs/04-architecture.md）：
 * 全量数据 + 转盘过滤配置 + 地图聚焦意图；写操作全部转发 Repository。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as EatsApp).container
    val repo = container.repository
    private val spinPrefs = container.spinPrefs

    val linkOpener: LinkOpener get() = container.linkOpener
    val buildCandidates get() = container.buildCandidates

    fun imageFileOf(name: String): File? = container.imageStore.file(name)?.takeIf { it.exists() }

    /** 一次性消息（snackbar） */
    private val _toast = MutableStateFlow<ToastAction?>(null)
    val toast: StateFlow<ToastAction?> = _toast.asStateFlow()

    fun toast(msg: String?) { _toast.value = msg?.let { ToastAction(it) } }

    /** it-012：带动作按钮的一次性消息（导出完成 → [分享]） */
    fun toastWithAction(message: String, actionLabel: String, onAction: () -> Unit) {
        _toast.value = ToastAction(message, actionLabel, onAction)
    }

    /**
     * it-009：写路径统一兜底——失败 Log + toast（quiet 时仅 Log），成功提示可选。
     * 内存快照回滚由 libs/store 的 commit 序列天然承担（commit 抛异常则快照不赋值）。
     */
    private fun launchSafely(
        okToast: String? = null,
        failToast: String = "操作失败",
        quiet: Boolean = false,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        try {
            block()
            okToast?.let(::toast)
        } catch (t: Throwable) {
            android.util.Log.e("Eats", "viewModel write failed", t)
            if (!quiet) toast("$failToast：${t.message ?: t.javaClass.simpleName}")
        }
    }

    val data: StateFlow<EatsData> = repo.data

    /** 转盘过滤配置（跨启动保留，specs/04 状态与导航） */
    val spinConfig: StateFlow<SpinFilter> = spinPrefs.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpinFilter())

    fun setSpinCategories(categories: Set<PlaceCategory>) {
        viewModelScope.launch { spinPrefs.setCategories(categories) }
    }

    fun setSpinKinds(kinds: Set<PlaceKind>) {
        viewModelScope.launch { spinPrefs.setKinds(kinds) }
    }

    fun setSpinExcludedTags(tags: Set<String>) {
        viewModelScope.launch { spinPrefs.setExcludedTags(tags) }
    }

    fun setSpinExcludeRecent(enabled: Boolean, days: Int) {
        viewModelScope.launch { spinPrefs.setExcludeRecent(enabled, days) }
    }

    fun setSpinWishOnly(enabled: Boolean) {
        viewModelScope.launch { spinPrefs.setWishOnly(enabled) }
    }

    /** 当前候选（含统计），供转盘页派生 */
    fun candidatesOf(filter: SpinFilter): List<PlaceWithStats> =
        buildCandidates(repo.data.value.statsOfAll(), filter, System.currentTimeMillis())

    // ---- Place ----

    fun savePlace(
        existing: Place?,
        name: String,
        kind: PlaceKind,
        category: PlaceCategory,
        cuisine: String,
        location: GeoLoc?,
        address: String,
        rating: Int?,
        tags: List<String>,
        keptPhotos: List<String>,
        newPhotoUris: List<String>,
        links: List<PlaceLink>,
        notes: String,
        wishlisted: Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        if (name.isBlank()) { toast("名称必填"); onDone(false); return }
        launchSafely(failToast = "保存失败") {
            val imported = newPhotoUris.mapNotNull { container.imageStore.importFromUri(it) }
            val p = Place(
                id = existing?.id ?: newId(),
                name = name.trim(),
                kind = kind,
                category = category,
                cuisine = cuisine.trim(),
                location = location,
                address = address.trim(),
                rating = rating?.coerceIn(1, 5),
                tags = tags.distinct().take(10),
                photos = keptPhotos + imported,
                links = links,
                notes = notes.trim(),
                // 种草：新建时按开关；编辑时保留原值（拔草只由记一笔触发，避免误改）
                wishlistedAt = existing?.wishlistedAt ?: if (wishlisted) System.currentTimeMillis() else null,
                planAt = existing?.planAt,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repo.upsertPlace(p)
            toast(if (existing == null) "已添加「${p.name}」" else "已更新「${p.name}」")
            onDone(true)
        }
    }

    fun deletePlace(id: String) = launchSafely(okToast = "食堂及其记录已删除") { repo.deletePlace(id) }

    /** 种草 / 取消种草（W5 详情操作） */
    fun setWish(placeId: String, wished: Boolean) = launchSafely {
        val place = repo.data.value.places.firstOrNull { it.id == placeId } ?: return@launchSafely
        repo.upsertPlace(place.copy(wishlistedAt = if (wished) System.currentTimeMillis() else null))
        toast(if (wished) "已种草 🌟" else "已取消种草")
    }

    /** 安排到某天 / 清除安排（W5 详情操作，it-008 阶段C） */
    fun setPlan(placeId: String, planAt: Long?) = launchSafely {
        val place = repo.data.value.places.firstOrNull { it.id == placeId } ?: return@launchSafely
        repo.upsertPlace(place.copy(planAt = planAt))
        toast(if (planAt != null) "已安排，到时提醒你" else "已清除安排")
    }

    // ---- Visit ----

    fun logVisit(
        placeId: String,
        at: Long,
        rating: Int?,
        cost: Double?,
        text: String,
        newPhotoUris: List<String>,
        onDone: (Boolean) -> Unit,
    ) {
        launchSafely(failToast = "落账失败") {
            val imported = newPhotoUris.mapNotNull { container.imageStore.importFromUri(it) }
            repo.addVisit(
                Visit(
                    id = "",
                    placeId = placeId,
                    at = at,
                    rating = rating?.coerceIn(1, 5),
                    cost = cost,
                    text = text.trim(),
                    photos = imported,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            // 拔草闭环（it-008 US-10）：愿望条目落账后自动移出愿望，snackbar 可撤销
            val place = repo.data.value.places.firstOrNull { it.id == placeId }
            val oldWish = place?.wishlistedAt
            if (place != null && oldWish != null) {
                repo.upsertPlace(place.copy(wishlistedAt = null))
                _toast.value = ToastAction("落账 ✓ 已移出愿望清单", "撤销") {
                    launchSafely(quiet = true) {
                        repo.data.value.places.firstOrNull { it.id == placeId }?.let {
                            repo.upsertPlace(it.copy(wishlistedAt = oldWish))
                        }
                    }
                }
            } else {
                toast("落账 ✓")
            }
            onDone(true)
        }
    }

    fun deleteVisit(id: String) = launchSafely(okToast = "已删除这条记录") { repo.deleteVisit(id) }

    // ---- 统计回顾（it-007） ----

    /** 回忆提醒设置（DataStore，跨启动保留） */
    val recapPrefs: StateFlow<RecapReminderPrefs> = container.recapPrefs.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapReminderPrefs())

    val isDemo: Boolean get() = container.isDemo

    fun setReminder(enabled: Boolean, days: Int) {
        launchSafely {
            container.recapPrefs.set(enabled, days)
            if (!container.isDemo) {
                ReminderScheduler.sync(getApplication(), enabled)
            }
        }
    }

    /** 应用启动时对齐提醒任务与开关（兜底重启/升级；演示模式恒取消）；失败仅记日志 */
    fun syncReminderSchedule() {
        launchSafely(quiet = true) {
            val prefs = container.recapPrefs.snapshot()
            ReminderScheduler.sync(getApplication(), prefs.enabled && !container.isDemo)
        }
    }

    fun generateRecap(range: RecapRange, now: Long, onReady: (Bitmap?) -> Unit) {
        launchSafely(failToast = "长图生成失败") {
            val stats = repo.data.value.recap(range, now)
            if (!stats.hasData) {
                onReady(null)
                return@launchSafely
            }
            val label = when (val r = range) {
                is RecapRange.Year -> "${r.year}"
                RecapRange.All -> "食光总账"
            }
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(java.util.Date(now))
            val bitmap = RecapLongImage(stats, label, dateStr) { name -> imageFileOf(name) }.render()
            onReady(bitmap)
        }
    }

    suspend fun saveRecap(bitmap: Bitmap): String? = container.recapSaver.saveToGallery(bitmap)

    fun shareRecap(bitmap: Bitmap): Intent = container.recapSaver.shareIntent(bitmap)

    // ---- 地图聚焦意图（W5「在地图上看」） ----

    private val _pendingMapFocus = MutableStateFlow<String?>(null)
    val pendingMapFocus: StateFlow<String?> = _pendingMapFocus.asStateFlow()

    fun focusOnMap(placeId: String) { _pendingMapFocus.value = placeId }

    fun consumeMapFocus() { _pendingMapFocus.value = null }

    // ---- 数据包导入导出（it-012）----

    /** 导入流程状态机（D3–D7）；Confirm 为唯一决策点 */
    sealed interface ImportUi {
        data object Idle : ImportUi
        data object Checking : ImportUi
        data class Confirm(
            val fileName: String,
            val generator: String,
            val exportedAt: Long,
            val diff: com.leo.eats.domain.model.EatsDiff,
            val localCounts: Map<String, Int>,
            val packageCounts: Map<String, Int>,
            /** 本地/包内被引用图片数（it-014 O2：替换明细两侧对称补图数） */
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

    private var pendingOk: com.leo.eats.data.packages.EatsPackages.Precheck.Ok? = null
    private var importCacheFile: File? = null

    fun exportTo(uri: android.net.Uri) {
        if (_exportUi.value is ExportUi.Running) return
        viewModelScope.launch {
            _exportUi.value = ExportUi.Running(0, 0)
            try {
                val app = getApplication<Application>()
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.CHINA)
                    .format(java.util.Date())
                val cacheFile = File(File(app.cacheDir, "share").apply { mkdirs() }, "eats-backup-$stamp.zip")
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "?"
                val stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    container.packages.export(cacheFile.outputStream(), generator = "eats $version") { d, t ->
                        _exportUi.value = ExportUi.Running(d, t)
                    }
                }
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    cacheFile.inputStream().use { input -> input.copyTo(out) }
                } ?: error("无法写入所选位置")
                _exportUi.value = ExportUi.Done(
                    summary = "已导出 · ${stats.counts["places"] ?: 0} 家 · ${stats.counts["visits"] ?: 0} 笔记录 · ${stats.imageCount} 图",
                    shareFile = cacheFile,
                )
            } catch (t: Throwable) {
                android.util.Log.e("Eats", "export failed", t)
                _exportUi.value = ExportUi.Failed(t.message ?: "导出失败")
            }
        }
    }

    fun dismissExport() { _exportUi.value = ExportUi.Idle }

    /** SAF 选包入口（④a）：拷入缓存再预检 */
    fun startImportFromUri(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    /** 系统直达入口（④b）与 SAF 共用 */
    fun startImport(file: File, displayName: String = file.name) {
        if (_importUi.value is ImportUi.Checking || _importUi.value is ImportUi.Running) return
        _importUi.value = ImportUi.Checking
        viewModelScope.launch {
            when (val r = container.packages.precheck(file)) {
                is com.leo.eats.data.packages.EatsPackages.Precheck.Rejected -> {
                    file.delete()
                    _importUi.value = ImportUi.Rejected(r.reasons)
                }
                is com.leo.eats.data.packages.EatsPackages.Precheck.Ok -> {
                    pendingOk = r
                    importCacheFile = file
                    _importUi.value = ImportUi.Confirm(
                        fileName = displayName,
                        generator = r.manifest.generator,
                        exportedAt = r.manifest.exportedAt,
                        diff = r.diff,
                        localCounts = countsOf(repo.data.value),
                        packageCounts = r.manifest.counts,
                        localImages = repo.data.value.referencedImages().size,
                        packageImages = r.data.referencedImages().size,
                    )
                }
            }
        }
    }

    fun applyImport(mode: com.leo.eats.domain.model.ImportMode) {
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
                android.util.Log.e("Eats", "applyImport failed", t)
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

    fun consumeImportDone() { _importUi.value = ImportUi.Idle }

    private fun cleanupImport() {
        pendingOk?.let { container.packages.closeIfOk(it) }
        pendingOk = null
        importCacheFile?.delete()
        importCacheFile = null
    }

    private fun countsOf(d: com.leo.eats.domain.model.EatsData): Map<String, Int> = mapOf(
        "places" to d.places.size,
        "visits" to d.visits.size,
    )

    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 导出完成后的 [分享] 动作：FileProvider 经 cache/share 授权外发 */
    fun sharePackage(file: File): Boolean = runCatching {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(
                Intent.EXTRA_STREAM,
                androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(Intent.createChooser(send, "分享数据包").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
