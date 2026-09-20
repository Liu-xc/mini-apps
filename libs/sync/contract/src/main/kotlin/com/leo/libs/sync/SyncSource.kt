package com.leo.libs.sync

/**
 * 云端数据源接口（契约 ports）：飞书多维表格是首个实现，未来 LeanCloud/WebDAV 等同级接入。
 * 后端私有能力（如多维表格的 record_id、串行写）不得泄漏出实现边界。
 */
interface SyncSource {

    /** 对应 SyncConfig.backend，如 "feishu-bitable" */
    val backendId: String

    /**
     * 测试连通性并确保集合结构就绪（多维表格：换 token → 列表 → 自动建缺失的表/补列）。
     * 失败抛 [SyncException]（如 AuthFailed / PermissionDenied）。
     */
    suspend fun connect(config: SyncConfig, collections: List<SyncCollection>)

    /**
     * 拉取一个集合（v1 全量语义：返回云端全部实体，引擎负责对账 diff）。
     */
    suspend fun pull(collection: SyncCollection): PullResult

    /**
     * 推送一批操作：upserts 全量行覆盖（记录级 LWW），removes 物理删除。
     * 返回按 entityId 的结果（失败的不计入 applied，由引擎保留在待推队列）。
     */
    suspend fun push(collection: SyncCollection, upserts: List<SyncEntity>, removes: List<String>): PushResult

    /** 上传附件字节，返回真实引用（file_token / 文件 URL） */
    suspend fun putAttachment(name: String, bytes: ByteArray): SyncValue.Attachment

    /** 下载附件字节 */
    suspend fun fetchAttachment(ref: String): ByteArray
}

data class PullResult(val entities: List<SyncEntity>)

data class PushResult(
    val applied: List<String>,
    val failed: Map<String, SyncError>,
)
