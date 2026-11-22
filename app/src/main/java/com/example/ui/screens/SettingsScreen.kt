package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.security.PairingPayload
import com.example.ui.CodeChainUiState

private enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    SYNC_CONTROLS(
        title = "Sync & Chains",
        description = "Master kill-switch, Quick Settings tile, and per-chain settings",
        icon = Icons.Rounded.Sync
    ),
    PERMISSIONS(
        title = "Permissions & Capture",
        description = "Direct SMS reading, Notification Access, and share fallback",
        icon = Icons.Rounded.Security
    ),
    NOTIFICATIONS(
        title = "Notifications & Retention",
        description = "Alert preferences, auto-purge schedule, and local vault storage",
        icon = Icons.Rounded.NotificationsActive
    ),
    DEVICE_RELAY(
        title = "Device & Relay",
        description = "Device display name and zero-storage ephemeral relay URL",
        icon = Icons.Rounded.Dns
    )
}

@Composable
fun SettingsScreen(
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onAddQuickTile: () -> Unit,
    onToggleGlobalSync: (Boolean) -> Unit,
    onOpenChainSettings: (chainId: String) -> Unit,
    onOpenAddSyncSheet: () -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onUpdateRelayUrl: (String) -> Unit,
    onUpdateRetentionHours: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings ?: return
    val chains = uiState.chains
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    AnimatedContent(
        targetState = activeCategory,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "settingsCategoryTransition",
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
    ) { category ->
        if (category == null) {
            SettingsCategoryHub(
                uiState = uiState,
                hasReceiveSms = hasReceiveSms,
                hasNotificationAccess = hasNotificationAccess,
                onSelectCategory = { activeCategory = it },
                onToggleGlobalSync = onToggleGlobalSync
            )
        } else {
            SettingsCategoryDetail(
                category = category,
                uiState = uiState,
                hasReceiveSms = hasReceiveSms,
                hasNotificationAccess = hasNotificationAccess,
                onBack = { activeCategory = null },
                onRequestSmsPermission = onRequestSmsPermission,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenAppInfo = onOpenAppInfo,
                onAddQuickTile = onAddQuickTile,
                onToggleGlobalSync = onToggleGlobalSync,
                onOpenChainSettings = onOpenChainSettings,
                onOpenAddSyncSheet = onOpenAddSyncSheet,
                onUpdateDeviceName = onUpdateDeviceName,
                onUpdateRelayUrl = onUpdateRelayUrl,
                onUpdateRetentionHours = onUpdateRetentionHours,
                onToggleNotifications = onToggleNotifications,
                onClearHistory = onClearHistory
            )
        }
    }
}

