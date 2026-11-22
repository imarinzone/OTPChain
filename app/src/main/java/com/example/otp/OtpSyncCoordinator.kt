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
    data class Synced(
        val code: String,
        val serviceName: String,
        val syncedChainNames: List<String>
    ) : OtpSyncOutcome
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

    /**
     * Extracts an OTP from [rawText] and routes it across matching sync chains.
     * - When [targetChainId] is null (automatic SMS/notification capture), evaluates every paired
     *   chain independently using each chain's own toggle state and custom filter rules.
     * - When [targetChainId] is specified (manual Extract & Sync from a specific chain card),
     *   evaluates and sends only to that chain.
     */
    suspend fun processAndSyncText(
        context: Context,
        rawText: String,
        fallbackSender: String,
        deduplicate: Boolean = true,
        targetChainId: String? = null
    ): OtpSyncOutcome {
        val extracted = OtpParser.extractOtp(rawText, fallbackSender)
            ?: return OtpSyncOutcome.NoOtpFound

        val app = context.applicationContext as? CodeChainApplication
            ?: return OtpSyncOutcome.RelayFailed("Application unavailable")

        val deviceId = app.preferencesRepository.ensureDeviceIdInitialized()
        val settings = app.preferencesRepository.state.first()

        val candidateChains = if (targetChainId != null) {
            settings.chains.filter { it.chainId == targetChainId }
        } else {
            settings.chains
        }

        if (candidateChains.isEmpty()) {
            return OtpSyncOutcome.NoActiveChain
        }

        if (!settings.isSyncEnabled) {
            return OtpSyncOutcome.OutboundPaused
        }

        val enabledChains = candidateChains.filter { it.isSyncEnabled && it.sendOutboundEnabled }
        if (enabledChains.isEmpty()) {
            return OtpSyncOutcome.OutboundPaused
        }

        val matchingChains = enabledChains.filter { chain ->
            if (!chain.customRulesEnabled || chain.customFilterRules.isEmpty()) {
                true
            } else {
                OtpParser.findMatchingCustomRule(
                    rawText = rawText,
                    extracted = extracted,
                    rules = chain.customFilterRules
                ) != null
            }
        }

        if (matchingChains.isEmpty()) {
            return OtpSyncOutcome.FilteredByCustomRule(extracted.code, extracted.serviceName)
        }

        val now = System.currentTimeMillis()
        val syncedNames = mutableListOf<String>()
        var duplicateCount = 0
        var lastError: String? = null

        val expiresAt = if (settings.retentionHours <= 0) {
            Long.MAX_VALUE
        } else {
            now + settings.retentionHours * 3_600_000L
        }

        for (chain in matchingChains) {
            val dedupKey = "${chain.chainId}:${extracted.code}"
            if (deduplicate) {
                val previous = recentSyncedCodes[dedupKey]
                if (previous != null && (now - previous) < DEDUP_WINDOW_MS) {
                    duplicateCount++
                    continue
                }
                recentSyncedCodes[dedupKey] = now
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
                if (deduplicate) recentSyncedCodes.remove(dedupKey)
                lastError = err.localizedMessage ?: "Encryption failed"
                continue
            }

            val publishResult = app.relayClient.publishCiphertext(
                relayBaseUrl = chain.relayBaseUrl,
                blindedTopic = chain.blindedTopic,
                ciphertextToken = encrypted
            )

            if (publishResult.isFailure) {
                if (deduplicate) recentSyncedCodes.remove(dedupKey)
                lastError = publishResult.exceptionOrNull()?.localizedMessage ?: "Check network"
                continue
            }

            app.otpRepository.insertIfNew(
                SyncedOtpEntity(
                    messageUuid = msgUuid,
                    serviceName = extracted.serviceName,
                    otpCode = extracted.code,
                    rawSnippet = extracted.snippet,
                    direction = OtpDirection.OUTBOUND.name,
                    peerDeviceName = chain.displayTitle,
                    timestamp = now,
                    expiresAt = expiresAt,
                    isCopied = false,
                    chainId = chain.chainId
                )
            )
            syncedNames.add(chain.displayTitle)
        }

        if (recentSyncedCodes.size > 64) {
            recentSyncedCodes.entries.removeIf { (_, ts) -> (now - ts) > 120_000L }
        }

        return when {
            syncedNames.isNotEmpty() -> OtpSyncOutcome.Synced(
                code = extracted.code,
                serviceName = extracted.serviceName,
                syncedChainNames = syncedNames
            )
            duplicateCount == matchingChains.size -> OtpSyncOutcome.DuplicateSkipped
            else -> OtpSyncOutcome.RelayFailed(lastError ?: "Check network")
        }
    }
}
