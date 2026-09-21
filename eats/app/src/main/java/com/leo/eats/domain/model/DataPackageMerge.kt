package com.leo.eats.domain.model

/**
 * 数据包合并（it-024 / it-012 通用语义，eats 分册）：
 * places / visits 各自按 id 合并——包有本地无 → 新增；包有本地有 → 包内实体整体覆盖；
 * 本地有包无 → 保留不动（合并永不删数据）。
 */
enum class ImportMode { MERGE, REPLACE }

/** 单集合差异：新增 / 更新（内容不同且将被覆盖）/ 不变（内容相同）/ 本地独有（保留） */
data class CollectionDiff(val added: Int, val updated: Int, val unchanged: Int, val localOnly: Int) {
    val kept: Int get() = unchanged + localOnly
    val totalAfter: Int get() = added + updated + unchanged + localOnly
}

data class EatsDiff(
    val places: CollectionDiff,
    val visits: CollectionDiff,
    /** 将被覆盖、且本地 updatedAt 晚于包导出时间的 Place 数（预览屏警示行，D4） */
    val locallyNewer: Int,
) {
    val addedTotal: Int get() = places.added + visits.added
    val updatedTotal: Int get() = places.updated + visits.updated
}

private fun <T> diffOf(local: List<T>, incoming: List<T>, id: (T) -> String): CollectionDiff {
    val localById = local.associateBy(id)
    val incomingIds = incoming.mapTo(HashSet()) { id(it) }
    return CollectionDiff(
        added = incoming.count { id(it) !in localById },
        updated = incoming.count { i -> localById[id(i)]?.let { it != i } == true },
        unchanged = incoming.count { i -> localById[id(i)] == i },
        localOnly = local.count { id(it) !in incomingIds },
    )
}

private fun <T> mergeBy(local: List<T>, incoming: List<T>, id: (T) -> String): List<T> {
    val incomingById = incoming.associateBy(id)
    val localIds = local.mapTo(HashSet()) { id(it) }
    return local.map { incomingById[id(it)] ?: it } + incoming.filter { id(it) !in localIds }
}

fun diffEats(local: EatsData, incoming: EatsData, exportedAt: Long): EatsDiff {
    val localPlaces = local.places.associateBy { it.id }
    return EatsDiff(
        places = diffOf(local.places, incoming.places) { it.id },
        visits = diffOf(local.visits, incoming.visits) { it.id },
        locallyNewer = incoming.places.count { p ->
            val l = localPlaces[p.id]; l != null && l != p && l.updatedAt > exportedAt
        },
    )
}

fun mergeEats(local: EatsData, incoming: EatsData): EatsData = EatsData(
    schemaVersion = maxOf(local.schemaVersion, incoming.schemaVersion),
    places = mergeBy(local.places, incoming.places) { it.id },
    visits = mergeBy(local.visits, incoming.visits) { it.id },
)

/** 数据快照引用的全部图片文件名（it-012：导出收集 / 导入重映射 / 回收判定共用） */
fun EatsData.referencedImages(): Set<String> = buildSet {
    places.forEach { p -> p.photos.forEach { add(it) } }
    visits.forEach { v -> v.photos.forEach { add(it) } }
}

/** 按映射重写全部图片引用（导入归一化：包内名 → 落盘后的新 uuid.webp） */
fun EatsData.remapImageRefs(map: (String) -> String): EatsData = copy(
    places = places.map { p -> p.copy(photos = p.photos.map { map(it) }) },
    visits = visits.map { v -> v.copy(photos = v.photos.map { map(it) }) },
)

/**
 * it-012 eats 特有校验（预检 + skill validator 同规则）：
 * PLAY+TAKEOUT 无效组合 / rating 越界 / links 空 url。返回用户可读的问题清单（空 = 通过）。
 */
fun validateEatsData(data: EatsData): List<String> = buildList {
    data.places.forEach { p ->
        if (p.category == PlaceCategory.PLAY && p.kind == PlaceKind.TAKEOUT) {
            add("「${p.name}」：玩 + 外送是无效组合（玩只有出门/在家）")
        }
        if (p.rating != null && p.rating !in 1..5) {
            add("「${p.name}」：评分 ${p.rating} 越界（应为 1–5 或不填）")
        }
        if (p.links.any { it.url.isBlank() }) {
            add("「${p.name}」：存在空链接 url")
        }
    }
}
