package com.leo.eats.data.repo

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.Visit
import com.leo.eats.domain.model.newId
import com.leo.eats.domain.repository.EatsRepository
import com.leo.eats.domain.repository.ImageStore
import com.leo.libs.store.SnapshotStore
import com.leo.libs.store.SsotRepository

/**
 * SSOT 实现（与 wardrobe 同模式）：持久化与广播机制由 store SDK 的 [SsotRepository] 承担
 * （内存快照 + 写操作「改快照→原子落盘→广播」）；不变量见 specs/03-data-model.md。
 */
class EatsRepositoryImpl(
    store: SnapshotStore<EatsData>,
    private val images: ImageStore,
) : SsotRepository<EatsData>(store, onLoad = { it.cleaned() }), EatsRepository {

    /** 测试可注入的时钟 */
    var now: () -> Long = { System.currentTimeMillis() }

    /** it-012 数据包导入终步：清洗后整体替换（单事务：改快照→落盘→广播） */
    override suspend fun replaceAll(data: EatsData) {
        mutate { data.cleaned() }
    }

    override suspend fun upsertPlace(place: Place) {
        val old = data.value.places.firstOrNull { it.id == place.id }
        val fixed = place.copy(
            name = place.name.trim(),
            tags = place.tags.distinct().take(10),
            links = place.links
                .map { it.copy(url = it.url.trim()) }
                .filter { it.url.isNotBlank() }
                .distinctBy { it.url },
        ).let { if (old != null) it.copy(updatedAt = now()) else it.copy(createdAt = now(), updatedAt = now()) }
        // 被移出列表的旧照片，落盘成功后物理删除
        val removedPhotos = old?.photos.orEmpty().filterNot { it in fixed.photos }
        mutate { d ->
            d.copy(places = if (old != null) d.places.map { if (it.id == fixed.id) fixed else it } else d.places + fixed)
        }
        removedPhotos.forEach { images.delete(it) }
    }

    override suspend fun deletePlace(id: String) {
        val snapshot = data.value
        val place = snapshot.places.firstOrNull { it.id == id } ?: return
        val visitPhotos = snapshot.visits.filter { it.placeId == id }.flatMap { it.photos }
        mutate { d ->
            d.copy(
                places = d.places.filterNot { it.id == id },
                visits = d.visits.filterNot { it.placeId == id },
            )
        }
        (place.photos + visitPhotos).forEach { images.delete(it) }
    }

    override suspend fun addVisit(visit: Visit) {
        mutate { d ->
            val fixed = if (visit.id.isBlank()) visit.copy(id = newId(), createdAt = now()) else visit
            d.copy(visits = d.visits + fixed)
        }
    }

    override suspend fun deleteVisit(id: String) {
        val photos = data.value.visits.firstOrNull { it.id == id }?.photos.orEmpty()
        mutate { d -> d.copy(visits = d.visits.filterNot { it.id == id }) }
        photos.forEach { images.delete(it) }
    }
}

/** 载入时清洗悬空引用（不变量 1）：丢弃指向不存在食堂的 Visit、空链接 */
private fun EatsData.cleaned(): EatsData {
    val placeIds = places.map { it.id }.toSet()
    return copy(
        visits = visits.filter { it.placeId in placeIds },
        places = places.map { p ->
            p.copy(
                links = p.links.map { it.copy(url = it.url.trim()) }
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.url },
                tags = p.tags.distinct().take(10),
            )
        },
    )
}
