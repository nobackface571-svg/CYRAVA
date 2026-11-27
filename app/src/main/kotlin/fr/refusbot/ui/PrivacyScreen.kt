package fr.refusbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.data.DiagnosticsRepository
import fr.refusbot.data.LogRepository
import fr.refusbot.util.SystemStatus

/** Page « Confidentialité et autorisations » : ce que l'appli peut faire, et ce qu'elle garde. */
@Composable
fun PrivacyScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val openAccessibility = rememberAccessibilityOpener()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.priv_title),
                stringResource(R.string.priv_subtitle),
                onBack = onBack,
            )
        }
        item {
            SectionCard(title = stringResource(R.string.priv_used), icon = R.drawable.ic_lock) {
                Point(stringResource(R.string.priv_a11y_t), stringResource(R.string.priv_a11y))
                Point(stringResource(R.string.priv_notif_t), stringResource(R.string.priv_notif))
                Point(stringResource(R.string.priv_battery_t), stringResource(R.string.priv_battery))
                Point(stringResource(R.string.priv_apps_t), stringResource(R.string.priv_apps))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.priv_absent), icon = R.drawable.ic_check, tone = CardTone.OK) {
                Point(stringResource(R.string.priv_internet_t), stringResource(R.string.priv_internet))
                Point(stringResource(R.string.priv_other_t), stringResource(R.string.priv_other))
                Point(stringResource(R.string.priv_images_t), stringResource(R.string.priv_images))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.priv_stored), icon = R.drawable.ic_rules) {
                Point(stringResource(R.string.priv_settings_t), stringResource(R.string.priv_settings))
                Point(stringResource(R.string.priv_history_t), stringResource(R.string.priv_history, LogRepository.MAX_ENTRIES))
                Point(stringResource(R.string.priv_diag_t), stringResource(R.string.priv_diag, DiagnosticsRepository.MAX_FILES))
                Point(stringResource(R.string.priv_rules_t), stringResource(R.string.priv_rules))
                BodyText(stringResource(R.string.priv_erase))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.priv_never), icon = R.drawable.ic_shield) {
                Point(stringResource(R.string.priv_accept_t), stringResource(R.string.priv_accept))
                Point(stringResource(R.string.priv_doubt_t), stringResource(R.string.priv_doubt))
                Point(stringResource(R.string.priv_claim_t), stringResource(R.string.priv_claim))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.priv_check), icon = R.drawable.ic_settings) {
                OutlinedButton(onClick = { SystemStatus.openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.priv_open_details))
                }
                OutlinedButton(onClick = { openAccessibility() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.priv_open_a11y))
                }
            }
        }
    }
}

@Composable
private fun Point(title: String, text: String) {
    BodyText(stringResource(R.string.priv_point, title, text))
}