@Composable
private fun SettingsCategoryHub(
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    onSelectCategory: (SettingsCategory) -> Unit,
    onToggleGlobalSync: (Boolean) -> Unit
) {
    val settings = uiState.settings ?: return
    val chains = uiState.chains
    val enabledChains = chains.count { it.isSyncEnabled }
    val retentionLabel = when (settings.retentionHours) {
        1 -> "1h"
        24 -> "24h"
        168 -> "7d"
        else -> "Manual"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "App preferences · Chain-specific rules live on each chain card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Master Sync Kill-Switch on hub top
        if (chains.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (settings.isSyncEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (settings.isSyncEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Sync",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (settings.isSyncEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = if (settings.isSyncEnabled) {
                                    "$enabledChains of ${chains.size} sync chain(s) active"
                                } else {
                                    "All syncs paused globally"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.isSyncEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = settings.isSyncEnabled,
                            onCheckedChange = onToggleGlobalSync,
                            modifier = Modifier.testTag("settings_global_sync_switch")
                        )
                    }
                }
            }
        }

        item {
            SettingsCategoryCard(
                category = SettingsCategory.SYNC_CONTROLS,
                statusSummary = if (chains.isEmpty()) {
                    "No chains yet · Quick Settings Tile"
                } else {
                    "${chains.size} sync chain(s) · Quick Settings Tile"
                },
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onSelectCategory(SettingsCategory.SYNC_CONTROLS) }
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsCategory.PERMISSIONS,
                statusSummary = "SMS: ${if (hasReceiveSms) "On" else "Off"} · Listener: ${if (hasNotificationAccess) "On" else "Off"}",
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { onSelectCategory(SettingsCategory.PERMISSIONS) }
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsCategory.NOTIFICATIONS,
                statusSummary = "Alerts: ${if (settings.notificationsEnabled) "On" else "Off"} · Auto-purge: $retentionLabel · ${uiState.otps.size} saved",
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onSelectCategory(SettingsCategory.NOTIFICATIONS) }
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsCategory.DEVICE_RELAY,
                statusSummary = "${settings.localDeviceName} · ${settings.relayBaseUrl.removePrefix("https://")}",
                iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = { onSelectCategory(SettingsCategory.DEVICE_RELAY) }
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    statusSummary: String,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("settings_category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open ${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCategoryDetail(
    category: SettingsCategory,
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    onBack: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onAddQuickTile: () -> Unit,
    onToggleGlobalSync: (Boolean) -> Unit,
    onOpenChainSettings: (chainId: String) -> Unit,
    onOpenAddSyncSheet: () -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onUpdateRelayUrl: (String) -> Unit,
    onUpdateRetentionHours: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    val settings = uiState.settings ?: return
    val chains = uiState.chains
    var deviceNameDraft by remember(settings.localDeviceName) {
        mutableStateOf(settings.localDeviceName)
    }
    var relayUrlDraft by remember(settings.relayBaseUrl) {
        mutableStateOf(settings.relayBaseUrl)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Detail Header with Back Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                }
                Column {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (category) {
            SettingsCategory.SYNC_CONTROLS -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Master Sync Switch", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Pause or resume all sync chains at once (mirrors Quick Settings tile).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.isSyncEnabled,
                                onCheckedChange = onToggleGlobalSync
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Quick Settings Tile",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Toggle OTP Chain on or off right from your notification shade.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Button(
                                onClick = onAddQuickTile,
                                shape = CircleShape,
                                modifier = Modifier.testTag("add_quick_tile_button")
                            ) {
                                Text("Add Tile")
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Your Sync Chains", style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(
                                    onClick = onOpenAddSyncSheet,
                                    shape = CircleShape
                                ) {
                                    Text("Add Sync")
                                }
                            }
                            if (chains.isEmpty()) {
                                Text(
                                    text = "No chains paired yet. Tap Add Sync to generate or scan a QR code.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                chains.forEach { chain ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { onOpenChainSettings(chain.chainId) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Link,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = chain.displayTitle,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(if (chain.isSyncEnabled) "Active" else "Paused")
                                                        append(" · ")
                                                        if (chain.activeCustomRuleCount > 0) {
                                                            append("${chain.activeCustomRuleCount} filter rule(s)")
                                                        } else {
                                                            append("All OTPs")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = "Open ${chain.displayTitle} settings",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsCategory.PERMISSIONS -> {
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
                                        text = "Direct SMS: ${if (hasReceiveSms) "Granted" else "Not granted"} · Notification Listener: ${if (hasNotificationAccess) "Granted" else "Not granted"}",
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
                                    Text(if (hasNotificationAccess) "Manage Listener" else "Notification Access")
                                }
                            }
                        }
                    }
                }

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
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Android 13+ Restricted Settings", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "If Notification Access is grayed out for sideloaded APKs, open App Info, tap the ⋮ menu at top-right, and choose \"Allow restricted settings\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenAppInfo,
                                shape = CircleShape
                            ) {
                                Text("Open App Info")
                            }
                        }
                    }
                }

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
                                Text("Manual & Share Sheet Fallback", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "You can also share any SMS via Share → OTP Chain or paste directly on any chain card. Only the detected 4–8 digit OTP is encrypted with AES-256-GCM.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SettingsCategory.NOTIFICATIONS -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                Text("Clear All Local OTP History (${uiState.otps.size})")
                            }
                        }
                    }
                }
            }

            SettingsCategory.DEVICE_RELAY -> {
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
    }
}
