package com.example.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress?.trim().orEmpty()
        val fullBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        if (fullBody.isBlank()) return

        val combined = if (sender.isNotBlank()) "$sender: $fullBody" else fullBody
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                OtpSyncCoordinator.processAndSyncText(
                    context = context,
                    rawText = combined,
                    fallbackSender = sender.ifBlank { "SMS" },
                    deduplicate = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
