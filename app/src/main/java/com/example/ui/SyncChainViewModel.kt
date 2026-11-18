package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CodeChainApplication
import com.example.data.local.OtpDirection
import com.example.data.local.SyncedOtpEntity
import com.example.data.prefs.ChainSettingsState
import com.example.data.relay.RelayConnectionState
import com.example.data.relay.RelayEnvelope
import com.example.notifications.ClipboardHelper
import com.example.otp.OtpParser
import com.example.security.E2eeCryptoManager
import com.example.security.PairingPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CodeChainUiState(
    val settings: ChainSettingsState? = null,
    val otps: List<SyncedOtpEntity> = emptyList(),
    val relayState: RelayConnectionState = RelayConnectionState.Unpaired,
    val isSending: Boolean = false,
    val lastDetectedPreview: String? = null
)

class SyncChainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CodeChainApplication
    private val prefsRepo = app.preferencesRepository
    private val otpRepo = app.otpRepository
    private val relayClient = app.relayClient
    private val notificationHelper = app.notificationHelper

    private val _isSending = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _pendingSharedSms = MutableStateFlow<String?>(null)
    val pendingSharedSms: StateFlow<String?> = _pendingSharedSms.asStateFlow()

    val uiState: StateFlow<CodeChainUiState> = combine(
        prefsRepo.state,
        otpRepo.allOtps,
        relayClient.connectionState,
        _isSending
    ) { settings, otps, relayState, isSending ->
        CodeChainUiState(
            settings = settings,
            otps = otps,
            relayState = relayState,
            isSending = isSending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CodeChainUiState()
    )

    init {
        viewModelScope.launch {
            prefsRepo.ensureDeviceIdInitialized()
            otpRepo.purgeExpired()
        }

        // Keep WebSocket relay subscription synchronized with local settings & toggles
        viewModelScope.launch {
            prefsRepo.state.collect { settings ->
                val chain = settings.activeChain
                relayClient.updateSubscription(
                    hasChain = chain != null,
                    shouldListen = settings.isSyncEnabled && settings.receiveInboundEnabled,
                    relayBaseUrl = settings.relayBaseUrl,
                    blindedTopic = chain?.blindedTopic
                )
            }
        }

        // Listen for incoming E2EE frames
        viewModelScope.launch {
            relayClient.incomingCiphertexts.collect { ciphertext ->
                handleIncomingCiphertext(ciphertext)
            }
        }
    }

    private suspend fun handleIncomingCiphertext(ciphertext: String) {
        val settings = uiState.value.settings ?: return
        val chain = settings.activeChain ?: return

        val decryptedJson = runCatching {
            E2eeCryptoManager.decrypt(ciphertext, chain.masterKeyBytes, chain.chainId)
        }.getOrNull() ?: return // Ignore undecryptable packets cleanly

        val envelope = relayClient.decodeEnvelope(decryptedJson) ?: return
        if (envelope.senderDeviceId == settings.localDeviceId) {
            return // Ignore own echoes
        }

        when (envelope.type) {
            "PAIR_ACK" -> {
                prefsRepo.confirmPeer(envelope.senderDeviceName)
                _events.tryEmit("Sync chain verified with ${envelope.senderDeviceName}")
            }
            "PING" -> {
                prefsRepo.confirmPeer(envelope.senderDeviceName)
                _events.tryEmit("Verified E2EE ping from ${envelope.senderDeviceName}")
            }
            "UNPAIR" -> {
                _events.tryEmit("${envelope.senderDeviceName} turned off or severed this chain")
            }
            "OTP" -> {
                if (!settings.isSyncEnabled || !settings.receiveInboundEnabled) return
                val now = System.currentTimeMillis()
                val expiresAt = computeExpiry(now, settings.retentionHours)
                val inserted = otpRepo.insertIfNew(
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

    fun startNewChain() {
        viewModelScope.launch {
            prefsRepo.ensureDeviceIdInitialized()
            val masterKey = E2eeCryptoManager.generateMasterKey()
            val keyB64 = E2eeCryptoManager.encodeBase64Url(masterKey)
            val chainId = UUID.randomUUID().toString().replace("-", "").take(16)
            prefsRepo.saveNewChain(
                chainId = chainId,
                masterKeyBase64 = keyB64,
                peerDeviceName = "Waiting for partner scan...",
                isPeerConfirmed = false
            )
            _events.tryEmit("New 256-bit E2EE sync chain created")
        }
    }

    fun joinChainFromQr(rawQrText: String): Boolean {
        val parsed = PairingPayload.parse(rawQrText)
        if (parsed.isFailure) {
            _events.tryEmit(parsed.exceptionOrNull()?.message ?: "Invalid CodeChain QR code")
            return false
        }
        val payload = parsed.getOrThrow()
        viewModelScope.launch {
            val deviceId = prefsRepo.ensureDeviceIdInitialized()
            val settings = uiState.value.settings
            prefsRepo.saveNewChain(
                chainId = payload.chainId,
                masterKeyBase64 = payload.masterKeyBase64,
                peerDeviceName = payload.peerDeviceName,
                isPeerConfirmed = true,
                relayBaseUrl = payload.relayBaseUrl
            )
            // Transmit encrypted PAIR_ACK so host confirms pairing immediately
            val keyBytes = E2eeCryptoManager.decodeBase64Url(payload.masterKeyBase64)
            val topic = E2eeCryptoManager.deriveBlindedTopic(keyBytes)
            val ack = RelayEnvelope(
                messageUuid = UUID.randomUUID().toString(),
                type = "PAIR_ACK",
                senderDeviceId = deviceId,
                senderDeviceName = settings?.localDeviceName ?: "Partner Device",
                sentAtEpochMs = System.currentTimeMillis()
            )
            val encrypted = E2eeCryptoManager.encrypt(
                relayClient.encodeEnvelope(ack),
                keyBytes,
                payload.chainId
            )
            relayClient.publishCiphertext(payload.relayBaseUrl, topic, encrypted)
            _events.tryEmit("Joined E2EE chain with ${payload.peerDeviceName}")
        }
        return true
    }

    fun buildCurrentPairingUri(): String? {
        val settings = uiState.value.settings ?: return null
        val chain = settings.activeChain ?: return null
        return PairingPayload(
            chainId = chain.chainId,
            masterKeyBase64 = chain.masterKeyBase64,
            peerDeviceName = settings.localDeviceName,
            relayBaseUrl = settings.relayBaseUrl
        ).toUriString()
    }

    fun receiveIncomingShareIntent(sharedText: String) {
        if (sharedText.isNotBlank()) {
            _pendingSharedSms.value = sharedText
        }
    }

    fun clearPendingSharedSms() {
        _pendingSharedSms.value = null
    }

    fun extractAndSyncOtp(rawText: String, senderHint: String = "SMS Alert") {
        viewModelScope.launch {
            val extracted = OtpParser.extractOtp(rawText, senderHint)
            if (extracted == null) {
                _events.tryEmit("No 4–8 digit 2FA code found in message")
                return@launch
            }
            val settings = uiState.value.settings
            val chain = settings?.activeChain
            if (chain == null) {
                _events.tryEmit("Pair a device on the Chain tab before syncing")
                return@launch
            }
            if (!settings.isSyncEnabled || !settings.sendOutboundEnabled) {
                _events.tryEmit("Outbound sync is paused on this device")
                return@launch
            }

            _isSending.value = true
            try {
                val now = System.currentTimeMillis()
                val msgUuid = UUID.randomUUID().toString()
                val envelope = RelayEnvelope(
                    messageUuid = msgUuid,
                    type = "OTP",
                    senderDeviceId = settings.localDeviceId,
                    senderDeviceName = settings.localDeviceName,
                    otpCode = extracted.code,
                    serviceName = extracted.serviceName,
                    snippet = extracted.snippet,
                    sentAtEpochMs = now
                )
                val encrypted = E2eeCryptoManager.encrypt(
                    plaintext = relayClient.encodeEnvelope(envelope),
                    masterKey = chain.masterKeyBytes,
                    chainId = chain.chainId
                )
                val result = relayClient.publishCiphertext(
                    relayBaseUrl = settings.relayBaseUrl,
                    blindedTopic = chain.blindedTopic,
                    ciphertextToken = encrypted
                )
                if (result.isSuccess) {
                    otpRepo.insertIfNew(
                        SyncedOtpEntity(
                            messageUuid = msgUuid,
                            serviceName = extracted.serviceName,
                            otpCode = extracted.code,
                            rawSnippet = extracted.snippet,
                            direction = OtpDirection.OUTBOUND.name,
                            peerDeviceName = chain.peerDeviceName,
                            timestamp = now,
                            expiresAt = computeExpiry(now, settings.retentionHours),
                            isCopied = false
                        )
                    )
                    _events.tryEmit("Encrypted & synced ${extracted.code} (${extracted.serviceName})")
                } else {
                    _events.tryEmit(
                        "Relay send failed: ${result.exceptionOrNull()?.localizedMessage ?: "Check network"}"
                    )
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendEncryptedPing() {
        viewModelScope.launch {
            val settings = uiState.value.settings ?: return@launch
            val chain = settings.activeChain ?: return@launch
            _isSending.value = true
            try {
                val envelope = RelayEnvelope(
                    messageUuid = UUID.randomUUID().toString(),
                    type = "PING",
                    senderDeviceId = settings.localDeviceId,
                    senderDeviceName = settings.localDeviceName,
                    sentAtEpochMs = System.currentTimeMillis()
                )
                val encrypted = E2eeCryptoManager.encrypt(
                    relayClient.encodeEnvelope(envelope),
                    chain.masterKeyBytes,
                    chain.chainId
                )
                val result = relayClient.publishCiphertext(
                    settings.relayBaseUrl,
                    chain.blindedTopic,
                    encrypted
                )
                if (result.isSuccess) {
                    _events.tryEmit("Encrypted test ping sent over zero-cache relay")
                } else {
                    _events.tryEmit("Ping failed: ${result.exceptionOrNull()?.localizedMessage}")
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun severChain(clearHistory: Boolean) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val chain = settings?.activeChain
            if (settings != null && chain != null) {
                runCatching {
                    val unpair = RelayEnvelope(
                        messageUuid = UUID.randomUUID().toString(),
                        type = "UNPAIR",
                        senderDeviceId = settings.localDeviceId,
                        senderDeviceName = settings.localDeviceName,
                        sentAtEpochMs = System.currentTimeMillis()
                    )
                    val encrypted = E2eeCryptoManager.encrypt(
                        relayClient.encodeEnvelope(unpair),
                        chain.masterKeyBytes,
                        chain.chainId
                    )
                    relayClient.publishCiphertext(settings.relayBaseUrl, chain.blindedTopic, encrypted)
                }
            }
            prefsRepo.clearChain()
            if (clearHistory) {
                otpRepo.clearAll()
            }
            _events.tryEmit("Sync chain severed and local keys wiped")
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSyncEnabled(enabled)
            _events.tryEmit(if (enabled) "Sync resumed on this device" else "Sync paused on this device")
        }
    }

    fun setSendOutboundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSendOutboundEnabled(enabled) }
    }

    fun setReceiveInboundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setReceiveInboundEnabled(enabled) }
    }

    fun setLocalDeviceName(name: String) {
        viewModelScope.launch { prefsRepo.setLocalDeviceName(name) }
    }

    fun setRelayBaseUrl(url: String) {
        viewModelScope.launch {
            prefsRepo.setRelayBaseUrl(url)
            _events.tryEmit("Relay endpoint updated")
        }
    }

    fun setRetentionHours(hours: Int) {
        viewModelScope.launch {
            prefsRepo.setRetentionHours(hours)
            otpRepo.purgeExpired()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotificationsEnabled(enabled) }
    }

    fun copyOtp(entity: SyncedOtpEntity) {
        ClipboardHelper.copyOtpCode(app, "${entity.serviceName} 2FA Code", entity.otpCode)
        viewModelScope.launch {
            otpRepo.markCopiedById(entity.id)
        }
        _events.tryEmit("Copied ${entity.otpCode} to clipboard")
    }

    fun deleteOtp(id: Long) {
        viewModelScope.launch { otpRepo.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            otpRepo.clearAll()
            _events.tryEmit("Local OTP history cleared")
        }
    }

    private fun computeExpiry(now: Long, retentionHours: Int): Long {
        return if (retentionHours <= 0) Long.MAX_VALUE else now + retentionHours * 3_600_000L
    }
}
