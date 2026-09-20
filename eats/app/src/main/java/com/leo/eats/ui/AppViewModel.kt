package com.leo.eats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.eats.EatsApp
import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.PlaceWithStats
import com.leo.eats.domain.model.Visit
import com.leo.eats.domain.model.newId
import com.leo.eats.domain.model.statsOfAll
import com.leo.eats.domain.usecase.SpinFilter
import com.leo.eats.platform.LinkOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun toast(msg: String?) { _toast.value = msg }

    val data: StateFlow<EatsData> = repo.data

    /** 转盘过滤配置（跨启动保留，specs/04 状态与导航） */
    val spinConfig: StateFlow<SpinFilter> = spinPrefs.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpinFilter())

    fun setSpinKinds(kinds: Set<PlaceKind>) {
        viewModelScope.launch { spinPrefs.setKinds(kinds) }
    }

    fun setSpinExcludedTags(tags: Set<String>) {
        viewModelScope.launch { spinPrefs.setExcludedTags(tags) }
    }

    fun setSpinExcludeRecent(enabled: Boolean, days: Int) {
        viewModelScope.launch { spinPrefs.setExcludeRecent(enabled, days) }
    }

    /** 当前候选（含统计），供转盘页派生 */
    fun candidatesOf(filter: SpinFilter): List<PlaceWithStats> =
        buildCandidates(repo.data.value.statsOfAll(), filter, System.currentTimeMillis())

    // ---- Place ----

    fun savePlace(
        existing: Place?,
        name: String,
        kind: PlaceKind,
        cuisine: String,
        location: GeoLoc?,
        address: String,
        rating: Int?,
        tags: List<String>,
        keptPhotos: List<String>,
        newPhotoUris: List<String>,
        links: List<PlaceLink>,
        notes: String,
        onDone: (Boolean) -> Unit,
    ) {
        if (name.isBlank()) { toast("名称必填"); onDone(false); return }
        viewModelScope.launch {
            val imported = newPhotoUris.mapNotNull { container.imageStore.importFromUri(it) }
            val p = Place(
                id = existing?.id ?: newId(),
                name = name.trim(),
                kind = kind,
                cuisine = cuisine.trim(),
                location = location,
                address = address.trim(),
                rating = rating?.coerceIn(1, 5),
                tags = tags.distinct().take(10),
                photos = keptPhotos + imported,
                links = links,
                notes = notes.trim(),
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
            toast("落账 ✓")
            onDone(true)
        }
    }

    fun deleteVisit(id: String) = viewModelScope.launch {
        repo.deleteVisit(id)
        toast("已删除这条记录")
    }

    // ---- 地图聚焦意图（W5「在地图上看」） ----

    private val _pendingMapFocus = MutableStateFlow<String?>(null)
    val pendingMapFocus: StateFlow<String?> = _pendingMapFocus.asStateFlow()

    fun focusOnMap(placeId: String) { _pendingMapFocus.value = placeId }

    fun consumeMapFocus() { _pendingMapFocus.value = null }
}
