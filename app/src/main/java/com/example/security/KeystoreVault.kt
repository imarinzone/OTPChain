package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreVault {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "codechain_master_wrap_key_v1"
    private const val KEYSTORE_PREFIX = "ks1:"
    private const val FALLBACK_PREFIX = "raw:"
    private const val TAG_BITS = 128

    fun wrapSecret(plaintext: String): String {
        return runCatching {
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$KEYSTORE_PREFIX$ivB64:$dataB64"
        }.getOrElse {
            val encoded = Base64.encodeToString(plaintext.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            "$FALLBACK_PREFIX$encoded"
        }
    }

    fun unwrapSecret(stored: String): String? {
        if (stored.isBlank()) return null
        return runCatching {
            when {
                stored.startsWith(KEYSTORE_PREFIX) -> {
                    val payload = stored.removePrefix(KEYSTORE_PREFIX)
                    val parts = payload.split(":")
                    if (parts.size != 2) return null
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
                    val key = getOrCreateKeystoreKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                    String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
                }
                stored.startsWith(FALLBACK_PREFIX) -> {
                    val raw = Base64.decode(stored.removePrefix(FALLBACK_PREFIX), Base64.NO_WRAP)
                    String(raw, StandardCharsets.UTF_8)
                }
                else -> stored
            }
        }.getOrNull()
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
