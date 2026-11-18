package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.PairingPayload
import com.example.ui.CodeChainUiState

@Composable
fun SettingsScreen(
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onUpdateRelayUrl: (String) -> Unit,
    onUpdateRetentionHours: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings ?: return
    var deviceNameDraft by remember(settings.localDeviceName) {
        mutableStateOf(settings.localDeviceName)
    }
    var relayUrlDraft by remember(settings.relayBaseUrl) {
        mutableStateOf(settings.relayBaseUrl)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Security & Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Zero-server-storage relay and local vault retention",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Auto SMS Capture & Notification Listener card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic SMS Reading",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = buildString {
                                    append("Direct SMS: ")
                                    append(if (hasReceiveSms) "Granted" else "Not granted")
                                    append(" · Notification Listener: ")
                                    append(if (hasNotificationAccess) "Granted" else "Not granted")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasReceiveSms) {
                            Button(
                                onClick = onRequestSmsPermission,
                                shape = CircleShape,
                                modifier = Modifier.testTag("settings_grant_sms_button")
                            ) {
                                Text("Allow SMS")
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenNotificationAccess,
                            shape = CircleShape,
                            modifier = Modifier.testTag("settings_notification_access_button")
                        ) {
                            Text(if (hasNotificationAccess) "Manage Notifications" else "Notification Access")
                        }
                    }
                }
            }
        }

        // How Share Sheet OTP Sharing Works card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Manual & Share Sheet Fallback",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "You can also share any SMS via Share → CodeChain or paste in Vault. Only the detected 4–8 digit OTP is encrypted with AES-256-GCM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Device Identity
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Device Label", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = deviceNameDraft,
                        onValueChange = { deviceNameDraft = it },
                        singleLine = true,
                        label = { Text("Shown to paired partner") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_name_input")
                    )
                    Button(
                        onClick = { onUpdateDeviceName(deviceNameDraft) },
                        enabled = deviceNameDraft.isNotBlank() && deviceNameDraft.trim() != settings.localDeviceName,
                        shape = CircleShape,
                        modifier = Modifier.testTag("save_device_name_button")
                    ) {
                        Text("Save Label")
                    }
                }
            }
        }

        // Notifications & Retention
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Synced OTP Alerts",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "High-priority notification with 1-tap Copy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = onToggleNotifications,
                            modifier = Modifier.testTag("settings_notifications_switch")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoDelete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Local OTP Auto-Purge", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf(1 to "1h", 24 to "24h", 168 to "7d", -1 to "Manual")
                        options.forEach { (hours, label) ->
                            FilterChip(
                                selected = settings.retentionHours == hours,
                                onClick = { onUpdateRetentionHours(hours) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("retention_chip_$hours")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onClearHistory,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_history_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Local OTP History (${uiState.otps.size})")
                    }
                }
            }
        }

        // Ephemeral Zero-Storage Relay Endpoint
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("Ephemeral Relay Endpoint", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "All pushes send X-Cache: no so nothing is stored server-side.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = relayUrlDraft,
                        onValueChange = { relayUrlDraft = it },
                        singleLine = true,
                        label = { Text("ntfy-compatible relay URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("relay_url_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onUpdateRelayUrl(relayUrlDraft) },
                            enabled = relayUrlDraft.startsWith("http") &&
                                relayUrlDraft.trimEnd('/') != settings.relayBaseUrl,
                            shape = CircleShape,
                            modifier = Modifier.testTag("save_relay_url_button")
                        ) {
                            Text("Apply Relay")
                        }
                        if (settings.relayBaseUrl != PairingPayload.DEFAULT_RELAY_URL) {
                            OutlinedButton(
                                onClick = {
                                    relayUrlDraft = PairingPayload.DEFAULT_RELAY_URL
                                    onUpdateRelayUrl(PairingPayload.DEFAULT_RELAY_URL)
                                },
                                shape = CircleShape
                            ) {
                                Text("Reset Default")
                            }
                        }
                    }
                }
            }
        }
    }
}
