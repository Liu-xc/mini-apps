package com.leo.libs.sync

/**
 * 后端中立错误分类：各后端把自家错误码折叠进这些类别，
 * [recoverHint] 携带面向用户的恢复动作文案（设置页排错 UX 的依据）。
 */
sealed interface SyncError {

    /** 网络不可达 / 超时 → 可重试 */
    data class Network(val detail: String, val cause: Throwable? = null) : SyncError {
        override val recoverHint = "网络不可用，稍后自动重试"
    }

    /** 触发限频 → 按指示退避后重试 */
    data class RateLimited(val resetAfterSec: Long, val detail: String = "") : SyncError {
        override val recoverHint = "操作过于频繁，${resetAfterSec}s 后自动重试"
    }

    /** 凭证失效（secret 被重置/配置错误）→ 引导重新扫码或手填 */
    data class AuthFailed(val detail: String) : SyncError {
        override val recoverHint = "连接凭证已失效，请重新扫码或检查 App ID/Secret"
    }

    /** 平台侧权限不足（未挂协作者、应用未发版等）→ 按平台指引操作 */
    data class PermissionDenied(val detail: String) : SyncError {
        override val recoverHint = detail
    }

    /** 云端容量/配额超限 */
    data class QuotaExceeded(val detail: String) : SyncError {
        override val recoverHint = "云端存储已满，请清理或升级容量"
    }

    /** 记录映射/校验失败 → 进隔离区，不阻塞其余同步 */
    data class InvalidRecord(val collection: String, val recordId: String?, val detail: String) : SyncError {
        override val recoverHint = "个别云端记录无法识别，已放入隔离区"
    }

    data class Unknown(val httpCode: Int, val code: Int? = null, val detail: String = "") : SyncError {
        override val recoverHint = "未知错误（$httpCode${code?.let { "/$it" } ?: ""}），请稍后重试"
    }

    val recoverHint: String
}

/** 统一抛出通道：后端实现把 [SyncError] 包装成此异常抛出，引擎捕获后进入 Failed 状态 */
class SyncException(val error: SyncError) : Exception(error.toString())
