package com.leo.darkroom.develop

/**
 * 显影过程动效参数（it-008）：(mode, progress) → 参数 的确定性纯函数，
 * 预览端 Canvas 只消费不计算——暂停即冻结、倒放倒退，与「药水条可倒放重看」
 * 的化学隐喻严格一致（it-001 AC2 的延续）。
 *
 * it-008 二次修正（Leo 实机反馈「细致复刻显影过程」）：
 * - **气泡删除**——真实显影发生在相纸/底片夹层内，用户看不见泡；观感失真。
 * - 拍立得的显现改为 `RevealField.CHEMICAL` 的**白浊阻光层均匀消散**（真源同改），
 *   湿光带同样不给（见 wetBandAt 注释）。
 * - 保留：胶片 SWEEP 前沿的对位湿边（属前沿本身）。
 *
 * 只服务显影「过程」：包络在 85% 后归零，成片/导出端（progress=1）画面
 * 与无过程动效版本完全一致，三渲染端终态不因动效分叉。
 */
object DevelopFx {

    /**
     * 过程动效总包络：潜影早期不起（画面还没醒）、浮现段最活、
     * 85% 后归零（定影要收干净，终态逐像素一致的根据）。
     */
    fun activityEnvelope(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val rise = DevelopSpec.smoothstep(0.10f, 0.24f, p)
        val fall = 1f - DevelopSpec.smoothstep(0.70f, 0.85f, p)
        return (rise * fall).coerceIn(0f, 1f)
    }

    /** 前沿湿光带的瞬时参数 */
    class WetBand internal constructor(
        /** 沿推进轴的归一化位置（可为负/超 1 = 带离场中） */
        val position: Float,
        /** 0..1 峰值透明度（已含总包络） */
        val alpha: Float,
        /** 带相对竖直方向的旋转角（SWEEP 前沿竖直 = 0°） */
        val angleDeg: Float,
    )

    /**
     * 显现前沿的软亮带：仅胶片 SWEEP——与 RevealField.frontX 公式精确对位
     * （frontX = -0.22 + reveal·1.40），是冲洗前沿本身的湿边。
     * 拍立得不给（it-008 收尾修正，Leo 实机反馈）：叠加层与「整张从白里浮现」的
     * 真实观感冲突；数码 BLOCKS 恒为零强度。
     */
    fun wetBandAt(mode: DevelopMode, progress: Float, reveal: Float): WetBand {
        val p = progress.coerceIn(0f, 1f)
        return when (mode.reveal) {
            RevealKind.SWEEP -> WetBand(
                position = -0.22f + reveal.coerceIn(0f, 1f) * 1.40f,
                alpha = 0.10f * activityEnvelope(p),
                angleDeg = 0f,
            )

            RevealKind.CHEMICAL -> WetBand(0f, 0f, 0f)

            RevealKind.BLOCKS -> WetBand(0f, 0f, 0f)
        }
    }
}
