package com.synax.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores auth credentials encrypted with an Android Keystore AES/GCM key. */
class SecureTokens(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("synax_secure_tokens", Context.MODE_PRIVATE)
    private val alias = "synax_session_aes_v1"
    private val storeName = "AndroidKeyStore"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(storeName).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, storeName)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized fun save(role: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(role, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    @Synchronized fun read(role: String): String? {
        val packed = prefs.getString(role, null) ?: return null
        return try {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            if (bytes.size < 13) return null
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit().remove(role).apply()
            null
        }
    }

    fun clear(role: String) { prefs.edit().remove(role).apply() }
    fun clearAll() { prefs.edit().clear().apply() }
}
