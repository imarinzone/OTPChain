package com.example.otp

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OtpNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // Never intercept CodeChain's own synced notifications
        if (pkg == packageName) return

        // Ignore ongoing persistent system status notifications (e.g. battery, VPN, music)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val extractedText = extractAllNotificationText(extras)
        if (extractedText.combinedText.isBlank()) return

        serviceScope.launch {
            OtpSyncCoordinator.processAndSyncText(
                context = applicationContext,
                rawText = extractedText.combinedText,
                fallbackSender = extractedText.title.ifBlank { "SMS Notification" },
                deduplicate = true
            )
        }
    }

    private data class ExtractedNotificationText(
        val title: String,
        val combinedText: String
    )

    private fun extractAllNotificationText(extras: Bundle): ExtractedNotificationText {
        val parts = mutableListOf<String>()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()

        val effectiveTitle = bigTitle.ifBlank { title }.ifBlank { conversationTitle }
        if (effectiveTitle.isNotBlank()) parts.add(effectiveTitle)

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }

        // MessagingStyle notifications (Google Messages, Samsung Messages, RCS, etc.) store messages in EXTRA_MESSAGES
        @Suppress("DEPRECATION")
        val parcelableMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        } else {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.filterIsInstance<Bundle>()?.toTypedArray()
        }

        parcelableMessages?.forEach { msgBundle ->
            val sender = msgBundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
            val msgText = msgBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (msgText.isNotBlank()) {
                if (sender.isNotBlank()) {
                    parts.add("$sender: $msgText")
                } else {
                    parts.add(msgText)
                }
            }
        }

        return ExtractedNotificationText(
            title = effectiveTitle,
            combinedText = parts.distinct().joinToString(separator = " ")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
