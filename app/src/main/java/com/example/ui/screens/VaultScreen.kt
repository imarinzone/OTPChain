package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.SyncedOtpEntity
import com.example.ui.CodeChainUiState
import com.example.ui.components.LatestOtpHeroCard
import com.example.ui.components.NotificationAccessBanner
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.components.OtpHistoryRowCard
import com.example.ui.components.SmsPermissionBanner
import com.example.ui.components.SyncStatusHeroCard

@Composable
fun VaultScreen(
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    needsNotificationPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onOpenChainTab: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onStartNewChain: () -> Unit,
    onOpenPasteOtp: () -> Unit,
    onCopyOtp: (SyncedOtpEntity) -> Unit,
    onDeleteOtp: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val activeChain = settings?.activeChain
    val latestOtp = uiState.otps.firstOrNull()
    val historyOtps = if (uiState.otps.size > 1) uiState.otps.drop(1) else emptyList()

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
                    text = "CodeChain",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zero-storage end-to-end encrypted 2FA vault",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            val activeRuleCount = if (settings?.customRulesEnabled == true) {
                settings.customFilterRules.size
            } else {
                0
            }
            SyncStatusHeroCard(
                activeChain = activeChain,
                isSyncEnabled = settings?.isSyncEnabled ?: true,
                hasAutoSms = hasReceiveSms || hasNotificationAccess,
                activeCustomRuleCount = activeRuleCount,
                relayState = uiState.relayState,
                onToggleSync = onToggleSync,
                onOpenChainSetup = onOpenChainTab
            )
        }

        if (!hasReceiveSms) {
            item {
                SmsPermissionBanner(
                    onRequestSmsPermission = onRequestSmsPermission
                )
            }
        }

        if (!hasNotificationAccess) {
            item {
                NotificationAccessBanner(
                    onOpenSettings = onRequestNotificationAccess
                )
            }
        }

        if (needsNotificationPermission) {
            item {
                NotificationPermissionBanner(
                    onRequestPermission = onRequestNotificationPermission
                )
            }
        }

        if (activeChain == null) {
            item {
                OnboardingChainCard(
                    onStartNewChain = {
                        onStartNewChain()
                        onOpenChainTab()
                    },
                    onScanQr = onOpenQrScanner
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenPasteOtp,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("open_paste_otp_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Extract & Sync OTP")
                    }
                    FilledTonalButton(
                        onClick = onOpenChainTab,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("view_chain_controls_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = "Manage Chain",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (latestOtp != null) {
            item {
                Text(
                    text = "LATEST CODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                LatestOtpHeroCard(
                    otp = latestOtp,
                    onCopy = { onCopyOtp(latestOtp) }
                )
            }
        }

        if (historyOtps.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT HISTORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(historyOtps, key = { it.id }) { otp ->
                OtpHistoryRowCard(
                    otp = otp,
                    onCopy = { onCopyOtp(otp) },
                    onDelete = { onDeleteOtp(otp.id) }
                )
            }
        } else if (activeChain != null && latestOtp == null) {
            item {
                EmptyVaultPlaceholder(onOpenPasteOtp = onOpenPasteOtp)
            }
        }
    }
}

@Composable
private fun OnboardingChainCard(
    onStartNewChain: () -> Unit,
    onScanQr: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = "Link Two Devices in Seconds",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Generate a 256-bit encrypted QR code on one phone and scan it on the second. Nothing is stored on any server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartNewChain,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_start_chain_button")
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Chain (Show QR)")
                }
                FilledTonalButton(
                    onClick = onScanQr,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_scan_qr_button")
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR to Join")
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultPlaceholder(onOpenPasteOtp: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.EnhancedEncryption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Vault Ready for Encrypted OTPs",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Share any verification SMS to CodeChain from your messaging app, or tap Extract & Sync OTP above to forward a code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onOpenPasteOtp,
                shape = CircleShape,
                modifier = Modifier.testTag("empty_state_paste_otp_button")
            ) {
                Text("Try Syncing a Code")
            }
        }
    }
}
