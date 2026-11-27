package fr.refusbot.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.refusbot.R
import fr.refusbot.refusBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Tuile « RefusBot » : met en pause / réactive le refus automatique. */
class PauseTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var enabled = true

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = MainScope().also { s ->
            s.launch {
                refusBot.settings.settings.collect {
                    enabled = it.enabled
                    render()
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        enabled = !enabled
        render()
        val newValue = enabled
        refusBot.appScope.launch { refusBot.settings.setEnabled(newValue) }
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_pause_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (enabled) R.string.tile_pause_state_active else R.string.tile_pause_state_paused)
        }
        tile.contentDescription =
            getString(if (enabled) R.string.tile_pause_description_active else R.string.tile_pause_description_paused)
        tile.updateTile()
    }
}
