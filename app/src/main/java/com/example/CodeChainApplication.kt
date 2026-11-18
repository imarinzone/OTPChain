package com.example

import android.app.Application
import com.example.data.local.CodeChainDatabase
import com.example.data.local.SyncedOtpRepository
import com.example.data.prefs.ChainPreferencesRepository
import com.example.data.relay.EphemeralRelayClient
import com.example.notifications.OtpNotificationHelper

class CodeChainApplication : Application() {
    lateinit var database: CodeChainDatabase
        private set
    lateinit var otpRepository: SyncedOtpRepository
        private set
    lateinit var preferencesRepository: ChainPreferencesRepository
        private set
    lateinit var relayClient: EphemeralRelayClient
        private set
    lateinit var notificationHelper: OtpNotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        database = CodeChainDatabase.getInstance(this)
        otpRepository = SyncedOtpRepository(database.syncedOtpDao())
        preferencesRepository = ChainPreferencesRepository(this)
        relayClient = EphemeralRelayClient()
        notificationHelper = OtpNotificationHelper(this).apply {
            ensureChannelCreated()
        }
    }
}
