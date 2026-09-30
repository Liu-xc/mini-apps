package com.leo.lottery.core

/**
 * SplitMix64 确定性 PRNG：同一种子恒同一序列（跨版本稳定，不依赖 JDK 随机实现）。
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        var z = state + 0x9E3779B97F4A7C15UL.toLong()
        state = z
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
        return z xor (z ushr 31)
    }

    /** [0, bound) 均匀取整数（拒绝采样去偏）。 */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        if (bound == 1) return 0
        val b = bound.toLong()
        while (true) {
            val r = nextLong() ushr 1
            val limit = Long.MAX_VALUE - Long.MAX_VALUE % b
            if (r < limit) return (r % b).toInt()
        }
    }

    /** 从 [from, until) 不放回抽取 count 个，升序返回。 */
    fun sample(from: Int, until: Int, count: Int): List<Int> {
        require(count <= until - from) { "sample larger than pool" }
        val pool = IntArray(until - from) { from + it }
        for (i in 0 until count) {
            val j = i + nextInt(pool.size - i)
            val t = pool[i]; pool[i] = pool[j]; pool[j] = t
        }
        return pool.slice(0 until count).sorted()
    }
}
