package com.leo.libs.sync

/**
 * 集合适配器（app 侧实现）：引擎与 app 本地存储之间的唯一桥。
 * 引擎不持有 app 存储——落地永远在 app 侧（架构 §3.3「引擎不持有 App 存储」）。
 */
interface CollectionAdapter {

    val collection: SyncCollection

    // ---- pull 侧 ----

    /** 当前本地实体 id 集（删除对账用；应包含本地新建未推的实体） */
    suspend fun localIds(): Set<String>

    /** 本地版本判据（LWW：与云端 updatedAt 比较）；本地不存在返回 null */
    suspend fun localUpdatedAt(id: String): Long?

    /**
     * 应用云端增量。返回无法映射进本地结构的实体（隔离区），引擎汇总进 PullSummary。
     * 实现应自行把 SyncEntity 映射为领域实体后写入本地存储。
     */
    suspend fun applyCloud(upserts: List<SyncEntity>, deletes: List<String>): List<Rejected>

    // ---- push 侧 ----

    /**
     * 为待推实体解析附件：返回 fieldName → 字节的挂起附件。
     * 引擎逐个经 [SyncSource.putAttachment] 上传后回写真实 ref。
     */
    suspend fun attachmentsFor(entity: SyncEntity): List<PendingAttachment>

    /** 一批实体成功推送（含真实附件 ref）后回调；app 可借此持久化 ref（本地缓存正本化） */
    suspend fun onPushed(entities: List<SyncEntity>)
}

data class PendingAttachment(
    val fieldName: String,
    val fileName: String,
    val bytes: suspend () -> ByteArray,
)

data class Rejected(val id: String?, val reason: String)
