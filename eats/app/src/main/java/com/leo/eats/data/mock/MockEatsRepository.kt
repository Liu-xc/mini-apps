package com.leo.eats.data.mock

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.Visit
import com.leo.eats.domain.model.newId
import com.leo.eats.domain.repository.EatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 演示仓库（it-006）：内存 SSOT，写操作只改内存、绝不落盘——演示模式下的增删
 * 在退出（重启进程）后自然消失，真实数据零接触。归一化规则与 EatsRepositoryImpl 保持一致。
 */
class MockEatsRepository(seed: EatsData = MockEatsData.create()) : EatsRepository {

    private val _data = MutableStateFlow(seed)
    override val data: StateFlow<EatsData> = _data.asStateFlow()

    /** it-012：演示模式下导入导出入口置灰，此实现仅保接口完整 */
    override suspend fun replaceAll(data: EatsData) { _data.value = data }

    override suspend fun upsertPlace(place: Place) {
        val old = _data.value.places.firstOrNull { it.id == place.id }
        val fixed = place.copy(
            name = place.name.trim(),
            tags = place.tags.distinct().take(10),
            links = place.links
                .map { it.copy(url = it.url.trim()) }
                .filter { it.url.isNotBlank() }
                .distinctBy { it.url },
        )
        _data.value = _data.value.copy(
            places = if (old != null) _data.value.places.map { if (it.id == fixed.id) fixed else it } else _data.value.places + fixed,
        )
    }

    override suspend fun deletePlace(id: String) {
        _data.value = _data.value.copy(
            places = _data.value.places.filterNot { it.id == id },
            visits = _data.value.visits.filterNot { it.placeId == id },
        )
    }

    override suspend fun addVisit(visit: Visit) {
        val fixed = if (visit.id.isBlank()) visit.copy(id = newId(), createdAt = System.currentTimeMillis()) else visit
        _data.value = _data.value.copy(visits = _data.value.visits + fixed)
    }

    override suspend fun deleteVisit(id: String) {
        _data.value = _data.value.copy(visits = _data.value.visits.filterNot { it.id == id })
    }
}
