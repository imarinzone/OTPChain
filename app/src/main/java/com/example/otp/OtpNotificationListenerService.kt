package com.example.otp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.CodeChainApplication
import com.example.data.local.OtpDirection
import com.example.data.local.SyncedOtpEntity
import com.example.data.relay.RelayEnvelope
import com.example.security.E2eeCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OtpNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentFingerprints = ConcurrentHashMap<String, Long>()

    private val smsPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
        "com.oneplus.mms",
        "com.miui.mms",
        "com.sonyericsson.conversations",
        "com.htc.sense.mms",
        "com.asus.message",
        "org.thoughtcrime.securesms",
        "com.textra",
        "xyz.klinker.messenger",
        "com.chomp.sms"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // Never intercept our own notifications (prevents sync feedback loops)
        if (pkg == packageName) return

        // Ignore group summaries and ongoing persistent notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val isMessagingApp = pkg in smsPackages ||
            notification.category == Notification.CATEGORY_MESSAGE ||
            pkg.contains("messaging", ignoreCase = true) ||
            pkg.contains("mms", ignoreCase = true) ||
            pkg.contains("sms", ignoreCase = true)

        if (!isMessagingApp) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val body = bigText.ifBlank { text }
        if (body.isBlank()) return

        val combined = if (title.isNotBlank()) "$title: $body" else body
        val extracted = OtpParser.extractOtp(combined, fallbackSender = title.ifBlank { "SMS Alert" })
            ?: return

        // Deduplicate rapid notification updates for the same OTP within 20 seconds
        val dedupKey = "${extracted.serviceName}|${extracted.code}"
        val now = System.currentTimeMillis()
        val previous = recentFingerprints[dedupKey]
        if (previous != null && (now - previous) < 20_000L) {
            return
        }
        recentFingerprints[dedupKey] = now
        pruneOldFingerprints(now)

        val app = applicationContext as? CodeChainApplication ?: return
        serviceScope.launch {
            val deviceId = app.preferencesRepository.ensureDeviceIdInitialized()
            val settings = app.preferencesRepository.state.first()
            val chain = settings.activeChain ?: return@launch
            if (!settings.isSyncEnabled || !settings.sendOutboundEnabled) return@launch

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
            }.getOrNull() ?: return@launch

            val publishResult = app.relayClient.publishCiphertext(
                relayBaseUrl = settings.relayBaseUrl,
                blindedTopic = chain.blindedTopic,
                ciphertextToken = encrypted
            )

            if (publishResult.isSuccess) {
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
            }
        }
    }

    private fun pruneOldFingerprints(now: Long) {
        if (recentFingerprints.size > 32) {
            recentFingerprints.entries.removeIf { (_, ts) -> (now - ts) > 60_000L }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
