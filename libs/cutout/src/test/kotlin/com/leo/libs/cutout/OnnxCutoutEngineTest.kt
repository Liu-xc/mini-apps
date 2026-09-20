package com.leo.libs.cutout

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class OnnxCutoutEngineTest {

    /** 记录并发与生命周期的假推理器（不碰 onnxruntime）。 */
    private class FakeInferencer : SaliencyInferencer {
        val inferCount = AtomicInteger()
        val inflight = AtomicInteger()
        val maxInflight = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun infer(input: FloatArray): FloatArray {
            inferCount.incrementAndGet()
            val now = inflight.incrementAndGet()
            maxInflight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                Thread.sleep(10)
            } finally {
                inflight.decrementAndGet()
            }
            return FloatArray(U2NetPreprocessor.INPUT_SIDE * U2NetPreprocessor.INPUT_SIDE) { it / 102400f }
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private fun rgba(w: Int, h: Int) = ByteArray(w * h * 4) { 100 }

    private fun TestScope.engine(fake: SaliencyInferencer, idle: Duration = Duration.INFINITE): OnnxCutoutEngine =
        OnnxCutoutEngine(
            modelBytes = { ByteArray(0) },
            idleTimeout = idle,
            inferenceContext = StandardTestDispatcher(testScheduler),
            inferencerFactory = { fake },
        )

    @Test
    fun `cutout without prepare auto-initializes`() = runTest {
        val fake = FakeInferencer()
        val engine = engine(fake)
        assertEquals(CutoutReadiness.Idle, engine.readiness.value)
        val out = engine.cutout(rgba(4, 4), 4, 4)
        assertEquals(CutoutReadiness.Ready, engine.readiness.value)
        assertEquals(1, fake.inferCount.get())
        assertEquals(16 * 4, out.size)
    }

    @Test
    fun `bad input throws without creating session`() = runTest {
        val fake = FakeInferencer()
        val engine = engine(fake)
        var thrown: Throwable? = null
        try {
            engine.cutout(ByteArray(3), 1, 1)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("expect BadInput, got $thrown", thrown is CutoutException.BadInput)
        assertEquals(0, fake.inferCount.get())
        assertEquals(CutoutReadiness.Idle, engine.readiness.value)
    }

    @Test
    fun `failed initialization marks Failed and cutout retries`() = runTest {
        var failLoad = true
        val engine = OnnxCutoutEngine(
            modelBytes = { ByteArray(0) },
            idleTimeout = Duration.INFINITE,
            inferenceContext = StandardTestDispatcher(testScheduler),
            inferencerFactory = {
                if (failLoad) throw IllegalStateException("load boom") else FakeInferencer()
            },
        )
        var thrown: Throwable? = null
        try {
            engine.cutout(rgba(2, 2), 2, 2)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("expect InferFailed, got $thrown", thrown is CutoutException.InferFailed)
        assertTrue(engine.readiness.value is CutoutReadiness.Failed)
        failLoad = false
        engine.cutout(rgba(2, 2), 2, 2)
        assertEquals(CutoutReadiness.Ready, engine.readiness.value)
    }

    @Test
    fun `concurrent cutouts serialize on single session`() = runTest {
        val fake = FakeInferencer()
        val engine = engine(fake)
        val jobs = (1..3).map { async { engine.cutout(rgba(2, 2), 2, 2) } }
        jobs.awaitAll()
        assertEquals(3, fake.inferCount.get())
        assertEquals("inference must not overlap", 1, fake.maxInflight.get())
    }

    @Test
    fun `idle timeout releases session and next cutout re-creates`() = runTest {
        val fake = FakeInferencer()
        val engine = engine(fake, idle = 100.milliseconds)
        engine.prepare()
        assertEquals(CutoutReadiness.Ready, engine.readiness.value)
        runCurrent()
        advanceTimeBy(150.milliseconds)
        runCurrent()
        assertEquals(1, fake.closeCount.get())
        assertEquals(CutoutReadiness.Idle, engine.readiness.value)
        engine.cutout(rgba(2, 2), 2, 2)
        assertEquals(CutoutReadiness.Ready, engine.readiness.value)
        assertEquals(1, fake.closeCount.get())
    }

    @Test
    fun `release closes idempotently and cutout re-initializes`() = runTest {
        val fake = FakeInferencer()
        val engine = engine(fake)
        engine.prepare()
        engine.release()
        assertEquals(1, fake.closeCount.get())
        assertEquals(CutoutReadiness.Idle, engine.readiness.value)
        engine.release()
        assertEquals(1, fake.closeCount.get())
        engine.cutout(rgba(2, 2), 2, 2)
        assertEquals(CutoutReadiness.Ready, engine.readiness.value)
    }
}
