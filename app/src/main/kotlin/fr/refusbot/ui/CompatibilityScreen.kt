package fr.refusbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.core.BrowserPolicy
import fr.refusbot.core.Compatibility
import fr.refusbot.core.CompatibilityRating
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.data.AppsRepository
import fr.refusbot.data.LogEntry
import fr.refusbot.data.Settings
import fr.refusbot.refusBot
import kotlinx.coroutines.launch

/** Bilan d'une appli, à partir de son historique réel. */
private data class AppReport(
    val packageName: String,
    val appName: String,
    val monitored: Boolean,
    val observeOnly: Boolean,
    val compatibility: Compatibility,
    val last: LogEntry?,
    /** Dernière fenêtre hors interruptions (celle qui détermine « interface modifiée »). */
    val lastRelevant: LogEntry?,
    val counts: Map<OutcomeStatus, Int>,
    val platforms: List<String>,
)

private fun buildReports(context: android.content.Context, settings: Settings?, entries: List<LogEntry>): List<AppReport> {
    val byApp = entries.groupBy { it.packageName }
    val ratings = compatibilityByApp(entries, settings?.acknowledgedInterfaces.orEmpty())
    val packages = (settings?.monitored.orEmpty() + byApp.keys)
    return packages.map { pkg ->
        val list = byApp[pkg].orEmpty()
        AppReport(
            packageName = pkg,
            appName = list.firstOrNull()?.appName ?: AppsRepository.label(context, pkg),
            monitored = settings?.monitored?.contains(pkg) == true,
            observeOnly = settings?.observedApps?.contains(pkg) == true,
            compatibility = ratings[pkg] ?: Compatibility.INCONNUE,
            last = list.firstOrNull { it.status != OutcomeStatus.INTERRUPTED },
            lastRelevant = list.firstOrNull { it.status != OutcomeStatus.INTERRUPTED },
            counts = list.groupingBy { it.status }.eachCount(),
            platforms = list.map { it.platform }.filter { it != "inconnue" }.distinct(),
        )
    }.sortedWith(compareByDescending<AppReport> { it.monitored }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
}

@Composable
fun CompatibilityScreen(padding: PaddingValues, onOpenDiagnostic: () -> Unit) {
    val context = LocalContext.current
    val app = context.refusBot
    val settings by app.settings.settings.collectAsState(initial = null)
    val entries by app.journal.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var showNoResult by rememberSaveable { mutableStateOf(false) }
    val reports = remember(settings, entries) { buildReports(context, settings, entries) }
    val reportsWithResult = remember(reports) { reports.filter { it.lastRelevant != null } }
    val reportsWithoutResult = remember(reports) { reports.filter { it.lastRelevant == null } }

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
                stringResource(R.string.cmp_title),
                stringResource(R.string.cmp_subtitle),
            )
        }
        item {
            SectionCard(title = stringResource(R.string.cmp_overview), icon = R.drawable.ic_phone) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${reports.size}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.cmp_followed), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${reportsWithResult.size}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.cmp_with_result), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${reports.count { it.monitored }}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.cmp_monitored), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                BodyText(stringResource(R.string.cmp_overview_note))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.cmp_how), icon = R.drawable.ic_info) {
                Compatibility.entries.forEach { c ->
                    Row {
                        Text(
                            c.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = compatibilityColor(c),
                            modifier = Modifier.width(120.dp),
                        )
                        Text(stringResource(c.detailRes()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (reports.isEmpty()) {
            item { BodyText(stringResource(R.string.cmp_none)) }
        } else {
            if (reportsWithResult.isEmpty()) {
                item { BodyText(stringResource(R.string.cmp_no_result)) }
            }
            items(reportsWithResult, key = { it.packageName }) { r ->
                AppReportCard(r, settings, onOpenDiagnostic) { signature ->
                    scope.launch { app.settings.acknowledgeInterface(r.packageName, signature) }
                }
            }
            if (reportsWithoutResult.isNotEmpty()) {
                item(key = "sans-resultat-toggle") {
                    TextButton(onClick = { showNoResult = !showNoResult }) {
                        Text(
                            stringResource(
                                if (showNoResult) R.string.cmp_hide_n else R.string.cmp_show_n,
                                reportsWithoutResult.size,
                            ),
                        )
                    }
                }
                if (showNoResult) {
                    items(reportsWithoutResult, key = { "sans-resultat-${it.packageName}" }) { r ->
                        AppReportCard(r, settings, onOpenDiagnostic) { signature ->
                            scope.launch { app.settings.acknowledgeInterface(r.packageName, signature) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppReportCard(r: AppReport, settings: Settings?, onOpenDiagnostic: () -> Unit, onAcknowledge: (Int) -> Unit) {
    val tone = when (r.compatibility) {
        Compatibility.COMPATIBLE -> CardTone.OK
        Compatibility.REFUS_CLIQUE -> CardTone.NORMAL
        Compatibility.PARTIELLE, Compatibility.A_VERIFIER -> CardTone.WARNING
        Compatibility.NON_PRISE_EN_CHARGE -> CardTone.ERROR
        Compatibility.OBSERVEE, Compatibility.INCONNUE -> CardTone.NORMAL
    }
    SectionCard(title = r.appName, tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(r.packageName, sizeDp = 32)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.apps_compat, r.compatibility.text()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = compatibilityColor(r.compatibility),
                )
                Text(stringResource(r.compatibility.detailRes()), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (BrowserPolicy.isUnsupported(r.packageName)) {
            Text(
                stringResource(R.string.apps_sbrowser),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val mode = when {
            !r.monitored -> stringResource(R.string.cmp_mode_off)
            r.observeOnly -> stringResource(R.string.cmp_mode_observe)
            else -> stringResource(R.string.cmp_mode_auto, settings?.policy?.text() ?: "?")
        }
        BodyText(stringResource(R.string.cmp_mode, mode))
        if (r.platforms.isNotEmpty()) {
            val res = LocalResources.current
            BodyText(stringResource(R.string.cmp_platforms, r.platforms.joinToString(", ") { platformText(res, it) }))
        }
        if (r.counts.isNotEmpty()) {
            val lines = OutcomeStatus.entries.filter { (r.counts[it] ?: 0) > 0 }
                .map { stringResource(R.string.cmp_count, it.text(), r.counts[it] ?: 0) }
            BodyText(lines.joinToString(" · "))
        }
        r.last?.let { BodyText(stringResource(R.string.cmp_last, formatDateTime(it.time), it.status.text())) }
        val changedEntry = r.lastRelevant?.takeIf { r.compatibility == Compatibility.A_VERIFIER && it.signature != 0 }
        if (changedEntry != null) {
            BodyText(stringResource(R.string.cmp_prudent))
            OutlinedButton(onClick = { onAcknowledge(changedEntry.signature) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cmp_ack))
            }
        }
        if (r.compatibility == Compatibility.NON_PRISE_EN_CHARGE || r.compatibility == Compatibility.PARTIELLE ||
            r.compatibility == Compatibility.A_VERIFIER
        ) {
            TextButton(onClick = onOpenDiagnostic) { Text(stringResource(R.string.cmp_open_diag)) }
        }
    }
}
