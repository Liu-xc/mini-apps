package com.leo.darkroom.export

/**
 * 视频导出的时间线（纯 Kotlin 可单测，it-001 US-6）：
 * 0.5s 空白相纸起手 → 三阶段显影全程 → 定影定格 hold 收尾。
 */
data class ExportPlan(
    val fps: Int = 30,
    val leadMs: Long = 500L,
    val developMs: Long,
    val holdMs: Long = 1_500L,
) {
    init {
        require(fps > 0 && developMs > 0) { "fps/developMs must be positive" }
    }

    val totalMs: Long get() = leadMs + developMs + holdMs

    val frameCount: Int get() = ((totalMs * fps + 999) / 1000).toInt()

    data class FrameTiming(val index: Int, val timeMs: Long, val developProgress: Float)

    /** 第 index 帧的时刻与显影进度（index 超界返回最后一帧） */
    fun frameAt(index: Int): FrameTiming {
        val i = index.coerceIn(0, frameCount - 1)
        val t = i * 1000L / fps
        val progress = when {
            t <= leadMs -> 0f
            t >= leadMs + developMs -> 1f
            else -> (t - leadMs).toFloat() / developMs
        }
        return FrameTiming(i, t, progress.coerceIn(0f, 1f))
    }
}
