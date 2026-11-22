package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.prefs.ChainPreferencesRepository
import com.example.otp.OtpParser
import com.example.security.E2eeCryptoManager
import com.example.security.PairingPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        assertTrue(uri.startsWith("otpchain://pair?"))
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

        val colonNoSpaceSms = OtpParser.extractOtp("Bank Alert OTP:482910 valid for 5 mins at 12:30pm.")
        assertNotNull(colonNoSpaceSms)
        assertEquals("482910", colonNoSpaceSms?.code)

        val casualSms = OtpParser.extractOtp("Hey, dinner at 7:30pm near 4th St? Total was $24.50 in 2026.")
        assertNull(casualSms)
    }

    @Test
    fun `custom filter rules narrow synced otps on top of normal detection`() {
        val rules = listOf("Chase", "GitHub")

        val chaseText = "Chase: Your one-time verification code is 482910."
        val chaseOtp = OtpParser.extractOtp(chaseText)!!
        assertEquals("Chase", OtpParser.findMatchingCustomRule(chaseText, chaseOtp, rules))

        val githubText = "Use 842 109 as your github login code."
        val githubOtp = OtpParser.extractOtp(githubText)!!
        assertEquals("GitHub", OtpParser.findMatchingCustomRule(githubText, githubOtp, rules))

        val otherBankText = "AcmeBank: Your verification code is 112233."
        val otherOtp = OtpParser.extractOtp(otherBankText)!!
        assertNull(OtpParser.findMatchingCustomRule(otherBankText, otherOtp, rules))
    }

    @Test
    fun `multiple sync chains store independent rules and toggles`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChainPreferencesRepository(context)

        val key1 = E2eeCryptoManager.encodeBase64Url(E2eeCryptoManager.generateMasterKey())
        val key2 = E2eeCryptoManager.encodeBase64Url(E2eeCryptoManager.generateMasterKey())

        repo.addOrUpdateChain(
            chainId = "chain_work",
            masterKeyBase64 = key1,
            peerDeviceName = "Work Mac",
            isPeerConfirmed = true,
            label = "Work"
        )
        repo.addOrUpdateChain(
            chainId = "chain_bank",
            masterKeyBase64 = key2,
            peerDeviceName = "Backup Phone",
            isPeerConfirmed = true,
            label = "Banking"
        )

        repo.addCustomFilterRule("chain_work", "GitHub")
        repo.addCustomFilterRule("chain_work", "Okta")
        repo.addCustomFilterRule("chain_bank", "Chase")

        val state = repo.state.first()
        assertEquals(2, state.chains.size)

        val work = state.chains.first { it.chainId == "chain_work" }
        val bank = state.chains.first { it.chainId == "chain_bank" }
        assertEquals(listOf("GitHub", "Okta"), work.customFilterRules)
        assertEquals(listOf("Chase"), bank.customFilterRules)

        // Switching selected chain preserves both chains
        repo.selectChain("chain_work")
        assertEquals("chain_work", repo.state.first().selectedChainId)

        // Removing one chain keeps the other intact
        repo.removeChain("chain_work")
        val afterRemove = repo.state.first()
        assertEquals(1, afterRemove.chains.size)
        assertEquals("chain_bank", afterRemove.chains.first().chainId)
    }
}
