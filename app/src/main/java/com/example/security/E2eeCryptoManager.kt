package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2eeCryptoManager {
    private const val KEY_BYTES = 32 // 256-bit AES key
    private const val IV_BYTES = 12  // 96-bit GCM nonce
    private const val TAG_BITS = 128 // 128-bit authentication tag
    private const val VERSION_PREFIX = "v1"

    private val secureRandom = SecureRandom()

    private val fingerprintWords = listOf(
        "ALPINE", "AMBER", "ANCHOR", "ATLAS", "AURORA", "BEACON", "BREEZE", "BRIDGE",
        "CANYON", "CEDAR", "CIPHER", "COBALT", "COMET", "CORAL", "CREST", "CRYSTAL",
        "DAWN", "DELTA", "DRIFT", "ECHO", "EMBER", "FALCON", "FJORD", "FLINT",
        "FORGE", "FROST", "GLACIER", "HARBOR", "HORIZON", "INDIGO", "IVORY", "JADE",
        "JASPER", "KESTREL", "LANTERN", "LUMEN", "MEADOW", "MERIDIAN", "MIRAGE", "MIST",
        "NEBULA", "NEXUS", "NORTH", "NOVA", "OAK", "OBSIDIAN", "ONYX", "ORBIT",
        "PEAK", "PHOENIX", "PINE", "POLARIS", "PRISM", "PULSE", "QUARTZ", "RAVEN",
        "RIDGE", "RIVER", "SABLE", "SIERRA", "SIGNAL", "SILVER", "SOLAR", "SPARK"
    )

    fun generateMasterKey(): ByteArray {
        val bytes = ByteArray(KEY_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decodeBase64Url(encoded: String): ByteArray {
        return Base64.decode(encoded.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun deriveBlindedTopic(masterKey: ByteArray): String {
        val digest = sha256Domain(masterKey, "codechain-relay-topic-v1")
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "cc-${hex.take(32)}"
    }

    fun deriveSafetyFingerprint(masterKey: ByteArray): String {
        val digest = sha256Domain(masterKey, "codechain-fingerprint-v1")
        val w1 = fingerprintWords[(digest[0].toInt() and 0xFF) % fingerprintWords.size]
        val w2 = fingerprintWords[(digest[1].toInt() and 0xFF) % fingerprintWords.size]
        val w3 = fingerprintWords[(digest[2].toInt() and 0xFF) % fingerprintWords.size]
        val w4 = fingerprintWords[(digest[3].toInt() and 0xFF) % fingerprintWords.size]
        val hexCheck = "%02X%02X".format(digest[4], digest[5])
        return "$w1 · $w2 · $w3 · $w4 #$hexCheck"
    }

    fun encrypt(plaintext: String, masterKey: ByteArray, chainId: String): String {
        require(masterKey.size == KEY_BYTES) { "Master key must be 256 bits" }
        val aesKeyBytes = sha256Domain(masterKey, "codechain-aes-gcm-v1")
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(aesKeyBytes, "AES")
        val gcmSpec = GCMParameterSpec(TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(chainId.toByteArray(StandardCharsets.UTF_8))

        val ciphertextAndTag = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return "$VERSION_PREFIX.${encodeBase64Url(iv)}.${encodeBase64Url(ciphertextAndTag)}"
    }

    fun decrypt(token: String, masterKey: ByteArray, chainId: String): String {
        require(masterKey.size == KEY_BYTES) { "Master key must be 256 bits" }
        val parts = token.trim().split(".")
        require(parts.size == 3 && parts[0] == VERSION_PREFIX) { "Malformed E2EE token" }

        val iv = decodeBase64Url(parts[1])
        val ciphertextAndTag = decodeBase64Url(parts[2])
        require(iv.size == IV_BYTES) { "Invalid GCM IV length" }

        val aesKeyBytes = sha256Domain(masterKey, "codechain-aes-gcm-v1")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(aesKeyBytes, "AES")
        val gcmSpec = GCMParameterSpec(TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(chainId.toByteArray(StandardCharsets.UTF_8))

        val plainBytes = cipher.doFinal(ciphertextAndTag)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun sha256Domain(masterKey: ByteArray, domain: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(masterKey)
        md.update(domain.toByteArray(StandardCharsets.UTF_8))
        return md.digest()
    }
}
