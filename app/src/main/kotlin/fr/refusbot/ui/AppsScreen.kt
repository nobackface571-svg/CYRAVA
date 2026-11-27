package fr.refusbot.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.annotation.StringRes

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.refusbot.R
import fr.refusbot.core.BrowserPolicy
import fr.refusbot.core.Compatibility
import fr.refusbot.core.CompatibilityRating
import fr.refusbot.core.ConsentOrPay
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.ResultCategory
import fr.refusbot.core.ResultSummary
import fr.refusbot.core.SensitiveAppPolicy
import fr.refusbot.core.SystemPackagePolicy
import fr.refusbot.core.TextNormalizer
import fr.refusbot.data.AppsRepository
import fr.refusbot.data.InstalledApp
import fr.refusbot.data.LogEntry
import fr.refusbot.data.interfaceKey
import fr.refusbot.refusBot
import fr.refusbot.service.RefusBotAccessibilityService
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.refusBot
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = AppsRepository.launchableApps(context)
    }
    val entries by app.journal.entries.collectAsState()
    val accessibilityRunning by RefusBotAccessibilityService.running.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    var onlyMonitored by rememberSaveable { mutableStateOf(false) }
    var hideSystem by rememberSaveable { mutableStateOf(false) }
    val monitored = settings?.monitored.orEmpty()
    val observed = settings?.observedApps.orEmpty()
    val ratings = remember(entries, settings) { compatibilityByApp(entries, settings?.acknowledgedInterfaces.orEmpty()) }
    // Même définition que l'onglet Compatibilité : les interruptions ne remplacent pas
    // le dernier résultat exploitable d'une application.
    val latestByApp = remember(entries) {
        entries.groupBy { it.packageName }.mapNotNull { (pkg, rows) ->
            rows.firstOrNull { it.status != OutcomeStatus.INTERRUPTED }?.let { pkg to it }
        }.toMap()
    }
    val summary = remember(latestByApp, monitored) { ResultSummary.of(monitored, latestByApp.mapValues { it.value.status }) }
    // La sélection groupée épargne les applications financières, d'authentification et anti-triche.
    // Elles restent sélectionnables individuellement après décision explicite de l'utilisateur.

    val filtered = remember(apps, query, onlyMonitored, hideSystem, monitored) {
        val q = TextNormalizer.normalize(query)
        apps.orEmpty().filter {
            SystemPackagePolicy.isMonitorable(it.packageName, context.packageName) &&
                (q.isEmpty() || TextNormalizer.normalize(it.label).contains(q) || it.packageName.contains(q)) &&
                (!onlyMonitored || it.packageName in monitored) &&
                (!hideSystem || !it.system || it.packageName in monitored)
        }
    }
    // Samsung Internet (non pris en charge) n'est jamais coché par « Tout cocher ».
    val safeForBulk = remember(filtered) { SensitiveAppPolicy.safeForBulkSelection(filtered.map { it.packageName }) }
    val sensitiveSkipped = filtered.size - safeForBulk.size
    val bulkSelection = remember(safeForBulk) { safeForBulk.filterNot(BrowserPolicy::isUnsupported) }
    val unsupportedShown = filtered.any { BrowserPolicy.isUnsupported(it.packageName) }
    val openFail = stringResource(R.string.apps_open_fail)

    // Tout est dans une seule liste : l'en-tête défile avec les applis (petits écrans).
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "entete") {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader(
                    stringResource(R.string.apps_title),
                    stringResource(R.string.apps_subtitle, monitored.size),
                )
                SectionCard(title = stringResource(R.string.apps_summary_t), icon = R.drawable.ic_info) {
                    BodyText(stringResource(R.string.apps_summary_intro, summary.selected))
                    ResultCategory.entries.forEach { c ->
                        val n = summary.count(c)
                        if (n > 0) BodyText(stringResource(R.string.apps_summary_line, c.text(), n))
                    }
                    BodyText(stringResource(R.string.apps_summary_none, summary.noResult))
                    BodyText(stringResource(R.string.apps_summary_note))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.apps_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyMonitored,
                        onClick = { onlyMonitored = !onlyMonitored },
                        label = { Text(stringResource(R.string.apps_only_monitored)) },
                    )
                    FilterChip(
                        selected = hideSystem,
                        onClick = { hideSystem = !hideSystem },
                        label = { Text(stringResource(R.string.apps_hide_system)) },
                    )
                }
                if (sensitiveSkipped > 0) {
                    Text(
                        stringResource(R.string.apps_sensitive, sensitiveSkipped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // Au-delà de quelques applis, on demande confirmation (risque anti-triche).
                            if (bulkSelection.size > CONFIRM_SELECT_ALL_ABOVE) {
                                confirmAll = true
                            } else {
                                scope.launch { app.settings.setMonitored(bulkSelection, true) }
                            }
                        },
                        enabled = bulkSelection.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.apps_tick_all)) }
                    OutlinedButton(
                        onClick = { scope.launch { app.settings.setMonitored(filtered.map { it.packageName }, false) } },
                        enabled = filtered.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.apps_untick_all)) }
                }
                if (unsupportedShown) {
                    Text(
                        stringResource(R.string.apps_sbrowser_skipped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (query.isNotBlank() || onlyMonitored || hideSystem) {
                    Text(
                        stringResource(R.string.apps_filter_note, filtered.size, sensitiveSkipped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
        }
        when {
            apps == null -> item(key = "chargement") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            filtered.isEmpty() -> item(key = "vide") {
                Text(
                    stringResource(R.string.apps_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(filtered, key = { it.packageName }) { item ->
            AppRow(
                item = item,
                checked = item.packageName in monitored,
                observeOnly = item.packageName in observed,
                compatibility = ratings[item.packageName],
                lastResult = latestByApp[item.packageName],
                enabled = settings != null,
                canLaunchTest = item.packageName in monitored && settings?.enabled == true && accessibilityRunning,
                onLaunchTest = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (launchIntent == null) {
                        Toast.makeText(context, openFail, Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            context.startActivity(launchIntent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, openFail, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onToggle = { v -> scope.launch { app.settings.setMonitored(item.packageName, v) } },
                onObserveChange = { v -> scope.launch { app.settings.setObservation(item.packageName, v) } },
            )
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.apps_confirm_title, bulkSelection.size)) },
            text = {
                Text(stringResource(R.string.apps_confirm_text, sensitiveSkipped))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    scope.launch { app.settings.setMonitored(bulkSelection, true) }
                }) { Text(stringResource(R.string.apps_tick_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.apps_cancel)) } },
        )
    }
}

@Composable
private fun AppRow(
    item: InstalledApp,
    checked: Boolean,
    observeOnly: Boolean,
    compatibility: Compatibility?,
    lastResult: LogEntry?,
    enabled: Boolean,
    canLaunchTest: Boolean,
    onLaunchTest: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onObserveChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(item.packageName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lastResult != null) {
                    Text(
                        stringResource(R.string.apps_last_result, ResultCategory.of(lastResult.status).text()),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(lastResult.status),
                    )
                    Text(
                        reasonText(LocalResources.current, lastResult.reason)?.takeIf { it.isNotBlank() }
                            ?: lastResultExplanation(lastResult),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.apps_last_on, formatDateTime(lastResult.time)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (checked) {
                    Text(
                        stringResource(R.string.apps_no_window),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (BrowserPolicy.isUnsupported(item.packageName)) {
                    Text(
                        stringResource(R.string.apps_sbrowser_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.apps_sbrowser),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checked && compatibility != null) {
                    Text(
                        stringResource(R.string.apps_compat, compatibility.text()),
                        style = MaterialTheme.typography.bodySmall,
                        color = compatibilityColor(compatibility),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (checked) {
            // Mode de l'appli : refus automatique (profil global) ou observation seule (jamais d'action).
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !observeOnly,
                    onClick = { onObserveChange(false) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.apps_mode_auto)) },
                )
                FilterChip(
                    selected = observeOnly,
                    onClick = { onObserveChange(true) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.apps_mode_observe)) },
                )
            }
            OutlinedButton(
                onClick = onLaunchTest,
                enabled = canLaunchTest,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            ) { Text(stringResource(R.string.apps_open_test)) }
            Text(
                if (canLaunchTest) stringResource(R.string.apps_test_hint) else stringResource(R.string.apps_test_off),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}

/** Explication du dernier résultat constaté dans l'appli (usage réel, jamais le laboratoire). */
@Composable
private fun lastResultExplanation(entry: LogEntry): String = when (entry.status) {
    OutcomeStatus.OPTIONS_SUCCESS -> if (entry.togglesOff == 0) {
        stringResource(R.string.apps_res_options_none)
    } else {
        stringResource(R.string.apps_res_options_n, entry.togglesOff)
    }
    else -> stringResource(lastResultExplanationRes(entry))
}

@StringRes
private fun lastResultExplanationRes(entry: LogEntry): Int = when (entry.status) {
    OutcomeStatus.OPTIONS_SUCCESS -> R.string.apps_res_options_none
    OutcomeStatus.PARTIAL -> R.string.apps_res_partial
    OutcomeStatus.HANDED_OVER -> R.string.apps_res_handed
    OutcomeStatus.IMPOSSIBLE -> R.string.apps_res_impossible
    OutcomeStatus.INCOMPLETE_CAPTURE -> R.string.apps_res_capture
    OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> R.string.apps_res_unverified
    OutcomeStatus.INTERRUPTED -> R.string.apps_res_interrupted
    OutcomeStatus.OBSERVED -> R.string.apps_res_observed
}

/** Compatibilité de chaque appli d'après l'historique réel (applis sans historique : absentes). */
fun compatibilityByApp(entries: List<LogEntry>, acknowledged: Set<String> = emptySet()): Map<String, Compatibility> =
    entries.groupBy { it.packageName }.mapValues { (pkg, list) ->
        // L'historique est trié du plus récent au plus ancien.
        // Un mur payant ne dit rien de la compatibilité de l'appli (voir CompatibilityRating.rateHistory).
        val lastRelevant = list.firstOrNull { it.status != OutcomeStatus.INTERRUPTED && !ConsentOrPay.isPaywall(it.reason) }
        val changed = lastRelevant != null && lastRelevant.interfaceChanged &&
            interfaceKey(pkg, lastRelevant.signature) !in acknowledged
        CompatibilityRating.rateHistory(list.map { it.status to it.reason }, changed)
    }

@Composable
fun compatibilityColor(c: Compatibility): Color = when (c) {
    Compatibility.COMPATIBLE -> MaterialTheme.colorScheme.primary
    Compatibility.REFUS_CLIQUE -> MaterialTheme.colorScheme.secondary
    Compatibility.PARTIELLE, Compatibility.A_VERIFIER -> MaterialTheme.colorScheme.tertiary
    Compatibility.NON_PRISE_EN_CHARGE -> MaterialTheme.colorScheme.error
    Compatibility.OBSERVEE, Compatibility.INCONNUE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun AppIcon(packageName: String, sizeDp: Int = 40) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = AppsRepository.cachedIcon(packageName), packageName) {
        if (value == null) value = AppsRepository.icon(context, packageName)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    } else {
        Box(Modifier.size(sizeDp.dp))
    }
}

private const val CONFIRM_SELECT_ALL_ABOVE = 10
