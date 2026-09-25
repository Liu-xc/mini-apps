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
    fun `mimo 预设 baseUrl M0 已回填且两 host 不可混用`() {
        // 按量付费（sk- key）
        assertTrue(Providers.mimo.baseUrl.endsWith("/v1"))
        assertEquals("https://api.xiaomimimo.com/v1", Providers.mimo.baseUrl)
        // Token 套餐（tp-/ttp- key 专属 host，M0 实证按量 host 对 tp- key 返回 401）
        assertEquals("https://token-plan-cn.xiaomimimo.com/v1", Providers.mimoTokenPlan.baseUrl)
        assertTrue(Providers.mimo.quirks.reasoningField)
        assertTrue(Providers.mimoTokenPlan.quirks.reasoningField)
    }

    @Test
    fun `一等公民 id 唯一`() {
        val ids = Providers.all.map { it.id }
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
