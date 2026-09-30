package com.leo.lottery.core

import java.security.MessageDigest

/** 种子指纹与确定性哈希工具（SHA-256）。 */
object SeedHash {

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

    /** 展示用短指纹：前 16 hex。 */
    fun fingerprint(fullHex: String): String = fullHex.take(16)

    /** 哈希前 8 字节做大端 Long，作为 PRNG 种子。 */
    fun seedLong(bytes: ByteArray): Long {
        val h = sha256(bytes)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (h[i].toLong() and 0xFF)
        return v
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
