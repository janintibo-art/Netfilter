package com.example.netfilter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tuile "Réglages rapides". Pour démarrer, on ouvre l'app (une Activity peut toujours
 * lancer le service au premier plan et gérer l'autorisation VPN) ; pour arrêter, on
 * commande directement le service. Ça évite les plantages liés aux restrictions
 * de lancement de service en arrière-plan (Android 12+).
 */
class FilterTileService : TileService() {

    companion object {
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, FilterTileService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onStartListening() = updateTile()

    override fun onClick() {
        if (FilterVpnService.isRunning) {
            startService(
                Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_STOP)
            )
        } else {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_AUTO_START, true)
            )
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (FilterVpnService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "NetFilter"
        tile.updateTile()
    }
}
