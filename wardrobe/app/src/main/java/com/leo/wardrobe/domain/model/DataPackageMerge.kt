package com.leo.wardrobe.domain.model

/**
 * 数据包合并（it-024 / it-012 通用语义，wardrobe 分册）：
 * 七个集合各自按 id 合并——包有本地无 → 新增；包有本地有 → 包内实体整体覆盖（AI 打标回写
 * 的语义是「该实体的新版本」，不做字段级深合并）；本地有包无 → 保留不动（合并永不删数据）。
 */
enum class ImportMode { MERGE, REPLACE }

/** 单集合差异：新增 / 更新（内容不同且将被覆盖）/ 不变（内容相同）/ 本地独有（保留） */
data class CollectionDiff(val added: Int, val updated: Int, val unchanged: Int, val localOnly: Int) {
    val kept: Int get() = unchanged + localOnly
    val totalAfter: Int get() = added + updated + unchanged + localOnly
}

data class WardrobeDiff(
    val persons: CollectionDiff,
    val items: CollectionDiff,
    val outfits: CollectionDiff,
    val notes: CollectionDiff,
    val wearLogs: CollectionDiff,
    val wishItems: CollectionDiff,
    val wishOutfits: CollectionDiff,
    /** 将被覆盖、且本地 updatedAt 晚于包导出时间的实体数（预览屏警示行，D4） */
    val locallyNewer: Int,
) {
    val addedTotal: Int get() = persons.added + items.added + outfits.added + notes.added + wearLogs.added + wishItems.added + wishOutfits.added
    val updatedTotal: Int get() = persons.updated + items.updated + outfits.updated + notes.updated + wearLogs.updated + wishItems.updated + wishOutfits.updated
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
    // 本地顺序保留，包内新实体按包内顺序追加
    return local.map { incomingById[id(it)] ?: it } + incoming.filter { id(it) !in localIds }
}

fun diffWardrobe(local: WardrobeData, incoming: WardrobeData, exportedAt: Long): WardrobeDiff {
    val localItems = local.items.associateBy { it.id }
    val localOutfits = local.outfits.associateBy { it.id }
    val localWishItems = local.wishItems.associateBy { it.id }
    val localWishOutfits = local.wishOutfits.associateBy { it.id }
    val newerCount = listOf(
        incoming.items.count { i -> val l = localItems[i.id]; l != null && l != i && l.updatedAt > exportedAt },
        incoming.outfits.count { o -> val l = localOutfits[o.id]; l != null && l != o && l.updatedAt > exportedAt },
        incoming.wishItems.count { w -> val l = localWishItems[w.id]; l != null && l != w && l.updatedAt > exportedAt },
        incoming.wishOutfits.count { w -> val l = localWishOutfits[w.id]; l != null && l != w && l.updatedAt > exportedAt },
    ).sum()
    return WardrobeDiff(
        persons = diffOf(local.persons, incoming.persons) { it.id },
        items = diffOf(local.items, incoming.items) { it.id },
        outfits = diffOf(local.outfits, incoming.outfits) { it.id },
        notes = diffOf(local.notes, incoming.notes) { it.id },
        wearLogs = diffOf(local.wearLogs, incoming.wearLogs) { it.id },
        wishItems = diffOf(local.wishItems, incoming.wishItems) { it.id },
        wishOutfits = diffOf(local.wishOutfits, incoming.wishOutfits) { it.id },
        locallyNewer = newerCount,
    )
}

fun mergeWardrobe(local: WardrobeData, incoming: WardrobeData): WardrobeData = WardrobeData(
    schemaVersion = maxOf(local.schemaVersion, incoming.schemaVersion),
    persons = mergeBy(local.persons, incoming.persons) { it.id },
    items = mergeBy(local.items, incoming.items) { it.id },
    outfits = mergeBy(local.outfits, incoming.outfits) { it.id },
    notes = mergeBy(local.notes, incoming.notes) { it.id },
    wearLogs = mergeBy(local.wearLogs, incoming.wearLogs) { it.id },
    wishItems = mergeBy(local.wishItems, incoming.wishItems) { it.id },
    wishOutfits = mergeBy(local.wishOutfits, incoming.wishOutfits) { it.id },
)

/** 数据快照引用的全部图片文件名（it-024：导出收集 / 导入重映射 / 回收判定共用） */
fun WardrobeData.referencedImages(): Set<String> = buildSet {
    persons.forEach { p -> p.refImageFile?.let { add(it) } }
    items.forEach { add(it.imageFile) }
    outfits.forEach { o -> o.effectImages.forEach { add(it.file) } }
    wishItems.forEach { w -> w.imageFile?.let { add(it) } }
    wishOutfits.forEach { w -> w.previewImages.forEach { add(it.file) } }
}

/** 按映射重写全部图片引用（导入归一化：包内名 → 落盘后的新 uuid.webp） */
fun WardrobeData.remapImageRefs(map: (String) -> String): WardrobeData = copy(
    persons = persons.map { p -> p.refImageFile?.let { p.copy(refImageFile = map(it)) } ?: p },
    items = items.map { it.copy(imageFile = map(it.imageFile)) },
    outfits = outfits.map { o -> o.copy(effectImages = o.effectImages.map { it.copy(file = map(it.file)) }) },
    wishItems = wishItems.map { w -> w.imageFile?.let { w.copy(imageFile = map(it)) } ?: w },
    wishOutfits = wishOutfits.map { w -> w.copy(previewImages = w.previewImages.map { it.copy(file = map(it.file)) }) },
)
