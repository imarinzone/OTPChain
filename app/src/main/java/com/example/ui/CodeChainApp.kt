package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.otp.NotificationAccessHelper
import com.example.qr.QrScannerSheet
import com.example.ui.components.PasteOtpModalSheet
import com.example.ui.screens.ChainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultScreen
import kotlinx.coroutines.launch

sealed class CodeChainRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Vault : CodeChainRoute(
        route = "vault",
        labelRes = R.string.nav_vault,
        selectedIcon = Icons.Rounded.Lock,
        unselectedIcon = Icons.Outlined.Lock
    )

    data object Chain : CodeChainRoute(
        route = "chain",
        labelRes = R.string.nav_chain,
        selectedIcon = Icons.Rounded.Link,
        unselectedIcon = Icons.Outlined.Link
    )

    data object Settings : CodeChainRoute(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

private val topLevelRoutes = listOf(
    CodeChainRoute.Vault,
    CodeChainRoute.Chain,
    CodeChainRoute.Settings
)

@Composable
fun CodeChainApp(viewModel: SyncChainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingSharedSms by viewModel.pendingSharedSms.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: CodeChainRoute.Vault.route

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showQrScanner by remember { mutableStateOf(false) }
    var showPasteOtpSheet by remember { mutableStateOf(false) }
    var pasteInitialText by remember { mutableStateOf("") }

    var hasPostNotificationPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var hasReceiveSms by remember {
        mutableStateOf(NotificationAccessHelper.isReceiveSmsGranted(context))
    }
    var hasNotificationAccess by remember {
        mutableStateOf(NotificationAccessHelper.isNotificationAccessGranted(context))
    }
    var showNotificationAccessDialog by remember {
        mutableStateOf(
            !NotificationAccessHelper.isReceiveSmsGranted(context) &&
                !NotificationAccessHelper.isNotificationAccessGranted(context)
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasReceiveSms = NotificationAccessHelper.isReceiveSmsGranted(context)
                hasNotificationAccess = NotificationAccessHelper.isNotificationAccessGranted(context)
                NotificationAccessHelper.requestListenerRebindIfGranted(context)
                hasPostNotificationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openNotificationAccessSettings = {
        context.startActivity(NotificationAccessHelper.createNotificationListenerSettingsIntent(context))
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReceiveSms = granted
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                if (granted) {
                    "Automatic SMS reading enabled!"
                } else {
                    "SMS permission denied; you can still grant Notification Access or share manually."
                }
            )
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotificationPerm = granted
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Notification alerts denied; codes still appear in Vault.")
            }
        }
    }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasReceiveSms = results[Manifest.permission.RECEIVE_SMS] ?: NotificationAccessHelper.isReceiveSmsGranted(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPostNotificationPerm = results[Manifest.permission.POST_NOTIFICATIONS] ?: hasPostNotificationPerm
        }
        if (hasReceiveSms) {
            showNotificationAccessDialog = false
        }
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!NotificationAccessHelper.isReceiveSmsGranted(context)) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPerm) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            startupPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Automatically open Extract & Sync modal when user shares an SMS into CodeChain
    LaunchedEffect(pendingSharedSms) {
        val shared = pendingSharedSms
        if (!shared.isNullOrBlank()) {
            pasteInitialText = shared
            showPasteOtpSheet = true
            viewModel.clearPendingSharedSms()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        topLevelRoutes.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        topLevelRoutes.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = CodeChainRoute.Vault.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(CodeChainRoute.Vault.route) {
                            VaultScreen(
                                uiState = uiState,
                                hasReceiveSms = hasReceiveSms,
                                hasNotificationAccess = hasNotificationAccess,
                                needsNotificationPermission = !hasPostNotificationPerm &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                                onRequestSmsPermission = {
                                    smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                },
                                onRequestNotificationAccess = openNotificationAccessSettings,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onToggleSync = viewModel::setSyncEnabled,
                                onOpenChainTab = { navigateToTab(CodeChainRoute.Chain.route) },
                                onOpenQrScanner = { showQrScanner = true },
                                onStartNewChain = viewModel::startNewChain,
                                onOpenPasteOtp = {
                                    pasteInitialText = ""
                                    showPasteOtpSheet = true
                                },
                                onCopyOtp = viewModel::copyOtp,
                                onDeleteOtp = viewModel::deleteOtp
                            )
                        }

                        composable(CodeChainRoute.Chain.route) {
                            ChainScreen(
                                uiState = uiState,
                                pairingUri = viewModel.buildCurrentPairingUri(),
                                onStartNewChain = viewModel::startNewChain,
                                onOpenQrScanner = { showQrScanner = true },
                                onToggleMasterSync = viewModel::setSyncEnabled,
                                onToggleSendOutbound = viewModel::setSendOutboundEnabled,
                                onToggleReceiveInbound = viewModel::setReceiveInboundEnabled,
                                onSendTestPing = viewModel::sendEncryptedPing,
                                onSeverChain = viewModel::severChain,
                                onShowMessage = { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }

                        composable(CodeChainRoute.Settings.route) {
                            SettingsScreen(
                                uiState = uiState,
                                hasReceiveSms = hasReceiveSms,
                                hasNotificationAccess = hasNotificationAccess,
                                onRequestSmsPermission = {
                                    smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                },
                                onOpenNotificationAccess = openNotificationAccessSettings,
                                onUpdateDeviceName = viewModel::setLocalDeviceName,
                                onUpdateRelayUrl = viewModel::setRelayBaseUrl,
                                onUpdateRetentionHours = viewModel::setRetentionHours,
                                onToggleNotifications = viewModel::setNotificationsEnabled,
                                onClearHistory = viewModel::clearHistory
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerSheet(
            onQrScanned = { rawQr ->
                if (viewModel.joinChainFromQr(rawQr)) {
                    showQrScanner = false
                    navigateToTab(CodeChainRoute.Chain.route)
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showPasteOtpSheet) {
        PasteOtpModalSheet(
            initialText = pasteInitialText,
            isSending = uiState.isSending,
            onSyncOtp = { rawText, senderHint ->
                viewModel.extractAndSyncOtp(rawText, senderHint)
                showPasteOtpSheet = false
            },
            onDismiss = { showPasteOtpSheet = false }
        )
    }

    if (showNotificationAccessDialog && !hasNotificationAccess) {
        AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
            title = { Text(stringResource(R.string.notification_access_title)) },
            text = {
                Text(
                    "To automatically detect incoming SMS 2FA codes and sync them encrypted to your paired device, grant CodeChain Notification Access in Android Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationAccessDialog = false
                        openNotificationAccessSettings()
                    },
                    modifier = Modifier.testTag("dialog_grant_notification_access_button")
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationAccessDialog = false },
                    modifier = Modifier.testTag("dialog_later_notification_access_button")
                ) {
                    Text("Later")
                }
            }
        )
    }
}
