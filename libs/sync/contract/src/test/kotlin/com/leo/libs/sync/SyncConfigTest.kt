package com.leo.libs.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConfigTest {

    @Test
    fun sharePayloadRoundtrip() {
        val config = SyncConfig(
            backend = "feishu-bitable",
            params = mapOf("appId" to "cli_x", "appSecret" to "s3cr3t", "appToken" to "BascXX"),
            readOnly = true,
        )
        val encoded = SyncConfig.encodeSharePayload(config)
        assertEquals(config, SyncConfig.parseSharePayload(encoded))
    }

    @Test
    fun parseRejectsBadPayloads() {
        assertNull(SyncConfig.parseSharePayload("not json"))
        assertNull(SyncConfig.parseSharePayload("""{"v":2,"backend":"x"}"""))
        assertNull(SyncConfig.parseSharePayload("""{"backend":"x"}""")) // 缺 v
    }

    @Test
    fun defaultsReadOnlyFalse() {
        val parsed = SyncConfig.parseSharePayload("""{"v":1,"backend":"b","params":{}}""")
        assertEquals("b", parsed?.backend)
        assertTrue(parsed != null && !parsed.readOnly)
    }
}
