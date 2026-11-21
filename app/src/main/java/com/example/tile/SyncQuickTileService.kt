package com.example.tile

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.CodeChainApplication
import com.example.MainActivity
import com.example.R
import com.example.data.prefs.ChainSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncQuickTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? CodeChainApplication ?: return
        tileScope.launch {
            val settings = app.preferencesRepository.state.first()
            if (settings.activeChain == null) {
                openAppToPair()
                return@launch
            }
            val nextEnabled = !settings.isSyncEnabled
            app.preferencesRepository.setSyncEnabled(nextEnabled)
            applyStateToTile(settings.copy(isSyncEnabled = nextEnabled))
        }
    }

    private fun refreshTileState() {
        val app = applicationContext as? CodeChainApplication ?: return
        tileScope.launch {
            val settings = app.preferencesRepository.state.first()
            applyStateToTile(settings)
        }
    }

    private fun applyStateToTile(settings: ChainSettingsState) {
        val tile = qsTile ?: return
        val chain = settings.activeChain
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_codechain)

        when {
            chain == null -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.qs_tile_subtitle_unpaired)
                }
            }
            settings.isSyncEnabled -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.qs_tile_subtitle_active)
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.qs_tile_subtitle_paused)
                }
            }
        }
        tile.updateTile()
    }

    private fun openAppToPair() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, SyncQuickTileService::class.java)
                )
            }
        }

        fun requestAddTile(context: Context, onResultMessage: (String) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                if (statusBarManager == null) {
                    onResultMessage("Swipe down twice and tap Edit to add the OTP Chain tile.")
                    return
                }
                statusBarManager.requestAddTileService(
                    ComponentName(context, SyncQuickTileService::class.java),
                    context.getString(R.string.qs_tile_label),
                    Icon.createWithResource(context, R.drawable.ic_stat_codechain),
                    context.mainExecutor
                ) { resultCode ->
                    val message = when (resultCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                            "OTP Chain Quick Tile added to Quick Settings!"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                            "OTP Chain Quick Tile is already in your Quick Settings."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                            "Quick Tile was not added."
                        else ->
                            "Swipe down twice and tap Edit to add the OTP Chain tile."
                    }
                    onResultMessage(message)
                }
            } else {
                onResultMessage("Swipe down twice from top and tap the Edit pencil to add the OTP Chain tile.")
            }
        }
    }
}
