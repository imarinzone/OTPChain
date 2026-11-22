package com.example

import android.app.Application
import com.example.data.local.CodeChainDatabase
import com.example.data.local.OtpDirection
import com.example.data.local.SyncedOtpEntity
import com.example.data.local.SyncedOtpRepository
import com.example.data.prefs.ChainPreferencesRepository
import com.example.data.relay.EphemeralRelayClient
import com.example.data.relay.IncomingRelayMessage
import com.example.data.relay.RelaySubscription
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

        // Keep WebSocket relay subscriptions and inbound decryption active for all chains
        appScope.launch {
            preferencesRepository.ensureDeviceIdInitialized()
            otpRepository.purgeExpired()
            preferencesRepository.state.collect { settings ->
                val subscriptions = settings.chains.map { chain ->
                    RelaySubscription(
                        chainId = chain.chainId,
                        relayBaseUrl = chain.relayBaseUrl,
                        blindedTopic = chain.blindedTopic,
                        shouldListen = settings.isSyncEnabled &&
                            chain.isSyncEnabled &&
                            chain.receiveInboundEnabled
                    )
                }
                relayClient.updateSubscriptions(subscriptions)
                SyncQuickTileService.requestTileUpdate(this@CodeChainApplication)
            }
        }

        appScope.launch {
            relayClient.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private suspend fun handleIncomingMessage(message: IncomingRelayMessage) {
        val settings = preferencesRepository.state.first()
        val chain = settings.chains.firstOrNull { it.chainId == message.chainId } ?: return

        val decryptedJson = runCatching {
            E2eeCryptoManager.decrypt(message.ciphertext, chain.masterKeyBytes, chain.chainId)
        }.getOrNull() ?: return

        val envelope = relayClient.decodeEnvelope(decryptedJson) ?: return
        if (envelope.senderDeviceId == settings.localDeviceId) {
            return
        }

        when (envelope.type) {
            "PAIR_ACK" -> {
                preferencesRepository.confirmPeer(chain.chainId, envelope.senderDeviceName)
                _appEvents.tryEmit("Sync chain verified with ${envelope.senderDeviceName}")
            }
            "PING" -> {
                preferencesRepository.confirmPeer(chain.chainId, envelope.senderDeviceName)
                _appEvents.tryEmit("Verified E2EE ping from ${envelope.senderDeviceName}")
            }
            "UNPAIR" -> {
                _appEvents.tryEmit("${envelope.senderDeviceName} turned off or severed ${chain.displayTitle}")
            }
            "OTP" -> {
                if (!settings.isSyncEnabled || !chain.isSyncEnabled || !chain.receiveInboundEnabled) return
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
                        isCopied = false,
                        chainId = chain.chainId
                    )
                )
                if (inserted && settings.notificationsEnabled) {
                    notificationHelper.showSyncedOtpNotification(
                        messageUuid = envelope.messageUuid,
                        serviceName = envelope.serviceName.ifBlank { "2FA Code" },
                        otpCode = envelope.otpCode,
                        peerDeviceName = envelope.senderDeviceName.ifBlank { chain.displayTitle }
                    )
                }
            }
        }
    }
}
