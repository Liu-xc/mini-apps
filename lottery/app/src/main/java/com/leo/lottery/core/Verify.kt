package com.leo.lottery.core

/** 单注命中数。 */
data class Hit(val zone1: Int, val zone2: Int) {
    override fun toString(): String = "$zone1+$zone2"
}

/** 官方奖级（SSQ 六级 / DLT 九级），order 越小奖越大。 */
enum class PrizeLevel(val order: Int, val label: String) {
    L1(1, "一等"),
    L2(2, "二等"),
    L3(3, "三等"),
    L4(4, "四等"),
    L5(5, "五等"),
    L6(6, "六等"),
    L7(7, "七等"),
    L8(8, "八等"),
    L9(9, "九等"),
}

/** 票 × 开奖结果的验票结论（复式聚合各级注数）。 */
data class TicketVerdict(
    /** 集合交集命中（复式展示用：选号集合与中奖集合的交）。 */
    val setHits: Hit,
    /** 总注数。 */
    val combos: Int,
    /** 各奖级中奖注数（仅含 >0 的级）。 */
    val byLevel: Map<PrizeLevel, Int>,
    /** 中奖总注数。 */
    val totalWinning: Int,
    /** 最高奖级；未中为 null。 */
    val best: PrizeLevel?,
) {
    val won: Boolean get() = totalWinning > 0
}

object Verify {

    /** 单注命中。 */
    fun hitOf(game: Game, z1: List<Int>, z2: List<Int>, result: DrawResult): Hit =
        Hit(z1.count { it in result.zone1 }, z2.count { it in result.zone2 })

    /** 官方奖级表；未中返回 null。 */
    fun prizeOf(game: Game, hit: Hit): PrizeLevel? = when (game) {
        Game.SSQ -> when (hit) {
            Hit(6, 1) -> PrizeLevel.L1
            Hit(6, 0) -> PrizeLevel.L2
            Hit(5, 1) -> PrizeLevel.L3
            Hit(5, 0), Hit(4, 1) -> PrizeLevel.L4
            Hit(4, 0), Hit(3, 1) -> PrizeLevel.L5
            Hit(2, 1), Hit(1, 1), Hit(0, 1) -> PrizeLevel.L6
            else -> null
        }
        Game.DLT -> when (hit) {
            Hit(5, 2) -> PrizeLevel.L1
            Hit(5, 1) -> PrizeLevel.L2
            Hit(5, 0) -> PrizeLevel.L3
            Hit(4, 2) -> PrizeLevel.L4
            Hit(4, 1), Hit(3, 2) -> PrizeLevel.L5
            Hit(4, 0) -> PrizeLevel.L6
            Hit(3, 1), Hit(2, 2) -> PrizeLevel.L7
            Hit(3, 0), Hit(1, 2), Hit(2, 1) -> PrizeLevel.L8
            Hit(0, 2) -> PrizeLevel.L9
            else -> null
        }
    }

    /** 验票：单式判一注，复式全展开逐注判定后聚合（展开上限 50,000 注防爆）。 */
    fun verify(ticket: Ticket, result: DrawResult): TicketVerdict {
        require(ticket.game == result.game) { "game mismatch" }
        val byLevel = LinkedHashMap<PrizeLevel, Int>()
        var processed = 0
        outer@ for (c1 in subsets(ticket.zone1, ticket.game.baseZone1)) {
            for (c2 in subsets(ticket.zone2, ticket.game.baseZone2)) {
                if (processed++ >= 50_000) break@outer
                prizeOf(ticket.game, hitOf(ticket.game, c1, c2, result))
                    ?.let { byLevel[it] = (byLevel[it] ?: 0) + 1 }
            }
        }
        val total = byLevel.values.sum()
        return TicketVerdict(
            setHits = hitOf(ticket.game, ticket.zone1, ticket.zone2, result),
            combos = ticket.combos,
            byLevel = byLevel,
            totalWinning = total,
            best = byLevel.keys.minByOrNull { it.order },
        )
    }

    /** n 选 k 全部组合（升序元素列表）。 */
    fun subsets(list: List<Int>, k: Int): List<List<Int>> {
        if (k == 0) return listOf(emptyList())
        val out = ArrayList<List<Int>>()
        fun dfs(start: Int, acc: MutableList<Int>) {
            if (acc.size == k) { out.add(ArrayList(acc)); return }
            for (i in start..list.size - (k - acc.size)) {
                acc.add(list[i]); dfs(i + 1, acc); acc.removeAt(acc.size - 1)
            }
        }
        dfs(0, ArrayList())
        return out
    }
}
