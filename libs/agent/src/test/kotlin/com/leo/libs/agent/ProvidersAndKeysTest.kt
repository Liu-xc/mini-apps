package com.leo.libs.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersAndKeysTest {

    @Test
    fun `glm 预设路径无 v1`() {
        assertFalse(Providers.glm.baseUrl.endsWith("/v1"))
        assertTrue(Providers.glm.baseUrl.endsWith("/api/paas/v4"))
        assertTrue(Providers.glm.quirks.reasoningField)
        assertTrue(Providers.glm.models.isNotEmpty())
    }

    @Test
    fun `mimo 预设 baseUrl 留空待 M0 校准`() {
        assertTrue(Providers.mimo.baseUrl.isEmpty())
        assertTrue(Providers.mimo.quirks.reasoningField)
    }

    @Test
    fun `一等公民 id 唯一`() {
        val ids = listOf(Providers.glm, Providers.mimo).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `custom 预设与默认模型`() {
        val p = ProviderPreset.custom("deepseek", "DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat"))
        assertEquals("deepseek-chat", p.defaultModel)
    }

    @Test
    fun `mask 规则`() {
        assertEquals("sk-a***wxyz", maskApiKey("sk-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(maskApiKey("short").endsWith("***"))
        assertEquals("(未配置)", maskApiKey(""))
    }
}
