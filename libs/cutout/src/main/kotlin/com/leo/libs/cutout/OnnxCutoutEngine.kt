package com.leo.libs.cutout

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [CutoutEngine] 的 ONNX u2netp 实现（specs/00-architecture.md §5）：
 * - 会话惰性：首次 prepare()/cutout() 经 [inferencerFactory] 创建；[CutoutReadiness.Failed] 后重试即重建；
 * - Mutex 串行推理（单 session 不并发）；
 * - 空闲自动释放：最后一次活动后 [idleTimeout]（≤0 表示不自动释放）即 close 会话，readiness 回 Idle；
 * - 推理在 [inferenceContext] 上执行，默认独立单线程池，长任务不占公共调度池。
 */
class OnnxCutoutEngine(
    private val modelBytes: suspend () -> ByteArray,
    private val idleTimeout: Duration = 5.minutes,
    private val inferenceContext: CoroutineContext = defaultInferenceContext(),
    private val inferencerFactory: (ByteArray) -> SaliencyInferencer = ::OnnxSaliencyInferencer,
) : CutoutEngine {

    private val scope = CoroutineScope(SupervisorJob() + inferenceContext)
    private val mutex = Mutex()
    private val _readiness = MutableStateFlow<CutoutReadiness>(CutoutReadiness.Idle)
    override val readiness: StateFlow<CutoutReadiness> = _readiness.asStateFlow()

    private val idleLock = Any()
    private var idleJob: Job? = null

    @Volatile
    private var session: SaliencyInferencer? = null

    override suspend fun prepare() {
        mutex.withLock {
            ensureSessionLocked()
            scheduleIdleRelease()
        }
    }

    override suspend fun cutout(rgba: ByteArray, width: Int, height: Int): ByteArray {
        if (width <= 0 || height <= 0) throw CutoutException.BadInput("bad size ${width}x$height")
        if (rgba.size != width * height * 4) {
            throw CutoutException.BadInput("rgba size ${rgba.size} != ${width}x$height x4")
        }
        val mask = mutex.withLock {
            val s = ensureSessionLocked()
            try {
                withContext(inferenceContext) {
                    s.infer(U2NetPreprocessor.preprocess(rgba, width, height))
                }
            } catch (t: Throwable) {
                if (t is CutoutException) throw t
                throw CutoutException.InferFailed(t)
            }
        }
        MaskToAlpha.apply(mask, rgba, width, height)
        return rgba
    }

    override suspend fun release() {
        cancelIdleRelease()
        mutex.withLock { closeSessionLocked() }
    }

    /** 调用方持锁。 */
    private suspend fun ensureSessionLocked(): SaliencyInferencer {
        session?.let { return it }
        _readiness.value = CutoutReadiness.Preparing
        try {
            val bytes = modelBytes()
            val s = withContext(inferenceContext) { inferencerFactory(bytes) }
            session = s
            _readiness.value = CutoutReadiness.Ready
            return s
        } catch (t: Throwable) {
            _readiness.value = CutoutReadiness.Failed(t)
            throw CutoutException.InferFailed(t)
        }
    }

    /** 调用方持锁。Failed 态不被覆盖（重试语义归 ensureSessionLocked）。 */
    private fun closeSessionLocked() {
        val s = session
        session = null
        try {
            s?.close()
        } catch (_: Throwable) {
        }
        if (_readiness.value is CutoutReadiness.Ready) {
            _readiness.value = CutoutReadiness.Idle
        }
    }

    private fun scheduleIdleRelease() {
        if (idleTimeout == Duration.ZERO || idleTimeout < Duration.ZERO) return
        synchronized(idleLock) {
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(idleTimeout)
                mutex.withLock { closeSessionLocked() }
            }
        }
    }

    private fun cancelIdleRelease() {
        synchronized(idleLock) {
            idleJob?.cancel()
            idleJob = null
        }
    }

    companion object {
        /** 默认推理上下文：独立单线程守护线程，避免长任务占用公共调度池。 */
        fun defaultInferenceContext(): CoroutineContext =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "cutout-inference").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }
}
