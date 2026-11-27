package fr.refusbot.tile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import fr.refusbot.R
import fr.refusbot.service.RefusBotAccessibilityService

/**
 * Tuile « Capturer l'écran » (mode diagnostic) : enregistre l'arbre d'accessibilité de l'appli
 * surveillée affichée sous le volet des réglages rapides.
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = if (RefusBotAccessibilityService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.tile_capture_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(R.string.tile_capture_subtitle)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val service = RefusBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.diag_need_service), Toast.LENGTH_LONG).show()
            return
        }
        // Refermer le volet pour que l'appli redevienne la fenêtre active.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.collapseShade()
        } else {
            closeSystemDialogsLegacy()
        }
        Toast.makeText(this, getString(R.string.capture_soon), Toast.LENGTH_SHORT).show()
        service.requestCapture(delayMs = 1_500, trigger = "tuile")
    }

    /**
     * Avant Android 12, cette diffusion ferme le volet sans aucune permission ; la permission
     * BROADCAST_CLOSE_SYSTEM_DIALOGS signalée par lint n'existe qu'à partir d'Android 12, où l'on
     * utilise à la place l'action globale du service d'accessibilité.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun closeSystemDialogsLegacy() {
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }
}
