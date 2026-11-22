package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.R
import com.example.data.local.SyncedOtpEntity
import com.example.data.prefs.ActiveChain
import com.example.data.relay.RelayConnectionState
import com.example.ui.CodeChainUiState
import com.example.ui.components.ChainRulesStripCard
import com.example.ui.components.LatestOtpHeroCard
import com.example.ui.components.NotificationAccessBanner
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.components.OtpHistoryRowCard
import com.example.ui.components.SmsPermissionBanner
import com.example.ui.components.SyncPagerIndicatorRow
import com.example.ui.components.SyncStatusHeroCard
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun VaultScreen(
    uiState: CodeChainUiState,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    needsNotificationPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSelectChain: (String) -> Unit,
    onToggleChainSync: (chainId: String, enabled: Boolean) -> Unit,
    onToggleChainRulesEnabled: (chainId: String, enabled: Boolean) -> Unit,
    onOpenChainSettings: (chainId: String, expandQr: Boolean) -> Unit,
    onOpenChainRulesEditor: (chainId: String) -> Unit,
    onOpenAddSyncSheet: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onStartNewChain: () -> Unit,
    onOpenPasteOtp: (chainId: String) -> Unit,
    onCopyOtp: (SyncedOtpEntity) -> Unit,
    onDeleteOtp: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val chains = uiState.chains
    val isGlobalSyncEnabled = settings?.isSyncEnabled ?: true
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex = remember(chains, settings?.selectedChainId) {
        val idx = chains.indexOfFirst { it.chainId == settings?.selectedChainId }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { chains.size.coerceAtLeast(1) }
    )

    LaunchedEffect(selectedIndex, chains.size) {
        if (chains.isNotEmpty() && selectedIndex in chains.indices && pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage, chains) {
        val swipedChain = chains.getOrNull(pagerState.settledPage)
        if (swipedChain != null && swipedChain.chainId != settings?.selectedChainId) {
            onSelectChain(swipedChain.chainId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
    ) {
        // Compact Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        chains.size > 1 -> "${chains.size} chains · Swipe left or right"
                        chains.size == 1 -> "Zero-storage encrypted 2FA vault"
                        else -> "End-to-end encrypted OTP sync"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onOpenAddSyncSheet,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("home_add_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add sync chain",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (chains.isEmpty()) {
            // Unpaired Onboarding State
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SyncStatusHeroCard(
                        activeChain = null,
                        isGlobalSyncEnabled = isGlobalSyncEnabled,
                        isChainSyncEnabled = true,
                        hasAutoSms = hasReceiveSms || hasNotificationAccess,
                        activeCustomRuleCount = 0,
                        relayState = RelayConnectionState.Unpaired,
                        onToggleSync = {},
                        onOpenChainSettings = onOpenAddSyncSheet
                    )
                }

                if (!hasReceiveSms) {
                    item { SmsPermissionBanner(onRequestSmsPermission = onRequestSmsPermission) }
                }
                if (!hasNotificationAccess) {
                    item { NotificationAccessBanner(onOpenSettings = onRequestNotificationAccess) }
                }
                if (needsNotificationPermission) {
                    item { NotificationPermissionBanner(onRequestPermission = onRequestNotificationPermission) }
                }

                item {
                    OnboardingChainCard(
                        onStartNewChain = onStartNewChain,
                        onScanQr = onOpenQrScanner
                    )
                }
            }
        } else {
            // Animated Pager Dots when 2+ chains exist
            if (chains.size > 1) {
                SyncPagerIndicatorRow(
                    pageCount = chains.size,
                    currentPage = pagerState.currentPage.coerceIn(0, chains.lastIndex),
                    onSelectPage = { targetPage ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // Full-Page Tinder-Style Swipeable Chain Deck
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(
                    horizontal = if (chains.size > 1) 16.dp else 14.dp
                ),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("home_sync_pager")
            ) { page ->
                val chain = chains[page]
                val isFirstChain = page == 0
                val pageOffset = pagerState.getOffsetDistanceInPages(page)
                val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

                val chainOtps = remember(uiState.otps, chain.chainId, isFirstChain) {
                    uiState.otps.filter { otp ->
                        otp.chainId == chain.chainId || (isFirstChain && otp.chainId.isBlank())
                    }
                }

                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp)
                        .graphicsLayer {
                            // Tinder-style card deck physics as user swipes left/right
                            rotationZ = pageOffset * -4.2f
                            val scale = lerp(0.93f, 1f, 1f - absOffset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.76f, 1f, 1f - absOffset)
                            translationY = absOffset * 18f
                        }
                        .testTag("chain_deck_page_${chain.chainId}")
                ) {
                    ChainDeckPageContent(
                        chain = chain,
                        otps = chainOtps,
                        isGlobalSyncEnabled = isGlobalSyncEnabled,
                        hasAutoSms = hasReceiveSms || hasNotificationAccess,
                        hasReceiveSms = hasReceiveSms,
                        hasNotificationAccess = hasNotificationAccess,
                        needsNotificationPermission = needsNotificationPermission,
                        relayState = uiState.relayStateFor(chain.chainId),
                        onToggleChainSync = { enabled -> onToggleChainSync(chain.chainId, enabled) },
                        onOpenChainSettings = { expandQr -> onOpenChainSettings(chain.chainId, expandQr) },
                        onToggleRulesEnabled = { enabled -> onToggleChainRulesEnabled(chain.chainId, enabled) },
                        onOpenRulesEditor = { onOpenChainRulesEditor(chain.chainId) },
                        onOpenPasteOtp = { onOpenPasteOtp(chain.chainId) },
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestNotificationAccess = onRequestNotificationAccess,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onCopyOtp = onCopyOtp,
                        onDeleteOtp = onDeleteOtp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainDeckPageContent(
    chain: ActiveChain,
    otps: List<SyncedOtpEntity>,
    isGlobalSyncEnabled: Boolean,
    hasAutoSms: Boolean,
    hasReceiveSms: Boolean,
    hasNotificationAccess: Boolean,
    needsNotificationPermission: Boolean,
    relayState: RelayConnectionState,
    onToggleChainSync: (Boolean) -> Unit,
    onOpenChainSettings: (expandQr: Boolean) -> Unit,
    onToggleRulesEnabled: (Boolean) -> Unit,
    onOpenRulesEditor: () -> Unit,
    onOpenPasteOtp: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onCopyOtp: (SyncedOtpEntity) -> Unit,
    onDeleteOtp: (Long) -> Unit
) {
    val latestOtp = otps.firstOrNull()
    val historyOtps = if (otps.size > 1) otps.drop(1) else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Chain Hero Card with Top Settings Button + QR + Extract & Sync
        item(key = "hero_${chain.chainId}") {
            SyncStatusHeroCard(
                activeChain = chain,
                isGlobalSyncEnabled = isGlobalSyncEnabled,
                isChainSyncEnabled = chain.isSyncEnabled,
                hasAutoSms = hasAutoSms,
                activeCustomRuleCount = chain.activeCustomRuleCount,
                relayState = relayState,
                onToggleSync = onToggleChainSync,
                onOpenChainSettings = { onOpenChainSettings(false) },
                onOpenChainQr = { onOpenChainSettings(true) },
                onOpenExtractOtp = onOpenPasteOtp,
                onOpenRules = onOpenRulesEditor
            )
        }

        // 2. Chain-Specific Filter Rules right inside this page
        item(key = "rules_${chain.chainId}") {
            ChainRulesStripCard(
                chain = chain,
                onToggleRulesEnabled = onToggleRulesEnabled,
                onOpenRulesEditor = onOpenRulesEditor
            )
        }

        // 3. Compact permission reminders only when missing
        if (!hasReceiveSms) {
            item(key = "perm_sms") {
                SmsPermissionBanner(onRequestSmsPermission = onRequestSmsPermission)
            }
        }
        if (!hasNotificationAccess) {
            item(key = "perm_notif_access") {
                NotificationAccessBanner(onOpenSettings = onRequestNotificationAccess)
            }
        }
        if (needsNotificationPermission) {
            item(key = "perm_post_notif") {
                NotificationPermissionBanner(onRequestPermission = onRequestNotificationPermission)
            }
        }

        // 4. This Chain's Synced OTP Vault
        if (latestOtp != null) {
            item(key = "latest_header") {
                Text(
                    text = "LATEST CODE · ${chain.displayTitle.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            item(key = "latest_${latestOtp.id}") {
                LatestOtpHeroCard(
                    otp = latestOtp,
                    onCopy = { onCopyOtp(latestOtp) }
                )
            }
        }

        if (historyOtps.isNotEmpty()) {
            item(key = "history_header") {
                Text(
                    text = "RECENT HISTORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            items(historyOtps, key = { it.id }) { otp ->
                OtpHistoryRowCard(
                    otp = otp,
                    onCopy = { onCopyOtp(otp) },
                    onDelete = { onDeleteOtp(otp.id) }
                )
            }
        } else if (latestOtp == null) {
            item(key = "empty_${chain.chainId}") {
                EmptyVaultPlaceholder(
                    syncName = chain.displayTitle,
                    onOpenPasteOtp = onOpenPasteOtp
                )
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
                Box(contentAlignment = Alignment.Center) {
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
private fun EmptyVaultPlaceholder(
    syncName: String,
    onOpenPasteOtp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.EnhancedEncryption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "Ready for $syncName OTPs",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Incoming verification codes matching $syncName's rules will appear here automatically, or tap Extract & Sync above.",
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
