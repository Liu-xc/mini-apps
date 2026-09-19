package com.leo.eats.domain.model

/**
 * 派生统计（ADR-008）：lastVisitAt / visitCount / avgVisitRating 全部由 Visit 实时派生，不落盘。
 */
data class PlaceWithStats(
    val place: Place,
    val lastVisitAt: Long? = null,
    val visitCount: Int = 0,
    val avgVisitRating: Double? = null,
)

fun EatsData.placeById(id: String): Place? = places.firstOrNull { it.id == id }

/** 该食堂的 Visit 按吃饭时间倒序（W5 时间线） */
fun EatsData.visitsOf(placeId: String): List<Visit> =
    visits.filter { it.placeId == placeId }.sortedByDescending { it.at }

fun EatsData.statsOf(placeId: String): PlaceWithStats {
    val place = placeById(placeId) ?: return PlaceWithStats(Place(id = placeId, name = "", kind = PlaceKind.RESTAURANT))
    return statsFor(place)
}

/** 一次遍历算出全部食堂的派生统计（列表/地图/转盘共用） */
fun EatsData.statsOfAll(): List<PlaceWithStats> {
    val byPlace = visits.groupBy { it.placeId }
    return places.map { p -> statsFor(p, byPlace[p.id].orEmpty()) }
}

private fun EatsData.statsFor(place: Place, visits: List<Visit> = this.visits.filter { it.placeId == place.id }): PlaceWithStats {
    val rated = visits.mapNotNull { it.rating }
    return PlaceWithStats(
        place = place,
        lastVisitAt = visits.maxOfOrNull { it.at },
        visitCount = visits.size,
        avgVisitRating = rated.takeIf { it.isNotEmpty() }?.let { list -> list.sum().toDouble() / list.size },
    )
}
