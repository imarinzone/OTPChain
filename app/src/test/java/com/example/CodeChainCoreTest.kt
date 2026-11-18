package com.example

import com.example.otp.OtpParser
import com.example.security.E2eeCryptoManager
import com.example.security.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeChainCoreTest {

    @Test
    fun `e2ee encrypt and decrypt round trip succeeds and rejects tampered aad`() {
        val key = E2eeCryptoManager.generateMasterKey()
        val chainId = "chain1234567890"
        val secretPayload = """{"otpCode":"482910","serviceName":"Chase"}"""

        val encrypted = E2eeCryptoManager.encrypt(secretPayload, key, chainId)
        assertTrue(encrypted.startsWith("v1."))
        assertTrue(!encrypted.contains("482910"))

        val decrypted = E2eeCryptoManager.decrypt(encrypted, key, chainId)
        assertEquals(secretPayload, decrypted)

        val wrongChainResult = runCatching {
            E2eeCryptoManager.decrypt(encrypted, key, "wrong-chain")
        }
        assertTrue(wrongChainResult.isFailure)
    }

    @Test
    fun `blinded topic and safety fingerprint are deterministic`() {
        val key = E2eeCryptoManager.generateMasterKey()
        val topic1 = E2eeCryptoManager.deriveBlindedTopic(key)
        val topic2 = E2eeCryptoManager.deriveBlindedTopic(key)
        assertEquals(topic1, topic2)
        assertTrue(topic1.startsWith("cc-"))

        val fp1 = E2eeCryptoManager.deriveSafetyFingerprint(key)
        val fp2 = E2eeCryptoManager.deriveSafetyFingerprint(key)
        assertEquals(fp1, fp2)

        val otherKey = E2eeCryptoManager.generateMasterKey()
        assertNotEquals(topic1, E2eeCryptoManager.deriveBlindedTopic(otherKey))
    }

    @Test
    fun `pairing payload uri round trip validates 256-bit key`() {
        val keyB64 = E2eeCryptoManager.encodeBase64Url(E2eeCryptoManager.generateMasterKey())
        val original = PairingPayload(
            chainId = "abc12345",
            masterKeyBase64 = keyB64,
            peerDeviceName = "Pixel 9",
            relayBaseUrl = "https://ntfy.sh"
        )
        val uri = original.toUriString()
        val parsed = PairingPayload.parse(uri).getOrThrow()

        assertEquals(original.chainId, parsed.chainId)
        assertEquals(original.masterKeyBase64, parsed.masterKeyBase64)
        assertEquals(original.peerDeviceName, parsed.peerDeviceName)
        assertEquals(original.relayBaseUrl, parsed.relayBaseUrl)
    }

    @Test
    fun `otp parser extracts verification codes and ignores non-otp texts`() {
        val bankSms = OtpParser.extractOtp("Chase: Your verification code is 482910. Do not share.")
        assertNotNull(bankSms)
        assertEquals("482910", bankSms?.code)
        assertEquals("Chase", bankSms?.serviceName)

        val googleSms = OtpParser.extractOtp("G-918273 is your Google verification code.")
        assertNotNull(googleSms)
        assertEquals("G-918273", googleSms?.code)

        val spacedSms = OtpParser.extractOtp("Use 842 109 as your GitHub 2FA login passcode.")
        assertNotNull(spacedSms)
        assertEquals("842109", spacedSms?.code)

        val casualSms = OtpParser.extractOtp("Hey, dinner at 7:30pm near 4th St? Total was $24.50 in 2026.")
        assertNull(casualSms)
    }
}
