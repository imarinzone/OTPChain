package com.example.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.E2eeCryptoManager
import com.example.security.KeystoreVault
import com.example.security.PairingPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.chainDataStore: DataStore<Preferences> by preferencesDataStore(name = "codechain_prefs")

data class StoredChainRecord(
    val chainId: String,
    val label: String = "",
    val wrappedMasterKey: String,
    val peerDeviceName: String = "Partner Device",
    val isPeerConfirmed: Boolean = false,
    val relayBaseUrl: String = PairingPayload.DEFAULT_RELAY_URL,
    val isSyncEnabled: Boolean = true,
    val sendOutboundEnabled: Boolean = true,
    val receiveInboundEnabled: Boolean = true,
    val customRulesEnabled: Boolean = true,
    val customFilterRules: List<String> = emptyList()
)

data class ActiveChain(
    val chainId: String,
    val label: String,
    val masterKeyBytes: ByteArray,
    val masterKeyBase64: String,
    val blindedTopic: String,
    val safetyFingerprint: String,
    val peerDeviceName: String,
    val isPeerConfirmed: Boolean,
    val relayBaseUrl: String = PairingPayload.DEFAULT_RELAY_URL,
    val isSyncEnabled: Boolean = true,
    val sendOutboundEnabled: Boolean = true,
    val receiveInboundEnabled: Boolean = true,
    val customRulesEnabled: Boolean = true,
    val customFilterRules: List<String> = emptyList()
) {
    val displayTitle: String
        get() = label.takeIf { it.isNotBlank() }
            ?: peerDeviceName.takeIf { it.isNotBlank() && isPeerConfirmed }
            ?: "Sync ${chainId.take(4).uppercase()}"

    val activeCustomRuleCount: Int
        get() = if (customRulesEnabled) customFilterRules.size else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActiveChain) return false
        return chainId == other.chainId &&
            label == other.label &&
            masterKeyBase64 == other.masterKeyBase64 &&
            blindedTopic == other.blindedTopic &&
            safetyFingerprint == other.safetyFingerprint &&
            peerDeviceName == other.peerDeviceName &&
            isPeerConfirmed == other.isPeerConfirmed &&
            relayBaseUrl == other.relayBaseUrl &&
            isSyncEnabled == other.isSyncEnabled &&
            sendOutboundEnabled == other.sendOutboundEnabled &&
            receiveInboundEnabled == other.receiveInboundEnabled &&
            customRulesEnabled == other.customRulesEnabled &&
            customFilterRules == other.customFilterRules
    }

    override fun hashCode(): Int {
        var result = chainId.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + masterKeyBase64.hashCode()
        result = 31 * result + peerDeviceName.hashCode()
        result = 31 * result + isPeerConfirmed.hashCode()
        result = 31 * result + relayBaseUrl.hashCode()
        result = 31 * result + isSyncEnabled.hashCode()
        result = 31 * result + sendOutboundEnabled.hashCode()
        result = 31 * result + receiveInboundEnabled.hashCode()
        result = 31 * result + customRulesEnabled.hashCode()
        result = 31 * result + customFilterRules.hashCode()
        return result
    }
}

data class ChainSettingsState(
    val localDeviceId: String,
    val localDeviceName: String,
    val chains: List<ActiveChain>,
    val selectedChainId: String?,
    val isSyncEnabled: Boolean,
    val relayBaseUrl: String,
    val retentionHours: Int, // 1, 24, 168, or -1 (manual)
    val notificationsEnabled: Boolean
) {
    val activeChain: ActiveChain?
        get() = chains.firstOrNull { it.chainId == selectedChainId } ?: chains.firstOrNull()

    val sendOutboundEnabled: Boolean
        get() = activeChain?.sendOutboundEnabled ?: true

    val receiveInboundEnabled: Boolean
        get() = activeChain?.receiveInboundEnabled ?: true

    val customRulesEnabled: Boolean
        get() = activeChain?.customRulesEnabled ?: true

    val customFilterRules: List<String>
        get() = activeChain?.customFilterRules ?: emptyList()
}

