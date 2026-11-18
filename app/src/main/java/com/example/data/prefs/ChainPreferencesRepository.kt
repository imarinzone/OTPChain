package com.example.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.chainDataStore: DataStore<Preferences> by preferencesDataStore(name = "codechain_prefs")

data class ActiveChain(
    val chainId: String,
    val masterKeyBytes: ByteArray,
    val masterKeyBase64: String,
    val blindedTopic: String,
    val safetyFingerprint: String,
    val peerDeviceName: String,
    val isPeerConfirmed: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActiveChain) return false
        return chainId == other.chainId &&
            masterKeyBase64 == other.masterKeyBase64 &&
            blindedTopic == other.blindedTopic &&
            safetyFingerprint == other.safetyFingerprint &&
            peerDeviceName == other.peerDeviceName &&
            isPeerConfirmed == other.isPeerConfirmed
    }

    override fun hashCode(): Int {
        var result = chainId.hashCode()
        result = 31 * result + masterKeyBase64.hashCode()
        result = 31 * result + peerDeviceName.hashCode()
        result = 31 * result + isPeerConfirmed.hashCode()
        return result
    }
}

data class ChainSettingsState(
    val localDeviceId: String,
    val localDeviceName: String,
    val activeChain: ActiveChain?,
    val isSyncEnabled: Boolean,
    val sendOutboundEnabled: Boolean,
    val receiveInboundEnabled: Boolean,
    val relayBaseUrl: String,
    val retentionHours: Int, // 1, 24, 168, or -1 (manual)
    val notificationsEnabled: Boolean,
    val customRulesEnabled: Boolean,
    val customFilterRules: List<String>
)

class ChainPreferencesRepository(private val context: Context) {
    private object Keys {
        val LOCAL_DEVICE_ID = stringPreferencesKey("local_device_id")
        val LOCAL_DEVICE_NAME = stringPreferencesKey("local_device_name")
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

    val state: Flow<ChainSettingsState> = context.chainDataStore.data.map { prefs ->
        val deviceId = prefs[Keys.LOCAL_DEVICE_ID] ?: ""
        val defaultName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
        val deviceName = prefs[Keys.LOCAL_DEVICE_NAME] ?: defaultName

        val chainId = prefs[Keys.CHAIN_ID]
        val wrappedKey = prefs[Keys.WRAPPED_MASTER_KEY]
        val unwrappedB64 = wrappedKey?.let { KeystoreVault.unwrapSecret(it) }

        val activeChain = if (!chainId.isNullOrBlank() && !unwrappedB64.isNullOrBlank()) {
            runCatching {
                val keyBytes = E2eeCryptoManager.decodeBase64Url(unwrappedB64)
                ActiveChain(
                    chainId = chainId,
                    masterKeyBytes = keyBytes,
                    masterKeyBase64 = unwrappedB64,
                    blindedTopic = E2eeCryptoManager.deriveBlindedTopic(keyBytes),
                    safetyFingerprint = E2eeCryptoManager.deriveSafetyFingerprint(keyBytes),
                    peerDeviceName = prefs[Keys.PEER_DEVICE_NAME] ?: "Partner Device",
                    isPeerConfirmed = prefs[Keys.PEER_CONFIRMED] ?: false
                )
            }.getOrNull()
        } else {
            null
        }

        ChainSettingsState(
            localDeviceId = deviceId,
            localDeviceName = deviceName,
            activeChain = activeChain,
            isSyncEnabled = prefs[Keys.IS_SYNC_ENABLED] ?: true,
            sendOutboundEnabled = prefs[Keys.SEND_OUTBOUND_ENABLED] ?: true,
            receiveInboundEnabled = prefs[Keys.RECEIVE_INBOUND_ENABLED] ?: true,
            relayBaseUrl = prefs[Keys.RELAY_BASE_URL] ?: PairingPayload.DEFAULT_RELAY_URL,
            retentionHours = prefs[Keys.RETENTION_HOURS] ?: 24,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            customRulesEnabled = prefs[Keys.CUSTOM_RULES_ENABLED] ?: true,
            customFilterRules = (prefs[Keys.CUSTOM_FILTER_RULES] ?: emptySet())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sortedBy { it.lowercase() }
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

    suspend fun saveNewChain(
        chainId: String,
        masterKeyBase64: String,
        peerDeviceName: String,
        isPeerConfirmed: Boolean,
        relayBaseUrl: String? = null
    ) {
        val wrapped = KeystoreVault.wrapSecret(masterKeyBase64)
        context.chainDataStore.edit { prefs ->
            prefs[Keys.CHAIN_ID] = chainId
            prefs[Keys.WRAPPED_MASTER_KEY] = wrapped
            prefs[Keys.PEER_DEVICE_NAME] = peerDeviceName
            prefs[Keys.PEER_CONFIRMED] = isPeerConfirmed
            prefs[Keys.IS_SYNC_ENABLED] = true
            prefs[Keys.SEND_OUTBOUND_ENABLED] = true
            prefs[Keys.RECEIVE_INBOUND_ENABLED] = true
            if (!relayBaseUrl.isNullOrBlank()) {
                prefs[Keys.RELAY_BASE_URL] = relayBaseUrl.trimEnd('/')
            }
        }
    }

    suspend fun confirmPeer(peerName: String) {
        context.chainDataStore.edit { prefs ->
            prefs[Keys.PEER_DEVICE_NAME] = peerName.ifBlank { "Partner Device" }
            prefs[Keys.PEER_CONFIRMED] = true
        }
    }

    suspend fun clearChain() {
        context.chainDataStore.edit { prefs ->
            prefs.remove(Keys.CHAIN_ID)
            prefs.remove(Keys.WRAPPED_MASTER_KEY)
            prefs.remove(Keys.PEER_DEVICE_NAME)
            prefs.remove(Keys.PEER_CONFIRMED)
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.IS_SYNC_ENABLED] = enabled }
    }

    suspend fun setSendOutboundEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.SEND_OUTBOUND_ENABLED] = enabled }
    }

    suspend fun setReceiveInboundEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.RECEIVE_INBOUND_ENABLED] = enabled }
    }

    suspend fun setLocalDeviceName(name: String) {
        context.chainDataStore.edit { it[Keys.LOCAL_DEVICE_NAME] = name.trim().ifBlank { "My Device" } }
    }

    suspend fun setRelayBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/').ifBlank { PairingPayload.DEFAULT_RELAY_URL }
        context.chainDataStore.edit { it[Keys.RELAY_BASE_URL] = cleaned }
    }

    suspend fun setRetentionHours(hours: Int) {
        context.chainDataStore.edit { it[Keys.RETENTION_HOURS] = hours }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun addCustomFilterRule(rule: String) {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return
        context.chainDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FILTER_RULES] ?: emptySet()
            val updated = current.filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableSet()
            updated.add(trimmed)
            prefs[Keys.CUSTOM_FILTER_RULES] = updated
            prefs[Keys.CUSTOM_RULES_ENABLED] = true
        }
    }

    suspend fun removeCustomFilterRule(rule: String) {
        context.chainDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FILTER_RULES] ?: emptySet()
            prefs[Keys.CUSTOM_FILTER_RULES] = current.filterNot { it.equals(rule, ignoreCase = true) }.toSet()
        }
    }

    suspend fun setCustomRulesEnabled(enabled: Boolean) {
        context.chainDataStore.edit { it[Keys.CUSTOM_RULES_ENABLED] = enabled }
    }

    suspend fun clearCustomFilterRules() {
        context.chainDataStore.edit { prefs ->
            prefs.remove(Keys.CUSTOM_FILTER_RULES)
        }
    }
}
