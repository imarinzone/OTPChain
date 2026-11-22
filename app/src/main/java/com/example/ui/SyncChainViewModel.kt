package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CodeChainApplication
import com.example.data.local.SyncedOtpEntity
import com.example.data.prefs.ActiveChain
import com.example.data.prefs.ChainSettingsState
import com.example.data.relay.RelayConnectionState
import com.example.data.relay.RelayEnvelope
import com.example.notifications.ClipboardHelper
import com.example.otp.OtpSyncCoordinator
import com.example.otp.OtpSyncOutcome
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
    val chainRelayStates: Map<String, RelayConnectionState> = emptyMap(),
    val isSending: Boolean = false
) {
    val chains: List<ActiveChain>
        get() = settings?.chains.orEmpty()

    val selectedChain: ActiveChain?
        get() = settings?.activeChain

    fun relayStateFor(chainId: String?): RelayConnectionState {
        if (chainId == null) return RelayConnectionState.Unpaired
        return chainRelayStates[chainId] ?: RelayConnectionState.Unpaired
    }
}

class SyncChainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CodeChainApplication
    private val prefsRepo = app.preferencesRepository
    private val otpRepo = app.otpRepository
    private val relayClient = app.relayClient

    private val _isSending = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _pendingSharedSms = MutableStateFlow<String?>(null)
    val pendingSharedSms: StateFlow<String?> = _pendingSharedSms.asStateFlow()

    val uiState: StateFlow<CodeChainUiState> = combine(
        prefsRepo.state,
        otpRepo.allOtps,
        relayClient.chainStates,
        _isSending
    ) { settings, otps, relayStates, isSending ->
        CodeChainUiState(
            settings = settings,
            otps = otps,
            chainRelayStates = relayStates,
            isSending = isSending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CodeChainUiState()
    )

    init {
        viewModelScope.launch {
            app.appEvents.collect { msg ->
                _events.tryEmit(msg)
            }
        }
    }

    fun startNewChain(customLabel: String? = null): String {
        val chainId = UUID.randomUUID().toString().replace("-", "").take(16)
        viewModelScope.launch {
            prefsRepo.ensureDeviceIdInitialized()
            val masterKey = E2eeCryptoManager.generateMasterKey()
            val keyB64 = E2eeCryptoManager.encodeBase64Url(masterKey)
            val existingCount = uiState.value.chains.size
            val resolvedLabel = customLabel?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Sync #${existingCount + 1}"
            prefsRepo.addOrUpdateChain(
                chainId = chainId,
                masterKeyBase64 = keyB64,
                peerDeviceName = "Waiting for partner scan...",
                isPeerConfirmed = false,
                label = resolvedLabel
            )
            _events.tryEmit("Created \"$resolvedLabel\" (256-bit E2EE)")
        }
        return chainId
    }

    fun joinChainFromQr(rawQrText: String, customLabel: String? = null): Boolean {
        val parsed = PairingPayload.parse(rawQrText)
        if (parsed.isFailure) {
            _events.tryEmit(parsed.exceptionOrNull()?.message ?: "Invalid OTP Chain QR code")
            return false
        }
        val payload = parsed.getOrThrow()
        viewModelScope.launch {
            val deviceId = prefsRepo.ensureDeviceIdInitialized()
            val settings = uiState.value.settings
            val label = customLabel?.trim()?.takeIf { it.isNotEmpty() } ?: payload.peerDeviceName
            prefsRepo.addOrUpdateChain(
                chainId = payload.chainId,
                masterKeyBase64 = payload.masterKeyBase64,
                peerDeviceName = payload.peerDeviceName,
                isPeerConfirmed = true,
                label = label,
                relayBaseUrl = payload.relayBaseUrl
            )
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
            _events.tryEmit("Joined E2EE sync \"$label\"")
        }
        return true
    }

    fun selectChain(chainId: String) {
        if (uiState.value.settings?.selectedChainId == chainId) return
        viewModelScope.launch {
            prefsRepo.selectChain(chainId)
        }
    }

    fun renameChain(chainId: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            prefsRepo.renameChain(chainId, trimmed)
            _events.tryEmit("Renamed sync to \"$trimmed\"")
        }
    }

    fun buildPairingUri(chainId: String? = null): String? {
        val settings = uiState.value.settings ?: return null
        val chain = if (chainId != null) {
            settings.chains.firstOrNull { it.chainId == chainId }
        } else {
            settings.activeChain
        } ?: return null

        return PairingPayload(
            chainId = chain.chainId,
            masterKeyBase64 = chain.masterKeyBase64,
            peerDeviceName = settings.localDeviceName,
            relayBaseUrl = chain.relayBaseUrl
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

    fun extractAndSyncOtp(
        rawText: String,
        senderHint: String = "SMS Alert",
        targetChainId: String? = null
    ) {
        viewModelScope.launch {
            _isSending.value = true
            try {
                when (
                    val outcome = OtpSyncCoordinator.processAndSyncText(
                        context = app,
                        rawText = rawText,
                        fallbackSender = senderHint,
                        deduplicate = false,
                        targetChainId = targetChainId
                    )
                ) {
                    is OtpSyncOutcome.Synced -> {
                        val dest = if (outcome.syncedChainNames.size == 1) {
                            outcome.syncedChainNames.first()
                        } else {
                            "${outcome.syncedChainNames.size} syncs"
                        }
                        _events.tryEmit("Encrypted & synced ${outcome.code} to $dest")
                    }
                    is OtpSyncOutcome.FilteredByCustomRule ->
                        _events.tryEmit("Skipped ${outcome.code} — didn't match filter rules")
                    OtpSyncOutcome.NoOtpFound ->
                        _events.tryEmit("No 4–8 digit 2FA code found in message")
                    OtpSyncOutcome.NoActiveChain ->
                        _events.tryEmit("Add a sync on the Chain tab before syncing")
                    OtpSyncOutcome.OutboundPaused ->
                        _events.tryEmit("Outbound sync is paused")
                    is OtpSyncOutcome.RelayFailed ->
                        _events.tryEmit("Relay send failed: ${outcome.reason}")
                    OtpSyncOutcome.DuplicateSkipped -> Unit
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendEncryptedPing(chainId: String? = null) {
        viewModelScope.launch {
            val settings = uiState.value.settings ?: return@launch
            val chain = if (chainId != null) {
                settings.chains.firstOrNull { it.chainId == chainId }
            } else {
                settings.activeChain
            } ?: return@launch

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
                    chain.relayBaseUrl,
                    chain.blindedTopic,
                    encrypted
                )
                if (result.isSuccess) {
                    _events.tryEmit("Encrypted test ping sent to ${chain.displayTitle}")
                } else {
                    _events.tryEmit("Ping failed: ${result.exceptionOrNull()?.localizedMessage}")
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun severChain(chainId: String, clearHistory: Boolean) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val chain = settings?.chains?.firstOrNull { it.chainId == chainId }
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
                    relayClient.publishCiphertext(chain.relayBaseUrl, chain.blindedTopic, encrypted)
                }
            }
            prefsRepo.removeChain(chainId)
            if (clearHistory) {
                otpRepo.deleteByChainId(chainId)
            }
            _events.tryEmit("Removed sync \"${chain?.displayTitle ?: "Chain"}\" and wiped its key")
        }
    }

    fun setGlobalSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setGlobalSyncEnabled(enabled)
            _events.tryEmit(if (enabled) "All syncs resumed" else "All syncs paused on this device")
        }
    }

    fun setChainSyncEnabled(chainId: String, enabled: Boolean) {
        viewModelScope.launch {
            val label = uiState.value.chains.firstOrNull { it.chainId == chainId }?.displayTitle ?: "Sync"
            prefsRepo.setChainSyncEnabled(chainId, enabled)
            _events.tryEmit(if (enabled) "$label resumed" else "$label paused")
        }
    }

    fun setChainSendOutboundEnabled(chainId: String, enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setChainSendOutboundEnabled(chainId, enabled) }
    }

    fun setChainReceiveInboundEnabled(chainId: String, enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setChainReceiveInboundEnabled(chainId, enabled) }
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

    fun addCustomRule(chainId: String, rule: String) {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            prefsRepo.addCustomFilterRule(chainId, trimmed)
            _events.tryEmit("Added rule \"$trimmed\"")
        }
    }

    fun removeCustomRule(chainId: String, rule: String) {
        viewModelScope.launch {
            prefsRepo.removeCustomFilterRule(chainId, rule)
        }
    }

    fun setCustomRulesEnabled(chainId: String, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setCustomRulesEnabled(chainId, enabled)
            _events.tryEmit(
                if (enabled) "Filter rules enabled for this sync" else "Filter rules paused for this sync"
            )
        }
    }
}
