package com.example.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.CodeChainDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_OTP) return
        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
        val serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME) ?: "OTP"
        val messageUuid = intent.getStringExtra(EXTRA_MESSAGE_UUID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        ClipboardHelper.copyOtpCode(context, "$serviceName 2FA Code", code)
        Toast.makeText(context, "Copied $code to clipboard", Toast.LENGTH_SHORT).show()

        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        }

        if (!messageUuid.isNullOrBlank()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CodeChainDatabase.getInstance(context)
                        .syncedOtpDao()
                        .markCopiedByUuid(messageUuid)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY_OTP = "com.example.notifications.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        const val EXTRA_MESSAGE_UUID = "extra_message_uuid"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