class ChainPreferencesRepository(private val context: Context) {
    private object Keys {
        val LOCAL_DEVICE_ID = stringPreferencesKey("local_device_id")
        val LOCAL_DEVICE_NAME = stringPreferencesKey("local_device_name")
        // Multi-chain storage
        val CHAINS_JSON = stringPreferencesKey("chains_json")
        val SELECTED_CHAIN_ID = stringPreferencesKey("selected_chain_id")
        // Legacy single-chain keys (kept for automatic migration)
        val CHAIN_ID = stringPreferencesKey("chain_id")
        val WRAPPED_MASTER_KEY = stringPreferencesKey("wrapped_master_key")
        val PEER_DEVICE_NAME = stringPreferencesKey("peer_device_name")
        val PEER_CONFIRMED = booleanPreferencesKey("peer_confirmed")
        val IS_SYNC_ENABLED = booleanPreferencesKey("is_sync_enabled")
        val SEND_OUTBOUND_ENABLED = booleanPreferencesKey("send_outbound_enabled")
        val RECEIVE_INBOUND_ENABLED = booleanPreferencesKey("receive_inbound_enabled")
        val RELAY_BASE_URL = stringPreferencesKey("relay_base_url")
        val RETENTION_HOURS = intPreferencesKey("retention_hours")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CUSTOM_RULES_ENABLED = booleanPreferencesKey("custom_rules_enabled")
        val CUSTOM_FILTER_RULES = stringSetPreferencesKey("custom_filter_rules")
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val chainListType = Types.newParameterizedType(List::class.java, StoredChainRecord::class.java)
    private val chainsAdapter = moshi.adapter<List<StoredChainRecord>>(chainListType)

    private fun readStoredRecords(prefs: Preferences): List<StoredChainRecord> {
        val json = prefs[Keys.CHAINS_JSON]
        if (json != null) {
            return runCatching { chainsAdapter.fromJson(json) }.getOrNull().orEmpty()
        }
        // Migrate legacy single-chain record on the fly
        val legacyChainId = prefs[Keys.CHAIN_ID]
        val legacyWrappedKey = prefs[Keys.WRAPPED_MASTER_KEY]
        if (!legacyChainId.isNullOrBlank() && !legacyWrappedKey.isNullOrBlank()) {
            val legacyPeer = prefs[Keys.PEER_DEVICE_NAME] ?: "Partner Device"
            val legacyConfirmed = prefs[Keys.PEER_CONFIRMED] ?: false
            val legacyRules = (prefs[Keys.CUSTOM_FILTER_RULES] ?: emptySet())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sortedBy { it.lowercase() }
            return listOf(
                StoredChainRecord(
                    chainId = legacyChainId,
                    label = if (legacyConfirmed) legacyPeer else "Sync #1",
                    wrappedMasterKey = legacyWrappedKey,
                    peerDeviceName = legacyPeer,
                    isPeerConfirmed = legacyConfirmed,
                    relayBaseUrl = prefs[Keys.RELAY_BASE_URL] ?: PairingPayload.DEFAULT_RELAY_URL,
                    isSyncEnabled = prefs[Keys.IS_SYNC_ENABLED] ?: true,
                    sendOutboundEnabled = prefs[Keys.SEND_OUTBOUND_ENABLED] ?: true,
                    receiveInboundEnabled = prefs[Keys.RECEIVE_INBOUND_ENABLED] ?: true,
                    customRulesEnabled = prefs[Keys.CUSTOM_RULES_ENABLED] ?: true,
                    customFilterRules = legacyRules
                )
            )
        }
        return emptyList()
    }

    private fun writeStoredRecords(prefs: MutablePreferences, records: List<StoredChainRecord>) {
        prefs[Keys.CHAINS_JSON] = chainsAdapter.toJson(records)
        // Clean legacy keys once migrated to CHAINS_JSON
        prefs.remove(Keys.CHAIN_ID)
        prefs.remove(Keys.WRAPPED_MASTER_KEY)
        prefs.remove(Keys.PEER_DEVICE_NAME)
        prefs.remove(Keys.PEER_CONFIRMED)
    }

    private fun StoredChainRecord.toActiveChain(defaultRelay: String): ActiveChain? {
        val unwrappedB64 = KeystoreVault.unwrapSecret(wrappedMasterKey) ?: return null
        return runCatching {
            val keyBytes = E2eeCryptoManager.decodeBase64Url(unwrappedB64)
            ActiveChain(
                chainId = chainId,
                label = label,
                masterKeyBytes = keyBytes,
                masterKeyBase64 = unwrappedB64,
                blindedTopic = E2eeCryptoManager.deriveBlindedTopic(keyBytes),
                safetyFingerprint = E2eeCryptoManager.deriveSafetyFingerprint(keyBytes),
                peerDeviceName = peerDeviceName.ifBlank { "Partner Device" },
                isPeerConfirmed = isPeerConfirmed,
                relayBaseUrl = relayBaseUrl.trim().trimEnd('/').ifBlank { defaultRelay },
                isSyncEnabled = isSyncEnabled,
                sendOutboundEnabled = sendOutboundEnabled,
                receiveInboundEnabled = receiveInboundEnabled,
                customRulesEnabled = customRulesEnabled,
                customFilterRules = customFilterRules
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }
            )
        }.getOrNull()
    }

