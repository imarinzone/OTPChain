package com.example.otp

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtpNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // Never intercept CodeChain's own notifications
        if (pkg == packageName) return

        // Ignore ongoing persistent notifications
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
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()
        val effectiveTitle = bigTitle.ifBlank { title }.ifBlank { conversationTitle }

        val bodyParts = mutableListOf<String>()

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { bodyParts.add(it) }

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { bodyParts.add(it) }

        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { bodyParts.add(it) }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { bodyParts.add(it) }
        }

        // MessagingStyle notifications (Google Messages, Samsung Messages, RCS, etc.)
        @Suppress("DEPRECATION")
        val parcelableMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        } else {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.filterIsInstance<Bundle>()?.toTypedArray()
        }

        parcelableMessages?.forEach { msgBundle ->
            val legacySender = msgBundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
            val personSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    msgBundle.getParcelable("sender_person", Person::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    msgBundle.getParcelable("sender_person")
                }
                person?.name?.toString()?.trim().orEmpty()
            } else {
                ""
            }
            val sender = personSender.ifBlank { legacySender }
            val msgText = msgBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (msgText.isNotBlank()) {
                if (sender.isNotBlank()) {
                    bodyParts.add("$sender: $msgText")
                } else {
                    bodyParts.add(msgText)
                }
            }
        }

        val bodyText = bodyParts.distinct().joinToString(separator = " ")
        val combined = when {
            effectiveTitle.isNotBlank() && bodyText.isNotBlank() -> "$effectiveTitle: $bodyText"
            bodyText.isNotBlank() -> bodyText
            else -> effectiveTitle
        }

        return ExtractedNotificationText(
            title = effectiveTitle,
            combinedText = combined
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        serviceScope.cancel()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
