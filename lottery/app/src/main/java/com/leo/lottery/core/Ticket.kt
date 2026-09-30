package com.leo.lottery.core

import kotlinx.serialization.Serializable

/** 票（票夹持久化实体，见 03-data-model）。 */
@Serializable
data class Ticket(
    val id: String,
    val game: Game,
    val createdAt: Long,
    /** 种子指纹（图片 SHA-256 前 16 hex）。 */
    val seed: String,
    /** 批次（「再换一批」递增）。 */
    val take: Int,
    val zone1: List<Int>,
    val zone2: List<Int>,
    /** 生成时指向的最新开奖期号。 */
    val targetIssue: String,
) {
    val isCombo: Boolean
        get() = zone1.size > game.baseZone1 || zone2.size > game.baseZone2

    val combos: Int
        get() = game.comboCount(zone1.size, zone2.size).toInt()
}