    val state: Flow<ChainSettingsState> = context.chainDataStore.data.map { prefs ->
        val deviceId = prefs[Keys.LOCAL_DEVICE_ID] ?: ""
        val defaultName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
        val deviceName = prefs[Keys.LOCAL_DEVICE_NAME] ?: defaultName
        val globalRelay = prefs[Keys.RELAY_BASE_URL] ?: PairingPayload.DEFAULT_RELAY_URL

        val records = readStoredRecords(prefs)
        val activeChains = records.mapNotNull { it.toActiveChain(globalRelay) }
        val savedSelectedId = prefs[Keys.SELECTED_CHAIN_ID]
        val resolvedSelectedId = activeChains.firstOrNull { it.chainId == savedSelectedId }?.chainId
            ?: activeChains.firstOrNull()?.chainId

        ChainSettingsState(
            localDeviceId = deviceId,
            localDeviceName = deviceName,
            chains = activeChains,
            selectedChainId = resolvedSelectedId,
            isSyncEnabled = prefs[Keys.IS_SYNC_ENABLED] ?: true,
            relayBaseUrl = globalRelay,
            retentionHours = prefs[Keys.RETENTION_HOURS] ?: 24,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun ensureDeviceIdInitialized(): String {
        val current = context.chainDataStore.data.first()[Keys.LOCAL_DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val newId = UUID.randomUUID().toString()
        context.chainDataStore.edit { prefs ->
            if (prefs[Keys.LOCAL_DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.LOCAL_DEVICE_ID] = newId
            }
            if (prefs[Keys.LOCAL_DEVICE_NAME].isNullOrBlank()) {
                prefs[Keys.LOCAL_DEVICE_NAME] = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
            }
        }
        return context.chainDataStore.data.first()[Keys.LOCAL_DEVICE_ID] ?: newId
    }

    suspend fun addOrUpdateChain(
        chainId: String,
        masterKeyBase64: String,
        peerDeviceName: String,
        isPeerConfirmed: Boolean,
        label: String? = null,
        relayBaseUrl: String? = null
    ) {
        val wrapped = KeystoreVault.wrapSecret(masterKeyBase64)
        context.chainDataStore.edit { prefs ->
            val current = readStoredRecords(prefs).toMutableList()
            val defaultRelay = prefs[Keys.RELAY_BASE_URL] ?: PairingPayload.DEFAULT_RELAY_URL
            val effectiveRelay = relayBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") }
                ?: defaultRelay

            val existingIdx = current.indexOfFirst { it.chainId == chainId }
            if (existingIdx >= 0) {
                val existing = current[existingIdx]
                current[existingIdx] = existing.copy(
                    label = label?.trim()?.takeIf { it.isNotEmpty() } ?: existing.label,
                    wrappedMasterKey = wrapped,
                    peerDeviceName = peerDeviceName,
                    isPeerConfirmed = isPeerConfirmed,
                    relayBaseUrl = effectiveRelay,
                    isSyncEnabled = true
                )
            } else {
                val nextIndex = current.size + 1
                val resolvedLabel = label?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (isPeerConfirmed && peerDeviceName.isNotBlank()) peerDeviceName else "Sync #$nextIndex"
                current.add(
                    StoredChainRecord(
                        chainId = chainId,
                        label = resolvedLabel,
                        wrappedMasterKey = wrapped,
                        peerDeviceName = peerDeviceName,
                        isPeerConfirmed = isPeerConfirmed,
                        relayBaseUrl = effectiveRelay,
                        isSyncEnabled = true,
                        sendOutboundEnabled = true,
                        receiveInboundEnabled = true,
                        customRulesEnabled = true,
                        customFilterRules = emptyList()
                    )
                )
            }
            writeStoredRecords(prefs, current)
            prefs[Keys.SELECTED_CHAIN_ID] = chainId
            prefs[Keys.IS_SYNC_ENABLED] = true
        }
    }

    suspend fun selectChain(chainId: String) {
        context.chainDataStore.edit { prefs ->
            val current = readStoredRecords(prefs)
            if (current.any { it.chainId == chainId }) {
                prefs[Keys.SELECTED_CHAIN_ID] = chainId
            }
        }
    }

    suspend fun confirmPeer(chainId: String, peerName: String) {
        val cleanPeer = peerName.trim().ifBlank { "Partner Device" }
        updateChainRecord(chainId) { record ->
            val shouldAdoptPeerAsLabel = record.label.isBlank() ||
                record.label.startsWith("Sync #") ||
                record.label.startsWith("Waiting for partner")
            record.copy(
                peerDeviceName = cleanPeer,
                isPeerConfirmed = true,
                label = if (shouldAdoptPeerAsLabel) cleanPeer else record.label
            )
        }
    }

    suspend fun renameChain(chainId: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        updateChainRecord(chainId) { it.copy(label = trimmed) }
    }

    suspend fun removeChain(chainId: String) {
        context.chainDataStore.edit { prefs ->
            val current = readStoredRecords(prefs)
            val remaining = current.filterNot { it.chainId == chainId }
            writeStoredRecords(prefs, remaining)
            if (prefs[Keys.SELECTED_CHAIN_ID] == chainId) {
                val nextSelected = remaining.firstOrNull()?.chainId
                if (nextSelected != null) {
                    prefs[Keys.SELECTED_CHAIN_ID] = nextSelected
                } else {
                    prefs.remove(Keys.SELECTED_CHAIN_ID)
                }
            }
        }
    }

    suspend fun setGlobalSyncEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.IS_SYNC_ENABLED] = enabled }
    }

