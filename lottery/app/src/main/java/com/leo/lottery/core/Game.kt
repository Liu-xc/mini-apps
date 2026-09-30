package com.leo.lottery.core

/** 玩法定义：区间、复式限档、注数组合数。UI/生成/验票共用此真源。 */
enum class Game(val label: String, val notation: String) {
    SSQ("双色球", "6+1"),
    DLT("大乐透", "5+2");

    val baseZone1: Int get() = when (this) { SSQ -> 6; DLT -> 5 }
    val poolZone1: Int get() = when (this) { SSQ -> 33; DLT -> 35 }
    val baseZone2: Int get() = when (this) { SSQ -> 1; DLT -> 2 }
    val poolZone2: Int get() = when (this) { SSQ -> 16; DLT -> 12 }

    val zone1Range: IntRange get() = 1..poolZone1
    val zone2Range: IntRange get() = 1..poolZone2

    /** 复式主区限档（demo 档位，见 03-data-model；特号区恒单式）。 */
    val comboMinZone1: Int get() = when (this) { SSQ -> 7; DLT -> 6 }
    val comboMaxZone1: Int get() = when (this) { SSQ -> 12; DLT -> 10 }

    val zone1Label: String get() = when (this) { SSQ -> "红球"; DLT -> "前区" }
    val zone2Label: String get() = when (this) { SSQ -> "蓝球"; DLT -> "后区" }

    fun isValid(zone1: List<Int>, zone2: List<Int>): Boolean =
        zone1.size in baseZone1..comboMaxZone1 &&
            zone2.size == baseZone2 &&
            zone1.all { it in zone1Range } && zone1.distinct().size == zone1.size &&
            zone2.all { it in zone2Range } && zone2.distinct().size == zone2.size
}

fun combination(n: Int, k: Int): Long {
    if (k < 0 || k > n) return 0L
    val r = minOf(k, n - k)
    var result = 1L
    for (i in 1..r) result = result * (n - r + i) / i
    return result
}

fun Game.comboCount(zone1Size: Int, zone2Size: Int): Long =
    combination(zone1Size, baseZone1) * combination(zone2Size, baseZone2)
