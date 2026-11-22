package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.prefs.ActiveChain
import com.example.notifications.ClipboardHelper
import com.example.qr.QrBitmapGenerator
import com.example.ui.theme.OtpMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChainSettingsModalSheet(
    chain: ActiveChain,
    localDeviceName: String,
    pairingUri: String?,
    isSending: Boolean,
    initiallyExpandQr: Boolean = false,
    onRenameChain: (String) -> Unit,
    onToggleChainSync: (Boolean) -> Unit,
    onToggleSendOutbound: (Boolean) -> Unit,
    onToggleReceiveInbound: (Boolean) -> Unit,
    onToggleRulesEnabled: (Boolean) -> Unit,
    onAddCustomRule: (String) -> Unit,
    onRemoveCustomRule: (String) -> Unit,
    onSendTestPing: () -> Unit,
    onSeverChain: (clearHistory: Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var labelDraft by remember(chain.chainId, chain.label) { mutableStateOf(chain.displayTitle) }
    var ruleDraft by remember { mutableStateOf("") }
    var showQrSection by remember(chain.chainId, initiallyExpandQr) {
        mutableStateOf(initiallyExpandQr || !chain.isPeerConfirmed)
    }
    var showSeverDialog by remember { mutableStateOf(false) }

    val qrBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = pairingUri) {
        value = pairingUri?.let { QrBitmapGenerator.generate(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${chain.displayTitle} Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (chain.isPeerConfirmed) {
                            "Paired with ${chain.peerDeviceName}"
                        } else {
                            "Waiting for partner to scan QR"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_chain_settings_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close chain settings")
                }
            }

            // 1. Chain Name Editor
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = labelDraft,
                        onValueChange = { labelDraft = it },
                        singleLine = true,
                        label = { Text("Chain name") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chain_name_input")
                    )
                    Button(
                        onClick = { onRenameChain(labelDraft) },
                        enabled = labelDraft.isNotBlank() && labelDraft.trim() != chain.displayTitle,
                        shape = CircleShape,
                        modifier = Modifier.testTag("save_chain_name_button")
                    ) {
                        Text("Save")
                    }
                }
            }

            // 2. Chain-Specific Custom OTP Filter Rules
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OTP Filter Rules",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (chain.customFilterRules.isEmpty()) {
                                    "Syncing all OTPs to ${chain.displayTitle}"
                                } else if (chain.customRulesEnabled) {
                                    "Only syncing OTPs matching ${chain.customFilterRules.size} rule(s)"
                                } else {
                                    "Rules paused · Syncing all OTPs"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = chain.customRulesEnabled,
                            enabled = chain.customFilterRules.isNotEmpty(),
                            onCheckedChange = onToggleRulesEnabled,
                            modifier = Modifier.testTag("chain_settings_rules_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ruleDraft,
                            onValueChange = { ruleDraft = it },
                            singleLine = true,
                            label = { Text("Add keyword or sender") },
                            placeholder = { Text("e.g. Chase, GitHub, UPI") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chain_settings_rule_input")
                        )
                        Button(
                            onClick = {
                                onAddCustomRule(ruleDraft)
                                ruleDraft = ""
                            },
                            enabled = ruleDraft.isNotBlank(),
                            shape = CircleShape,
                            modifier = Modifier.testTag("chain_settings_add_rule_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (chain.customFilterRules.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chain.customFilterRules.forEach { rule ->
                                InputChip(
                                    selected = chain.customRulesEnabled,
                                    onClick = { onRemoveCustomRule(rule) },
                                    label = { Text(rule) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove rule $rule",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("chain_settings_rule_chip_$rule")
                                )
                            }
                        }
                    }
                }
            }

            // 3. Pairing QR & Safety Verification
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (chain.isPeerConfirmed) Icons.Rounded.VerifiedUser else Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Pairing QR & Fingerprint",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "This device: $localDeviceName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = { showQrSection = !showQrSection },
                            modifier = Modifier.testTag("toggle_qr_section_button")
                        ) {
                            Text(if (showQrSection) "Hide" else "Show QR")
                            Icon(
                                imageVector = if (showQrSection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showQrSection) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            qrBitmap?.let { bmp ->
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = Color.White,
                                    tonalElevation = 2.dp
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "OTP Chain pairing QR code",
                                            modifier = Modifier.size(196.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
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
                                            text = "SAFETY FINGERPRINT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = chain.safetyFingerprint,
                                        fontFamily = OtpMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                pairingUri?.let { uri ->
                                    ClipboardHelper.copyPlainText(context, "OTP Chain Invite", uri)
                                    onShowMessage("Invite link copied to clipboard")
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_invite_link_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Link")
                        }
                        FilledTonalButton(
                            onClick = onSendTestPing,
                            enabled = !isSending,
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_test_ping_button")
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test E2EE")
                        }
                    }
                }
            }

            // 4. Per-Chain Sync Direction Controls
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Direction Controls",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ChainToggleRow(
                        icon = Icons.Rounded.Shield,
                        title = "Enable This Sync",
                        subtitle = "Turn sending & receiving on or off",
                        checked = chain.isSyncEnabled,
                        onCheckedChange = onToggleChainSync,
                        testTag = "chain_master_sync_switch"
                    )
                    ChainToggleRow(
                        icon = Icons.AutoMirrored.Rounded.CallMade,
                        title = "Forward Outbound OTPs",
                        subtitle = "Encrypt & send matching SMS codes",
                        checked = chain.sendOutboundEnabled,
                        enabled = chain.isSyncEnabled,
                        onCheckedChange = onToggleSendOutbound,
                        testTag = "chain_outbound_switch"
                    )
                    ChainToggleRow(
                        icon = Icons.AutoMirrored.Rounded.CallReceived,
                        title = "Receive Inbound OTPs",
                        subtitle = "Listen on relay & show alerts",
                        checked = chain.receiveInboundEnabled,
                        enabled = chain.isSyncEnabled,
                        onCheckedChange = onToggleReceiveInbound,
                        testTag = "chain_inbound_switch"
                    )
                }
            }

            // 5. Remove This Sync
            Button(
                onClick = { showSeverDialog = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sever_chain_button")
            ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove ${chain.displayTitle}")
            }
        }
    }

    if (showSeverDialog) {
        var clearLocalHistory by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showSeverDialog = false },
            icon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
            title = { Text("Remove ${chain.displayTitle}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This wipes the 256-bit key for \"${chain.displayTitle}\" and disconnects it. Other sync chains stay untouched."
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
                        Text("Also clear this sync's OTP history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSeverDialog = false
                        onSeverChain(clearLocalHistory)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_sever_button")
                ) {
                    Text("Remove")
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
private fun ChainToggleRow(
    icon: ImageVector,
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
