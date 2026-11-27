package fr.refusbot.ui

import android.content.res.Resources
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.data.RulesRepository
import fr.refusbot.data.Settings
import fr.refusbot.data.LogRepository
import fr.refusbot.data.ShareHelper
import fr.refusbot.refusBot
import fr.refusbot.service.RefusBotAccessibilityService
import fr.refusbot.util.SystemStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)

@Composable
fun DiagnosticScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val app = context.refusBot
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val running by RefusBotAccessibilityService.running.collectAsState()
    val files by app.diagnostics.files.collectAsState()
    val activeRules by app.rulesRepository.active.collectAsState()
    var countdown by remember { mutableIntStateOf(0) }
    val status = remember(settings, running, activeRules, countdown) { systemStatusLines(context, res, settings, running, activeRules) }

    LaunchedEffect(Unit) { app.diagnostics.refresh() }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown--
            if (countdown == 0) {
                delay(2_500)
                app.diagnostics.refresh()
            }
        }
    }

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
                stringResource(R.string.set_diag_t),
                stringResource(R.string.diag_subtitle),
                onBack = onBack,
            )
        }

        item {
            SectionCard(title = stringResource(R.string.diag_status), icon = R.drawable.ic_info) {
                status.forEach { BodyText(it) }
                TextButton(
                    onClick = {
                        scope.launch {
                            val intent = ShareHelper.shareText(
                                context, "refusbot-etat.txt", "text/plain", status.joinToString("\n"), res.getString(R.string.diag_status_share_t),
                            )
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, res.getString(R.string.diag_no_app), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.diag_status_share)) }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.diag_capture), icon = R.drawable.ic_capture) {
                NumberedStep(1, stringResource(R.string.diag_step1))
                NumberedStep(2, stringResource(R.string.diag_step2))
                NumberedStep(3, stringResource(R.string.diag_step3))
                Button(
                    onClick = {
                        val service = RefusBotAccessibilityService.instance
                        if (service == null) {
                            Toast.makeText(context, res.getString(R.string.diag_need_service), Toast.LENGTH_LONG).show()
                        } else {
                            service.requestCapture(delayMs = 5_000, trigger = "bouton (5 s)")
                            countdown = 5
                        }
                    },
                    enabled = running && countdown == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (countdown > 0) stringResource(R.string.diag_countdown, countdown) else stringResource(R.string.diag_capture_btn))
                }
                if (!running) {
                    Text(
                        stringResource(R.string.diag_service_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                AutoDiagnosticSwitch(
                    checked = settings?.autoDiagnostic ?: false,
                    enabled = settings != null,
                    onChange = { v -> scope.launch { app.settings.setAutoDiagnostic(v) } },
                )
            }
        }

        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.diag_files, files.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (files.isNotEmpty()) {
                        TextButton(onClick = { app.diagnostics.deleteAll() }) { Text(stringResource(R.string.set_delete_all)) }
                    }
                }
                Text(
                    stringResource(R.string.diag_files_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Tout partager en un seul fichier .zip (27/11) : diagnostics + historique.
                if (files.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    app.journal.awaitLoaded()
                                    val history = LogRepository.exportJson(app.journal.entries.value)
                                    context.startActivity(app.diagnostics.shareAllIntent(history))
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, res.getString(R.string.diag_no_app), Toast.LENGTH_LONG).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, res.getString(R.string.diag_share_all_error), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.diag_share_all, files.size))
                    }
                    Text(
                        stringResource(R.string.diag_share_all_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (files.isEmpty()) {
            item {
                Text(stringResource(R.string.diag_no_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(files, key = { it.absolutePath }) { file ->
            DiagnosticFileRow(
                file = file,
                onShare = {
                    try {
                        context.startActivity(app.diagnostics.shareIntent(file))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, res.getString(R.string.diag_no_app), Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = { app.diagnostics.delete(file) },
            )
        }
    }
}

/** Résumé technique de l'installation : aucun nom d'appli surveillée, aucun texte d'écran. */
private fun systemStatusLines(
    context: Context,
    res: Resources,
    settings: Settings?,
    running: Boolean,
    rules: RulesRepository.Active,
): List<String> = listOf(
    "CYRAVA ${SystemStatus.appVersion(context)}",
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) — ${Build.MANUFACTURER} ${Build.MODEL}",
    res.getString(
        R.string.ds_a11y,
        res.getString(
            when {
                running -> R.string.ds_on
                SystemStatus.isAccessibilityEnabled(context) -> R.string.ds_stopped
                else -> R.string.ds_off
            },
        ),
    ),
    res.getString(R.string.ds_protection, res.getString(if (settings?.enabled != false) R.string.ds_enabled else R.string.ds_paused)),
    res.getString(R.string.ds_profile, settings?.policy?.let { res.getString(it.labelRes()) } ?: "?"),
    res.getString(R.string.ds_apps, settings?.monitored?.size ?: 0, settings?.observedApps?.size ?: 0),
    res.getString(R.string.ds_rules, res.getString(rules.source.labelRes()), rules.rules.version.toString()),
    res.getString(
        R.string.ds_battery,
        res.getString(if (SystemStatus.isIgnoringBatteryOptimizations(context)) R.string.ds_battery_off else R.string.ds_battery_on),
    ),
    res.getString(
        R.string.ds_notif,
        res.getString(if (SystemStatus.hasNotificationPermission(context)) R.string.ds_allowed else R.string.ds_denied),
    ),
    res.getString(R.string.ds_autodiag, res.getString(if (settings?.autoDiagnostic == true) R.string.ds_autodiag_on else R.string.ds_off)),
)

@Composable
private fun DiagnosticFileRow(file: File, onShare: () -> Unit, onDelete: () -> Unit) {
    SectionCard(title = file.name.removePrefix("diagnostic-").removeSuffix(".json")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    FILE_DATE.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.diag_size, ((file.length() + 1023) / 1024).toInt()), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.diag_share))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.diag_delete))
            }
        }
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.diag_share))
        }
    }
}
