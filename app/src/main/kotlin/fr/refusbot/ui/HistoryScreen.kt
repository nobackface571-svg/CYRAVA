package fr.refusbot.ui

import android.content.res.Resources
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.refusbot.R
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.TextNormalizer
import fr.refusbot.data.LogEntry
import fr.refusbot.data.LogRepository
import fr.refusbot.data.ShareHelper
import fr.refusbot.refusBot
import kotlinx.coroutines.launch

/** Filtres de l'historique. */
enum class HistoryFilter(@StringRes val labelRes: Int, val statuses: Set<OutcomeStatus>) {
    TOUT(R.string.hist_f_all, OutcomeStatus.entries.toSet()),
    VERIFIES(R.string.hist_f_verified, setOf(OutcomeStatus.OPTIONS_SUCCESS)),
    NON_VERIFIABLES(R.string.hist_f_unverified, setOf(OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS)),
    PARTIELS(R.string.hist_f_partial, setOf(OutcomeStatus.PARTIAL)),
    CAPTURES_INCOMPLETES(R.string.hist_f_capture, setOf(OutcomeStatus.INCOMPLETE_CAPTURE)),
    IMPOSSIBLES(R.string.hist_f_impossible, setOf(OutcomeStatus.IMPOSSIBLE)),
    OBSERVES(R.string.hist_f_observed, setOf(OutcomeStatus.OBSERVED)),
    INTERROMPUS(R.string.hist_f_interrupted, setOf(OutcomeStatus.INTERRUPTED)),
}

@Composable
fun HistoryScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val res = LocalResources.current
    val app = context.refusBot
    val scope = rememberCoroutineScope()
    val entries by app.journal.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.TOUT) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(entries, filter, query) {
        val q = TextNormalizer.normalize(query)
        entries.filter { e ->
            e.status in filter.statuses &&
                (q.isEmpty() || TextNormalizer.normalize(e.appName).contains(q) || e.packageName.contains(q) ||
                    TextNormalizer.normalize(e.platform).contains(q))
        }
    }

    fun export(csv: Boolean) {
        scope.launch {
            val intent = if (csv) {
                ShareHelper.shareText(context, "refusbot-historique.csv", "text/csv", LogRepository.exportCsv(shown), res.getString(R.string.hist_export_csv_t))
            } else {
                ShareHelper.shareText(context, "refusbot-historique.json", "application/json", LogRepository.exportJson(shown), res.getString(R.string.hist_export_json_t))
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, res.getString(R.string.hist_no_app), Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ok = entries.count { it.status.isVerified }
                PageHeader(
                    stringResource(R.string.hist_title),
                    stringResource(R.string.hist_subtitle, ok, entries.size, LogRepository.MAX_ENTRIES),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.hist_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.entries.forEach { f ->
                        val n = if (f == HistoryFilter.TOUT) entries.size else entries.count { it.status in f.statuses }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(R.string.hist_filter_count, stringResource(f.labelRes), n)) },
                        )
                    }
                }
                if (entries.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { export(csv = false) }, enabled = shown.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.hist_export_json))
                        }
                        OutlinedButton(onClick = { export(csv = true) }, enabled = shown.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.hist_export_csv))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.hist_export_note, shown.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.hist_clear)) }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) stringResource(R.string.hist_empty) else stringResource(R.string.hist_no_match),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { e -> e.id }) { e ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                HistoryRow(e, horizontalPadding = 14.dp)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.hist_clear_title)) },
            text = { Text(stringResource(R.string.hist_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { app.journal.clear() }
                }) { Text(stringResource(R.string.hist_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.apps_cancel)) } },
        )
    }
}

@Composable
fun HistoryRow(e: LogEntry, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    val color = statusColor(e.status)
    val res = LocalResources.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(e.packageName, sizeDp = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(e.summaryText(res), style = MaterialTheme.typography.bodyMedium, color = color)
            val details = buildList {
                if (e.status == OutcomeStatus.OBSERVED) add(stringResource(R.string.hist_d_observed))
                if (e.consentDetected) add(stringResource(R.string.hist_d_banner))
                if (e.controlsRecognized > 0) add(stringResource(R.string.hist_d_controls, e.controlsRecognized))
                if (e.actionAttempts > 0) add(stringResource(R.string.hist_d_attempts, e.actionAttempts))
                if (e.actionExecutions > 0) add(stringResource(R.string.hist_d_exec, e.actionExecutions))
                if (e.blockedActions > 0) add(stringResource(R.string.hist_d_blocked, e.blockedActions))
                when (e.stateVerified) {
                    true -> add(stringResource(R.string.hist_d_verified))
                    false -> add(stringResource(R.string.hist_d_unverified))
                    null -> if (e.status == OutcomeStatus.OBSERVED) add(stringResource(R.string.hist_d_no_action))
                }
                if (e.captureTruncated) add(stringResource(R.string.hist_d_truncated))
                if (e.platform != "inconnue") add(e.platform)
                add(stringResource(R.string.hist_d_confidence, stringResource(e.confidence.labelRes())))
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.interfaceChanged) {
                Text(
                    stringResource(R.string.hist_new_ui),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(formatDateTime(e.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(statusIcon(e.status)),
            contentDescription = e.status.text(),
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}
