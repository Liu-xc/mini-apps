package com.leo.wardrobe.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.leo.libs.agent.ApiKeyStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * BYOK API Key 的平台加密实现（it-041 US-41a，libs/agent ADR-003 归 app 层的那半个）：
 * AndroidKeyStore 主密钥（不可导出）+ AES-GCM，密文落普通 SharedPreferences。
 *
 * 红线：③ 日志/异常只出 mask（调用方用 maskApiKey）；① 数据包导出不读本文件；
 * ② 演示模式由 AppContainer 注入 InMemoryApiKeyStore，真 key 不进演示态。
 */
class KeystoreApiKeyStore(context: Context) : ApiKeyStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("agent_secrets", Context.MODE_PRIVATE)

    override suspend fun get(presetId: String): String? {
        val encoded = prefs.getString(presetId, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull() // 密钥失效/损坏按未配置处理
    }

    override suspend fun put(presetId: String, key: String) {
        prefs.edit().putString(presetId, encrypt(key)).apply()
    }

    override suspend fun delete(presetId: String) {
        prefs.edit().remove(presetId).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // 首次生成（并发竞态下后生成者覆盖无妨，都是新随机钥）
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wardrobe_agent_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
