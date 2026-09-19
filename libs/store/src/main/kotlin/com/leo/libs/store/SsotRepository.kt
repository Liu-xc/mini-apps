package com.leo.libs.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SSOT 仓库基类（ADR-008 语义的通用化）：
 * 内存快照为唯一数据源；写操作「改快照 → 原子落盘 → 广播」，
 * 落盘失败时内存快照保持旧值并向上抛出（回滚由不赋值天然成立）。
 *
 * [writeHook] 是与 sync SDK 组合的唯一缝：本地提交成功后回调，
 * 供 app 在此登记云端待推操作（先本地后云端的顺序由此保证）。
 */
abstract class SsotRepository<T : Any>(
    val store: SnapshotStore<T>,
    onLoad: (T) -> T = { it },
) {

    private val _data = MutableStateFlow(onLoad(store.load()))
    val data: StateFlow<T> = _data.asStateFlow()

    private val mutex = Mutex()

    var writeHook: (suspend (T) -> Unit)? = null

    protected suspend fun mutate(transform: (T) -> T): T {
        val next = mutex.withLock {
            val result = transform(_data.value)
            store.commit(result)
            _data.value = result
            result
        }
        writeHook?.invoke(next)
        return next
    }
}
