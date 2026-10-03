package com.leo.wardrobe.data.prefs

import com.leo.libs.agent.ApiKeyStore
import com.leo.wardrobe.BuildConfig

/**
 * it-080 / ADR-031：体验包内置 Key 补缺——首启对当前 namespace（演示 mock_agent / 真实 agent）
 * 未配置的 presetId 预填构建注入的 Key；**只补缺不覆盖**，用户在 W11 配过/清除的槽位不被触碰。
 * 常规构建 BUILTIN_KEY_* 恒空串，列表为空即零行为。密钥红线：本类不写日志。
 */
object BuiltinKeysSeeder {

    private val builtins: List<Pair<String, String>> = listOf(
        "glm" to BuildConfig.BUILTIN_KEY_GLM,
        "mimo-tp" to BuildConfig.BUILTIN_KEY_MIMO_TP,
        "siliconflow" to BuildConfig.BUILTIN_KEY_SILICONFLOW,
    ).filter { it.second.isNotBlank() }

    suspend fun seed(store: ApiKeyStore) {
        seedInto(store, builtins)
    }

    /** 返回实际补填的 presetId 列表（测试与诊断用；不返回 Key 本体） */
    suspend fun seedInto(store: ApiKeyStore, keys: List<Pair<String, String>>): List<String> {
        val seeded = mutableListOf<String>()
        for ((presetId, key) in keys) {
            if (store.get(presetId) == null) {
                store.put(presetId, key)
                seeded += presetId
            }
        }
        return seeded
    }
}
