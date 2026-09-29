package com.leo.darkroom.develop

/**
 * 显影时钟：毫秒制的确定性进度累积器（纯 Kotlin 可单测）。
 * 播放 = tick 推进；甩一甩 = boost 按比例折算时间快进；药水条 = seek 直接定位。
 */
class DevelopClock(private val durationMs: Long) {

    init {
        require(durationMs > 0) { "durationMs must be positive" }
    }

    private var valueMs: Double = 0.0

    /** 当前显影进度 0..1 */
    val progress: Float
        get() = (valueMs / durationMs).toFloat().coerceIn(0f, 1f)

    val isFixed: Boolean
        get() = progress >= 1f

    /** 每帧推进（dt 为真实帧间隔；系统动画缩放由调用方先行折算） */
    fun tick(dtMs: Long) {
        if (dtMs <= 0) return
        valueMs = (valueMs + dtMs).coerceAtMost(durationMs.toDouble())
    }

    /** 甩一甩：进度快进一小段（fraction = 本次甩动的进度增益 0..1） */
    fun boost(fraction: Float) {
        if (fraction <= 0f) return
        valueMs = (valueMs + fraction.toDouble() * durationMs).coerceAtMost(durationMs.toDouble())
    }

    /** 药水条拖动：直接定位进度 */
    fun seek(fraction: Float) {
        valueMs = fraction.coerceIn(0f, 1f).toDouble() * durationMs
    }

    fun reset() {
        valueMs = 0.0
    }
}

/** 显影速度档位（it-001 US-2：慢洗 / 标准 / 快显；it-010 收尾整体 ×⅔ 提速） */
enum class DevelopSpeed(val durationMs: Long, val label: String) {
    SLOW(8_000L, "慢洗"),
    STANDARD(5_400L, "标准"),
    FAST(2_700L, "快显");

    companion object {
        fun fromOrDefault(name: String?): DevelopSpeed =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
