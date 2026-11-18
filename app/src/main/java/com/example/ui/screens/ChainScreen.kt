package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.notifications.ClipboardHelper
import com.example.qr.QrBitmapGenerator
import com.example.ui.CodeChainUiState
import com.example.ui.theme.OtpMonoFontFamily

@Composable
fun ChainScreen(
    uiState: CodeChainUiState,
    pairingUri: String?,
    onStartNewChain: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onToggleMasterSync: (Boolean) -> Unit,
    onToggleSendOutbound: (Boolean) -> Unit,
    onToggleReceiveInbound: (Boolean) -> Unit,
    onSendTestPing: () -> Unit,
    onSeverChain: (clearHistory: Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val activeChain = settings?.activeChain
    var showSeverDialog by remember { mutableStateOf(false) }

    val qrBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = pairingUri) {
        value = pairingUri?.let { QrBitmapGenerator.generate(it) }
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
                    text = "Sync Chain",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Pair via QR and manage per-device encryption controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (activeChain == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Start a New Sync Chain",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Generates a fresh 256-bit AES-GCM key on this device and renders a QR code for your partner device to scan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = onStartNewChain,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("chain_create_qr_button")
                        ) {
                            Text("Generate Pairing QR")
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Join an Existing Chain",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Scan the QR displayed on the primary device (or paste an invite link) to receive encrypted OTPs instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onOpenQrScanner,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("chain_scan_qr_button")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan QR Code")
                        }
                    }
                }
            }
        } else {
            // Paired QR display + Safety Fingerprint
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activeChain.isPeerConfirmed) {
                                        "Paired with ${activeChain.peerDeviceName}"
                                    } else {
                                        "Scan to Complete Pairing"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "This device: ${settings.localDeviceName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = if (activeChain.isPeerConfirmed) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        qrBitmap?.let { bmp ->
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White,
                                tonalElevation = 2.dp
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "CodeChain pairing QR code",
                                        modifier = Modifier.size(210.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SAFETY FINGERPRINT (VERIFY ON BOTH DEVICES)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = activeChain.safetyFingerprint,
                                    fontFamily = OtpMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    pairingUri?.let { uri ->
                                        ClipboardHelper.copyPlainText(context, "CodeChain Invite", uri)
                                        onShowMessage("Invite link copied to clipboard")
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("copy_invite_link_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Link")
                            }
                            FilledTonalButton(
                                onClick = onSendTestPing,
                                enabled = !uiState.isSending,
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("send_test_ping_button")
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test E2EE")
                            }
                        }
                    }
                }
            }

            // Per-device sync controls
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Per-Device Sync Controls",
                            style = MaterialTheme.typography.titleMedium
                        )

                        ToggleControlRow(
                            icon = Icons.Rounded.Shield,
                            title = "Enable Sync on This Device",
                            subtitle = "Master kill-switch for sending & receiving",
                            checked = settings.isSyncEnabled,
                            onCheckedChange = onToggleMasterSync,
                            testTag = "chain_master_sync_switch"
                        )
                        ToggleControlRow(
                            icon = Icons.AutoMirrored.Rounded.CallMade,
                            title = "Forward Outbound OTPs",
                            subtitle = "Allow this device to encrypt & send codes",
                            checked = settings.sendOutboundEnabled,
                            enabled = settings.isSyncEnabled,
                            onCheckedChange = onToggleSendOutbound,
                            testTag = "chain_outbound_switch"
                        )
                        ToggleControlRow(
                            icon = Icons.AutoMirrored.Rounded.CallReceived,
                            title = "Receive Inbound OTPs",
                            subtitle = "Listen on ephemeral relay & show notifications",
                            checked = settings.receiveInboundEnabled,
                            enabled = settings.isSyncEnabled,
                            onCheckedChange = onToggleReceiveInbound,
                            testTag = "chain_inbound_switch"
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenQrScanner,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("rescan_qr_button")
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Switch Chain")
                    }
                    Button(
                        onClick = { showSeverDialog = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("sever_chain_button")
                    ) {
                        Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sever Chain")
                    }
                }
            }
        }
    }

    if (showSeverDialog) {
        var clearLocalHistory by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showSeverDialog = false },
            icon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
            title = { Text("Sever Sync Chain?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This wipes the 256-bit encryption key from this device and disconnects from the relay. You will need a new QR scan to re-pair."
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = clearLocalHistory,
                            onCheckedChange = { clearLocalHistory = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Also wipe local OTP history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSeverDialog = false
                        onSeverChain(clearLocalHistory)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_sever_button")
                ) {
                    Text("Sever Chain")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ToggleControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
