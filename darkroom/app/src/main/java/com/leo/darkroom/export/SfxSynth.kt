package com.leo.darkroom.export

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 视频音轨合成器（纯 Kotlin DSP 可单测）。
 *
 * 分工声明（it-001 ADR-002，DESIGN.md §4 治理）：App 内交互**不加音效**（个人工具公共场合基线）；
 * 声音只存在于**导出的视频内容物**中——视频发到社交平台时，声音是内容而非 UI 音效。
 *
 * 音轨结构（与 ExportPlan 对齐）：出纸摩擦（t=0）→ 药水底噪（显影全程渐弱）→ 定影「嗒」（developEnd）。
 * 确定性：固定种子 LCG 白噪，同参数合成完全一致。
 */
object SfxSynth {

    const val SAMPLE_RATE = 44_100

    data class Track(val pcm: ShortArray, val sampleRate: Int = SAMPLE_RATE) {
        val durationMs: Long get() = pcm.size * 1000L / sampleRate
    }

    fun buildDevelopTrack(totalMs: Long, developEndMs: Long, seed: Long = 2026_0927L): Track {
        val n = (totalMs * SAMPLE_RATE / 1000L).toInt()
        val pcm = ShortArray(n)
        var rng = seed or 1L
        var brown = 0.0

        val endSample = (developEndMs.coerceIn(0, totalMs) * SAMPLE_RATE / 1000L).toInt()

        for (i in 0 until n) {
            val tMs = i * 1000.0 / SAMPLE_RATE

            // LCG 白噪 (-1..1)：ushr 32 → 32 位 [0,2^32) / 2^31 = [0,2) → -1 = [-1,1) 零均值
            rng = rng * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            val white = ((rng ushr 32).toDouble() / (1L shl 31)) - 1.0

            // 药水底噪：布朗噪（积分白噪）+ 出纸 1s 内的额外高频摩擦
            brown = (brown + 0.02 * white) * 0.995
            val developGain = if (tMs <= developEndMs) 1.0 - 0.55 * (tMs / developEndMs.coerceAtLeast(1)) else 0.25
            val bed = brown * 1.1 * developGain

            val paper = if (tMs < 900) (white * 0.25 + brown * 0.6) * exp(-tMs / 320.0) else 0.0

            // 定影「嗒」：短促正弦衰减 + 噪声瞬态
            val sinceTick = tMs - developEndMs
            val tick = if (sinceTick in 0.0..180.0) {
                val env = exp(-sinceTick / 45.0)
                sin(2 * PI * 1320.0 * sinceTick / 1000.0) * 0.9 * env + white * 0.15 * env
            } else 0.0

            val mixed = bed + paper + tick
            // 软限幅：渐近线 1.0（旧公式渐近线 1/0.35≈2.86 会削顶成直流，RMS 恒 32768）
            val limited = mixed / (1.0 + kotlin.math.abs(mixed))
            pcm[i] = (limited * Short.MAX_VALUE * 0.9).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Track(pcm)
    }

    /** 区间 RMS（测试用：验证事件能量分布） */
    fun rms(track: Track, fromMs: Long, toMs: Long): Double {
        val from = (fromMs.coerceAtLeast(0) * SAMPLE_RATE / 1000L).toInt()
        val to = min((toMs * SAMPLE_RATE / 1000L).toInt(), track.pcm.size)
        if (to <= from) return 0.0
        var acc = 0.0
        for (i in from until to) {
            val v = track.pcm[i].toDouble()
            acc += v * v
        }
        return kotlin.math.sqrt(acc / (to - from))
    }
}