    suspend fun setChainSyncEnabled(chainId: String, enabled: Boolean) {
        context.chainDataStore.edit { prefs ->
            val current = readStoredRecords(prefs).map { record ->
                if (record.chainId == chainId) record.copy(isSyncEnabled = enabled) else record
            }
            writeStoredRecords(prefs, current)
            if (enabled) {
                prefs[Keys.IS_SYNC_ENABLED] = true
            }
        }
    }

    suspend fun setChainSendOutboundEnabled(chainId: String, enabled: Boolean) {
        updateChainRecord(chainId) { it.copy(sendOutboundEnabled = enabled) }
    }

    suspend fun setChainReceiveInboundEnabled(chainId: String, enabled: Boolean) {
        updateChainRecord(chainId) { it.copy(receiveInboundEnabled = enabled) }
    }

    suspend fun addCustomFilterRule(chainId: String, rule: String) {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return
        updateChainRecord(chainId) { record ->
            val updated = record.customFilterRules
                .filterNot { it.equals(trimmed, ignoreCase = true) }
                .plus(trimmed)
                .sortedBy { it.lowercase() }
            record.copy(
                customFilterRules = updated,
                customRulesEnabled = true
            )
        }
    }

    suspend fun removeCustomFilterRule(chainId: String, rule: String) {
        updateChainRecord(chainId) { record ->
            record.copy(
                customFilterRules = record.customFilterRules
                    .filterNot { it.equals(rule, ignoreCase = true) }
            )
        }
    }

    suspend fun setCustomRulesEnabled(chainId: String, enabled: Boolean) {
        updateChainRecord(chainId) { it.copy(customRulesEnabled = enabled) }
    }

    suspend fun setLocalDeviceName(name: String) {
        context.chainDataStore.edit { it[Keys.LOCAL_DEVICE_NAME] = name.trim().ifBlank { "My Device" } }
    }

    suspend fun setRelayBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/').ifBlank { PairingPayload.DEFAULT_RELAY_URL }
        context.chainDataStore.edit { prefs ->
            prefs[Keys.RELAY_BASE_URL] = cleaned
            val updated = readStoredRecords(prefs).map { it.copy(relayBaseUrl = cleaned) }
            if (updated.isNotEmpty()) {
                writeStoredRecords(prefs, updated)
            }
        }
    }

    suspend fun setRetentionHours(hours: Int) {
        context.chainDataStore.edit { it[Keys.RETENTION_HOURS] = hours }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    private suspend fun updateChainRecord(
        chainId: String,
        transform: (StoredChainRecord) -> StoredChainRecord
    ) {
        context.chainDataStore.edit { prefs ->
            val current = readStoredRecords(prefs)
            if (current.none { it.chainId == chainId }) return@edit
            val updated = current.map { record ->
                if (record.chainId == chainId) transform(record) else record
            }
            writeStoredRecords(prefs, updated)
        }
    }
}
