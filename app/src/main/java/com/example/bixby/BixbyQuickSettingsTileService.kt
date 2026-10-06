package com.example.bixby

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BixbyQuickSettingsTileService : TileService() {

    companion object {
        private const val PREFS = "bixby_floating_access"
        private const val PREF_ENABLED = "enabled"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_ENABLED, false)

        if (enabled) {
            stopService(Intent(this, BixbyFloatingAccessService::class.java))
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
        } else {
            val serviceIntent = Intent(this, BixbyFloatingAccessService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                prefs.edit().putBoolean(PREF_ENABLED, true).apply()
            } catch (_: Exception) {
                prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            }
        }

        updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        stopService(Intent(this, BixbyFloatingAccessService::class.java))
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(PREF_ENABLED, false)
            .apply()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            val enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false)

            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.icon = Icon.createWithResource(this, R.drawable.ic_bixby_tile)
            }

            tile.label = "Bixby Access"
            tile.contentDescription = "Bixby floating access"
            tile.updateTile()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    2102,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
