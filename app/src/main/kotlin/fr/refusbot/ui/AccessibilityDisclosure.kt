package fr.refusbot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.refusBot
import fr.refusbot.util.SystemStatus
import kotlinx.coroutines.launch

/**
 * Écran d'information obligatoire avant d'activer le service d'accessibilité (règle Google Play
 * « prominent disclosure ») : ce qui est lu, ce qui est fait, ce qui ne l'est jamais, et un accord
 * explicite. Tant que l'utilisateur n'a pas appuyé sur « J'accepte », les réglages d'accessibilité
 * ne sont pas ouverts depuis l'appli.
 *
 * Renvoie l'action à utiliser à la place de [SystemStatus.openAccessibilitySettings].
 */
@Composable
fun rememberAccessibilityOpener(): () -> Unit {
    val context = LocalContext.current
    val app = context.refusBot
    val settings by app.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    if (show) {
        AccessibilityDisclosureDialog(
            onAccept = {
                show = false
                scope.launch { app.settings.setDisclosureAccepted(true) }
                SystemStatus.openAccessibilitySettings(context)
            },
            onDecline = { show = false },
        )
    }
    return {
        if (settings?.disclosureAccepted == true) SystemStatus.openAccessibilitySettings(context) else show = true
    }
}

@Composable
fun AccessibilityDisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    @Composable
    fun title(id: Int) = Text("\n" + stringResource(id), style = MaterialTheme.typography.titleSmall)

    @Composable
    fun body(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.bodyMedium)
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.disc_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                body(R.string.disc_intro)
                title(R.string.disc_reads_title)
                body(R.string.disc_reads)
                title(R.string.disc_does_title)
                body(R.string.disc_does)
                title(R.string.disc_data_title)
                body(R.string.disc_data)
                Text("\n" + stringResource(R.string.disc_disable), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.disc_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.disc_decline)) } },
    )
}
