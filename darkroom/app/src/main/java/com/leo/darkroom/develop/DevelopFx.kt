package com.leo.darkroom.develop

import kotlin.math.sin

/**
 * 显影过程动效参数（it-008）：全部是 (mode, progress) → 参数 的确定性纯函数，
 * 预览端 Canvas 只消费不计算——暂停即冻结、倒放倒退，与「药水条可倒放重看」
 * 的化学隐喻严格一致（it-001 AC2 的延续）。
 *
 * 只服务显影「过程」：包络在 85% 后归零，成片/导出端（progress=1）画面
 * 与无过程动效版本完全一致，三渲染端终态不因动效分叉。
 */
object DevelopFx {

    /** 气泡数量（种子下标 0..N-1） */
    const val BUBBLE_COUNT = 12

    /**
     * 过程动效总包络：潜影早期不起（画面还没醒）、浮现段最活、
     * 85% 后归零（定影要收干净，AC2 逐像素一致的根据）。
     */
    fun activityEnvelope(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val rise = DevelopSpec.smoothstep(0.10f, 0.24f, p)
        val fall = 1f - DevelopSpec.smoothstep(0.70f, 0.85f, p)
        return (rise * fall).coerceIn(0f, 1f)
    }

    /** 药液气泡是否参与：化学工艺（拍立得/胶片）有药水，数码屏没有 */
    fun bubblesFor(mode: DevelopMode): Boolean = mode.reveal != RevealKind.BLOCKS

    /** 单个气泡的瞬时参数（坐标/半径均为照片区归一化比例） */
    class Bubble internal constructor(
        /** 0..1 照片宽 */
        val x: Float,
        /** 0..1 照片高；>1 或 <0 表示已离场 */
        val y: Float,
        /** 相对照片宽的半径 */
        val radius: Float,
        /** 0..1，已含总包络与单泡生命周期 */
        val alpha: Float,
    )

    /**
     * 气泡 i 在进度 p 的位置：相位 = frac(p·loops_i + offset_i)，
     * 每个气泡在一次显影里从底部浮到顶部 1.6–3.2 次，
     * 单泡生命周期用半正弦软包络（入场淡入、离场淡出）。
     */
    fun bubbleAt(index: Int, progress: Float): Bubble {
        val p = progress.coerceIn(0f, 1f)
        val env = activityEnvelope(p)
        val loops = 1.6f + hash(index, 0xA3) * 1.6f
        val raw = p * loops + hash(index, 0x17)
        val phase = raw - raw.toInt()
        val life = sin(Math.PI.toFloat() * phase)
        val xBase = 0.06f + hash(index, 0x51) * 0.88f
        val sway = sin(phase * 4f * Math.PI.toFloat() + hash(index, 0x2B) * 6.283f) * 0.014f
        return Bubble(
            x = (xBase + sway).coerceIn(0f, 1f),
            y = 1.06f - phase * 1.12f,
            radius = 0.004f + hash(index, 0x3D) * 0.006f,
            alpha = 0.16f * env * life,
        )
    }

    /** 前沿湿光带的瞬时参数 */
    class WetBand internal constructor(
        /** 沿推进轴的归一化位置（可为负/超 1 = 带离场中） */
        val position: Float,
        /** 0..1 峰值透明度（已含总包络） */
        val alpha: Float,
        /** 带相对竖直方向的旋转角：SWEEP 前沿竖直=0°，CHEMICAL 对角=-45° */
        val angleDeg: Float,
    )

    /**
     * 显现前沿的软亮带：胶片 SWEEP 与 RevealField.frontX 公式精确对位
     * （frontX = -0.22 + reveal·1.40）；拍立得 CHEMICAL 用左下入口向右上
     * 推进的对角近似（reveal 语义相同）。数码 BLOCKS 恒为零强度。
     */
    fun wetBandAt(mode: DevelopMode, progress: Float, reveal: Float): WetBand {
        val p = progress.coerceIn(0f, 1f)
        return when (mode.reveal) {
            RevealKind.SWEEP -> WetBand(
                position = -0.22f + reveal.coerceIn(0f, 1f) * 1.40f,
                alpha = 0.10f * activityEnvelope(p),
                angleDeg = 0f,
            )

            RevealKind.CHEMICAL -> WetBand(
                position = reveal.coerceIn(0f, 1f),
                alpha = 0.09f * activityEnvelope(p),
                angleDeg = -45f,
            )

            RevealKind.BLOCKS -> WetBand(0f, 0f, 0f)
        }
    }

    /** 种子哈希 → 0..1（无状态、跨调用稳定） */
    private fun hash(i: Int, salt: Int): Float {
        var n = (i * -0x61CA1E39) xor (salt * 0x6F1BB5CD.toInt())
        n = (n xor (n ushr 13)) * -0x3D4D51CB
        n = n xor (n ushr 16)
        return (n ushr 8).toFloat() / 0x00ffffff.toFloat()
    }
}
