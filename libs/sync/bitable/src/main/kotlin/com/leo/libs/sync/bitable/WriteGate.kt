package com.leo.libs.sync.bitable

import com.leo.libs.sync.SyncError
import com.leo.libs.sync.SyncException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 写门闩：官方建议「对单个多维表格同时只发一次写操作」→ 全部写经此串行；
 * 429/限频按 x-ogw-ratelimit-reset 指示退避重试。
 * [delayFn] 注入以便单测虚拟时间。
 */
class WriteGate(
    private val maxAttempts: Int = 5,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()

    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock {
        var attempt = 0
        while (true) {
            try {
                return@withLock block()
            } catch (e: SyncException) {
                val limited = e.error as? SyncError.RateLimited ?: throw e
                attempt++
                if (attempt >= maxAttempts) throw e
                delayFn(limited.resetAfterSec.coerceAtLeast(1) + 1)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}
