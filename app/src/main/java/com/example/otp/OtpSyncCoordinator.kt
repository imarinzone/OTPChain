package com.example.otp

import android.content.Context
import com.example.CodeChainApplication
import com.example.data.local.OtpDirection
import com.example.data.local.SyncedOtpEntity
import com.example.data.relay.RelayEnvelope
import com.example.security.E2eeCryptoManager
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface OtpSyncOutcome {
    data class Synced(val code: String, val serviceName: String) : OtpSyncOutcome
    data class FilteredByCustomRule(val code: String, val serviceName: String) : OtpSyncOutcome
    data object NoOtpFound : OtpSyncOutcome
    data object DuplicateSkipped : OtpSyncOutcome
    data object NoActiveChain : OtpSyncOutcome
    data object OutboundPaused : OtpSyncOutcome
    data class RelayFailed(val reason: String) : OtpSyncOutcome
}

object OtpSyncCoordinator {
    private val recentSyncedCodes = ConcurrentHashMap<String, Long>()
    private const val DEDUP_WINDOW_MS = 30_000L

    suspend fun processAndSyncText(
        context: Context,
        rawText: String,
        fallbackSender: String,
        deduplicate: Boolean = true
    ): OtpSyncOutcome {
        val extracted = OtpParser.extractOtp(rawText, fallbackSender)
            ?: return OtpSyncOutcome.NoOtpFound

        val now = System.currentTimeMillis()
        if (deduplicate) {
            val previous = recentSyncedCodes[extracted.code]
            if (previous != null && (now - previous) < DEDUP_WINDOW_MS) {
                return OtpSyncOutcome.DuplicateSkipped
            }
        }

        val app = context.applicationContext as? CodeChainApplication
            ?: return OtpSyncOutcome.RelayFailed("Application unavailable")

        val deviceId = app.preferencesRepository.ensureDeviceIdInitialized()
        val settings = app.preferencesRepository.state.first()

        if (settings.customRulesEnabled && settings.customFilterRules.isNotEmpty()) {
            val matchedRule = OtpParser.findMatchingCustomRule(
                rawText = rawText,
                extracted = extracted,
                rules = settings.customFilterRules
            )
            if (matchedRule == null) {
                return OtpSyncOutcome.FilteredByCustomRule(extracted.code, extracted.serviceName)
            }
        }

        val chain = settings.activeChain ?: return OtpSyncOutcome.NoActiveChain

        if (!settings.isSyncEnabled || !settings.sendOutboundEnabled) {
            return OtpSyncOutcome.OutboundPaused
        }

        if (deduplicate) {
            recentSyncedCodes[extracted.code] = now
            if (recentSyncedCodes.size > 32) {
                recentSyncedCodes.entries.removeIf { (_, ts) -> (now - ts) > 120_000L }
            }
        }

        val msgUuid = UUID.randomUUID().toString()
        val envelope = RelayEnvelope(
            messageUuid = msgUuid,
            type = "OTP",
            senderDeviceId = deviceId,
            senderDeviceName = settings.localDeviceName,
            otpCode = extracted.code,
            serviceName = extracted.serviceName,
            snippet = extracted.snippet,
            sentAtEpochMs = now
        )

        val encrypted = runCatching {
            E2eeCryptoManager.encrypt(
                plaintext = app.relayClient.encodeEnvelope(envelope),
                masterKey = chain.masterKeyBytes,
                chainId = chain.chainId
            )
        }.getOrElse { err ->
            return OtpSyncOutcome.RelayFailed(err.localizedMessage ?: "Encryption failed")
        }

        val publishResult = app.relayClient.publishCiphertext(
            relayBaseUrl = settings.relayBaseUrl,
            blindedTopic = chain.blindedTopic,
            ciphertextToken = encrypted
        )

        if (publishResult.isFailure) {
            if (deduplicate) {
                recentSyncedCodes.remove(extracted.code)
            }
            return OtpSyncOutcome.RelayFailed(
                publishResult.exceptionOrNull()?.localizedMessage ?: "Check network"
            )
        }

        val expiresAt = if (settings.retentionHours <= 0) {
            Long.MAX_VALUE
        } else {
            now + settings.retentionHours * 3_600_000L
        }

        app.otpRepository.insertIfNew(
            SyncedOtpEntity(
                messageUuid = msgUuid,
                serviceName = extracted.serviceName,
                otpCode = extracted.code,
                rawSnippet = extracted.snippet,
                direction = OtpDirection.OUTBOUND.name,
                peerDeviceName = chain.peerDeviceName,
                timestamp = now,
                expiresAt = expiresAt,
                isCopied = false
            )
        )

        return OtpSyncOutcome.Synced(extracted.code, extracted.serviceName)
    }
}
