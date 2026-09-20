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
        viewModelScope.launch {
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

    fun deletePlace(id: String) = viewModelScope.launch {
        repo.deletePlace(id)
        toast("食堂及其记录已删除")
    }

    /** 种草 / 取消种草（W5 详情操作） */
    fun setWish(placeId: String, wished: Boolean) = viewModelScope.launch {
        val place = repo.data.value.places.firstOrNull { it.id == placeId } ?: return@launch
        repo.upsertPlace(place.copy(wishlistedAt = if (wished) System.currentTimeMillis() else null))
        toast(if (wished) "已种草 🌟" else "已取消种草")
    }

    /** 安排到某天 / 清除安排（W5 详情操作，it-008 阶段C） */
    fun setPlan(placeId: String, planAt: Long?) = viewModelScope.launch {
        val place = repo.data.value.places.firstOrNull { it.id == placeId } ?: return@launch
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
        viewModelScope.launch {
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
                    viewModelScope.launch {
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

    fun deleteVisit(id: String) = viewModelScope.launch {
        repo.deleteVisit(id)
        toast("已删除这条记录")
    }

    // ---- 统计回顾（it-007） ----

    /** 回忆提醒设置（DataStore，跨启动保留） */
    val recapPrefs: StateFlow<RecapReminderPrefs> = container.recapPrefs.flow
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

    /** 应用启动时对齐提醒任务与开关（兜底重启/升级；演示模式恒取消） */
    fun syncReminderSchedule() {
        viewModelScope.launch {
            val prefs = container.recapPrefs.snapshot()
            ReminderScheduler.sync(getApplication(), prefs.enabled && !container.isDemo)
        }
    }

    fun generateRecap(range: RecapRange, now: Long, onReady: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            val stats = repo.data.value.recap(range, now)
            if (!stats.hasData) {
                onReady(null)
                return@launch
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
}
