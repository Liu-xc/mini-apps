package com.leo.libs.agent

/**
 * key 存取抽象（ADR-003）：core 不持平台实现——Keystore/钥匙串加密实现归消费 app 层，
 * SDK 只见此接口；红线：导出永不带 key、日志只出 [maskApiKey]。
 */
interface ApiKeyStore {
    suspend fun get(presetId: String): String?
    suspend fun put(presetId: String, key: String)
    suspend fun delete(presetId: String)
}

/** 内存实现：演示模式 / 测试 / 无持久化场景 */
class InMemoryApiKeyStore : ApiKeyStore {
    private val map = mutableMapOf<String, String>()

    override suspend fun get(presetId: String): String? = map[presetId]
    override suspend fun put(presetId: String, key: String) { map[presetId] = key }
    override suspend fun delete(presetId: String) { map.remove(presetId) }
}

/** 日志/异常只出 mask：形如 sk-a…wxyz */
fun maskApiKey(key: String): String = when {
    key.isBlank() -> "(未配置)"
    key.length <= 8 -> key.take(2) + "***"
    else -> key.take(4) + "***" + key.takeLast(4)
}
