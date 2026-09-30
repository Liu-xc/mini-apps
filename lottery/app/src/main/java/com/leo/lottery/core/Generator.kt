package com.leo.lottery.core

/**
 * 图片种子 → 号码的确定性映射。
 * 同 fingerprint + 玩法 + 区个数 + 批次 → 恒同结果（US-1.3，单测钉死）。
 */
object Generator {

    data class Numbers(val zone1: List<Int>, val zone2: List<Int>)

    fun generate(
        fingerprint: String,
        game: Game,
        zone1Size: Int,
        zone2Size: Int,
        take: Int,
    ): Numbers {
        require(game.isValidSizes(zone1Size, zone2Size)) { "invalid zone sizes" }
        val key = "$fingerprint|${game}|$zone1Size|$zone2Size|$take"
        val rng = SplitMix64(SeedHash.seedLong(key.toByteArray(Charsets.UTF_8)))
        return Numbers(
            zone1 = rng.sample(1, game.poolZone1 + 1, zone1Size),
            zone2 = rng.sample(1, game.poolZone2 + 1, zone2Size),
        )
    }

    private fun Game.isValidSizes(zone1Size: Int, zone2Size: Int): Boolean =
        zone1Size in baseZone1..comboMaxZone1 && zone2Size == baseZone2
}
