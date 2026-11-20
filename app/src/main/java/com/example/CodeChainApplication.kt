package com.example

import android.app.Application
import com.example.data.local.CodeChainDatabase
import com.example.data.local.OtpDirection
import com.example.data.local.SyncedOtpEntity
import com.example.data.local.SyncedOtpRepository
import com.example.data.prefs.ChainPreferencesRepository
import com.example.data.relay.EphemeralRelayClient
import com.example.notifications.OtpNotificationHelper
import com.example.security.E2eeCryptoManager
import com.example.tile.SyncQuickTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CodeChainApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val _appEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val appEvents: SharedFlow<String> = _appEvents.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        database = CodeChainDatabase.getInstance(this)
        otpRepository = SyncedOtpRepository(database.syncedOtpDao())
        preferencesRepository = ChainPreferencesRepository(this)
        relayClient = EphemeralRelayClient()
        notificationHelper = OtpNotificationHelper(this).apply {
            ensureChannelCreated()
        }

        // Keep WebSocket relay subscription and inbound decryption active at Application scope
        appScope.launch {
            preferencesRepository.ensureDeviceIdInitialized()
            otpRepository.purgeExpired()
            preferencesRepository.state.collect { settings ->
                val chain = settings.activeChain
                relayClient.updateSubscription(
                    hasChain = chain != null,
                    shouldListen = settings.isSyncEnabled && settings.receiveInboundEnabled,
                    relayBaseUrl = settings.relayBaseUrl,
                    blindedTopic = chain?.blindedTopic
                )
                SyncQuickTileService.requestTileUpdate(this@CodeChainApplication)
            }
        }

        appScope.launch {
            relayClient.incomingCiphertexts.collect { ciphertext ->
                handleIncomingCiphertext(ciphertext)
            }
        }
    }

    private suspend fun handleIncomingCiphertext(ciphertext: String) {
        val settings = preferencesRepository.state.first()
        val chain = settings.activeChain ?: return

        val decryptedJson = runCatching {
            E2eeCryptoManager.decrypt(ciphertext, chain.masterKeyBytes, chain.chainId)
        }.getOrNull() ?: return

        val envelope = relayClient.decodeEnvelope(decryptedJson) ?: return
        if (envelope.senderDeviceId == settings.localDeviceId) {
            return
        }

        when (envelope.type) {
            "PAIR_ACK" -> {
                preferencesRepository.confirmPeer(envelope.senderDeviceName)
                _appEvents.tryEmit("Sync chain verified with ${envelope.senderDeviceName}")
            }
            "PING" -> {
                preferencesRepository.confirmPeer(envelope.senderDeviceName)
                _appEvents.tryEmit("Verified E2EE ping from ${envelope.senderDeviceName}")
            }
            "UNPAIR" -> {
                _appEvents.tryEmit("${envelope.senderDeviceName} turned off or severed this chain")
            }
            "OTP" -> {
                if (!settings.isSyncEnabled || !settings.receiveInboundEnabled) return
                val now = System.currentTimeMillis()
                val expiresAt = if (settings.retentionHours <= 0) {
                    Long.MAX_VALUE
                } else {
                    now + settings.retentionHours * 3_600_000L
                }
                val inserted = otpRepository.insertIfNew(
                    SyncedOtpEntity(
                        messageUuid = envelope.messageUuid,
                        serviceName = envelope.serviceName.ifBlank { "2FA Verification" },
                        otpCode = envelope.otpCode,
                        rawSnippet = envelope.snippet,
                        direction = OtpDirection.INBOUND.name,
                        peerDeviceName = envelope.senderDeviceName.ifBlank { chain.peerDeviceName },
                        timestamp = envelope.sentAtEpochMs.takeIf { it > 0 } ?: now,
                        expiresAt = expiresAt,
                        isCopied = false
                    )
                )
                if (inserted && settings.notificationsEnabled) {
                    notificationHelper.showSyncedOtpNotification(
                        messageUuid = envelope.messageUuid,
                        serviceName = envelope.serviceName.ifBlank { "2FA Code" },
                        otpCode = envelope.otpCode,
                        peerDeviceName = envelope.senderDeviceName.ifBlank { chain.peerDeviceName }
                    )
                }
            }
        }
    }
}
