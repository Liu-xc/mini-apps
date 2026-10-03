package com.leo.wardrobe.data.prefs

import com.leo.libs.agent.InMemoryApiKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** it-080：内置 Key 补缺语义——只补缺、不覆盖已配槽位 */
class BuiltinKeysSeederTest {

    @Test
    fun `空 store 全部补填`() = runTest {
        val store = InMemoryApiKeyStore()
        val seeded = BuiltinKeysSeeder.seedInto(
            store,
            listOf("glm" to "k1", "siliconflow" to "k2", "mimo-tp" to "k3"),
        )
        assertEquals(listOf("glm", "siliconflow", "mimo-tp"), seeded)
        assertEquals("k1", store.get("glm"))
        assertEquals("k3", store.get("mimo-tp"))
    }

    @Test
    fun `已配置槽位不被覆盖`() = runTest {
        val store = InMemoryApiKeyStore()
        store.put("glm", "user-key")
        val seeded = BuiltinKeysSeeder.seedInto(store, listOf("glm" to "builtin-key"))
        assertEquals(emptyList<String>(), seeded)
        assertEquals("user-key", store.get("glm"))
    }

    @Test
    fun `重复执行幂等`() = runTest {
        val store = InMemoryApiKeyStore()
        val keys = listOf("glm" to "k1")
        BuiltinKeysSeeder.seedInto(store, keys)
        val second = BuiltinKeysSeeder.seedInto(store, keys)
        assertEquals(emptyList<String>(), second)
    }
}
