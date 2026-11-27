package fr.refusbot.ui

import android.content.res.Resources
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.refusbot.R
import fr.refusbot.core.Policy
import fr.refusbot.data.NotifyLevel
import fr.refusbot.data.RulesRepository
import fr.refusbot.data.ShareHelper
import fr.refusbot.data.ThemeMode
import fr.refusbot.refusBot
import fr.refusbot.util.SystemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Pages de l'onglet Réglages. */
enum class SettingsPage { MENU, DIAGNOSTIC, LABORATOIRE, CONFIDENTIALITE }

@Composable
fun SettingsScreen(padding: PaddingValues, page: SettingsPage, onNavigate: (SettingsPage) -> Unit) {
    val back = { onNavigate(SettingsPage.MENU) }
    BackHandler(enabled = page != SettingsPage.MENU, onBack = back)
    when (page) {
        SettingsPage.MENU -> SettingsMenu(padding, onNavigate)
        SettingsPage.DIAGNOSTIC -> DiagnosticScreen(padding, onBack = back)
        SettingsPage.LABORATOIRE -> LabScreen(padding, onBack = back)
        SettingsPage.CONFIDENTIALITE -> PrivacyScreen(padding, onBack = back)
    }
}

/** Message affiché dans une boîte de dialogue (résultat d'un import, d'une restauration…). */
private data class Notice(val title: String, val text: String)

@Composable
private fun SettingsMenu(padding: PaddingValues, onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val app = context.refusBot
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val activeRules by app.rulesRepository.active.collectAsState()
    val files by app.diagnostics.files.collectAsState()
    var notice by remember { mutableStateOf<Notice?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifOk = remember(refresh) { SystemStatus.hasNotificationPermission(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                notice = try {
                    val report = app.rulesRepository.import(readText(context, res, uri))
                    if (report.isValid) {
                        Notice(
                            res.getString(R.string.set_rules_ok_t),
                            res.getString(R.string.set_rules_ok) +
                                if (report.warnings.isEmpty()) "" else "\n\n" + res.getString(R.string.set_rules_remarks) + "\n" + report.warnings.joinToString("\n") { "• $it" },
                        )
                    } else {
                        Notice(
                            res.getString(R.string.set_rules_ko_t),
                            res.getString(R.string.set_rules_ko) + "\n" + report.errors.take(10).joinToString("\n") { "• $it" } +
                                if (report.errors.size > 10) "\n" + res.getString(R.string.set_rules_more, report.errors.size - 10) else "",
                        )
                    }
                } catch (e: IOException) {
                    Notice(res.getString(R.string.set_read_ko_t), e.message ?: res.getString(R.string.set_read_ko))
                } catch (_: SecurityException) {
                    Notice(res.getString(R.string.set_read_ko_t), res.getString(R.string.set_read_denied))
                }
            }
        }
    }

    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                notice = try {
                    writeText(context, res, uri, app.settings.exportJson())
                    Notice(res.getString(R.string.set_export_ok_t), res.getString(R.string.set_export_ok))
                } catch (e: IOException) {
                    Notice(res.getString(R.string.set_export_ko_t), e.message ?: res.getString(R.string.set_export_ko))
                } catch (_: SecurityException) {
                    Notice(res.getString(R.string.set_export_ko_t), res.getString(R.string.set_export_denied))
                }
            }
        }
    }

    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                notice = try {
                    val count = app.settings.importJson(readText(context, res, uri))
                    Notice(res.getString(R.string.set_restore_ok_t), res.getString(R.string.set_restore_ok, count))
                } catch (e: IllegalArgumentException) {
                    Notice(res.getString(R.string.set_restore_ko_t), res.getString(R.string.set_restore_ko, e.message ?: res.getString(R.string.set_invalid)))
                } catch (e: IOException) {
                    Notice(res.getString(R.string.set_read_ko_t), e.message ?: res.getString(R.string.set_read_ko))
                } catch (_: SecurityException) {
                    Notice(res.getString(R.string.set_read_ko_t), res.getString(R.string.set_read_denied))
                }
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
        item { PageHeader(stringResource(R.string.set_title)) }

        // --- Profil de refus ----------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_profile), icon = R.drawable.ic_shield) {
                Policy.entries.forEach { p ->
                    RadioRow(
                        label = p.text(),
                        detail = stringResource(p.detailRes()),
                        selected = settings?.policy == p,
                        enabled = settings != null,
                        onClick = { scope.launch { app.settings.setPolicy(p) } },
                    )
                }
                BodyText(stringResource(R.string.set_profile_note))
                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.set_hold_t),
                    supporting = stringResource(R.string.set_hold),
                    checked = settings?.holdIfUnverifiable ?: false,
                    enabled = settings != null,
                    onCheckedChange = { v -> scope.launch { app.settings.setHoldIfUnverifiable(v) } },
                )
                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.set_full_t),
                    supporting = stringResource(R.string.set_full),
                    checked = settings?.completeRefusal ?: false,
                    enabled = settings != null,
                    onCheckedChange = { v -> scope.launch { app.settings.setCompleteRefusal(v) } },
                )
            }
        }

        // --- Diagnostic et outils -------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_tools), icon = R.drawable.ic_bug) {
                AutoDiagnosticSwitch(
                    checked = settings?.autoDiagnostic ?: false,
                    enabled = settings != null,
                    onChange = { v -> scope.launch { app.settings.setAutoDiagnostic(v) } },
                )
                HorizontalDivider()
                NavRow(
                    title = stringResource(R.string.set_diag_t),
                    subtitle = when (files.size) {
                        0 -> stringResource(R.string.set_diag_0)
                        1 -> stringResource(R.string.set_diag_1)
                        else -> stringResource(R.string.set_diag_n, files.size)
                    },
                    icon = R.drawable.ic_capture,
                    onClick = { onNavigate(SettingsPage.DIAGNOSTIC) },
                )
                NavRow(
                    title = stringResource(R.string.set_lab_t),
                    subtitle = stringResource(R.string.set_lab),
                    icon = R.drawable.ic_lab,
                    onClick = { onNavigate(SettingsPage.LABORATOIRE) },
                )
                NavRow(
                    title = stringResource(R.string.priv_title),
                    subtitle = stringResource(R.string.set_priv),
                    icon = R.drawable.ic_lock,
                    onClick = { onNavigate(SettingsPage.CONFIDENTIALITE) },
                )
            }
        }

        // --- Notifications ------------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_notif), icon = R.drawable.ic_info) {
                if (!notifOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    BodyText(stringResource(R.string.set_notif_denied))
                    Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_notif_action))
                    }
                    TextButton(onClick = { SystemStatus.openNotificationSettings(context) }) { Text(stringResource(R.string.set_notif_open)) }
                    HorizontalDivider()
                }
                NotifyLevel.entries.forEach { level ->
                    RadioRow(
                        label = stringResource(level.labelRes()),
                        detail = stringResource(level.detailRes()),
                        selected = settings?.notifyLevel == level,
                        enabled = settings != null,
                        onClick = { scope.launch { app.settings.setNotifyLevel(level) } },
                    )
                }
            }
        }

        // --- Langue (Android 13 et plus : langue propre à l'appli) ------------------------------
        item {
            SectionCard(title = stringResource(R.string.lang_section), icon = R.drawable.ic_info) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LanguageChoices(context)
                } else {
                    BodyText(stringResource(R.string.lang_hint_old_android))
                }
                BodyText(stringResource(R.string.lang_note))
            }
        }

        // --- Apparence ----------------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_theme), icon = R.drawable.ic_phone) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(mode.labelRes()),
                        selected = settings?.theme == mode,
                        enabled = settings != null,
                        onClick = { scope.launch { app.settings.setTheme(mode) } },
                    )
                }
            }
        }

        // --- Règles de détection --------------------------------------------------------------
        item {
            val rules = activeRules.rules
            SectionCard(title = stringResource(R.string.set_rules), icon = R.drawable.ic_rules) {
                BodyText(
                    stringResource(
                        R.string.set_rules_used,
                        stringResource(activeRules.source.labelRes()),
                        rules.version.toString(),
                        rules.platforms.size,
                        rules.refuse.size + rules.globalReject.size,
                    ),
                )
                BodyText(stringResource(R.string.set_rules_note))
                OutlinedButton(
                    onClick = { importRules.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.set_rules_import)) }
                if (activeRules.hasPrevious) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = app.rulesRepository.restorePrevious()
                                notice = if (ok) {
                                    Notice(res.getString(R.string.set_rules_prev_ok_t), res.getString(R.string.set_rules_prev_ok))
                                } else {
                                    Notice(res.getString(R.string.set_rules_prev_ko_t), res.getString(R.string.set_rules_prev_ko))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.set_rules_prev)) }
                }
                if (activeRules.source == RulesRepository.Source.IMPORTED) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                app.rulesRepository.resetToBundled()
                                notice = Notice(res.getString(R.string.set_rules_orig_ok_t), res.getString(R.string.set_rules_orig_ok))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.set_rules_orig)) }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            val json = withContext(Dispatchers.IO) { app.rulesRepository.bundledJson() }
                            val intent = ShareHelper.shareText(context, "refusbot-regles-modele.json", "application/json", json, res.getString(R.string.set_rules_share_t))
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, res.getString(R.string.hist_no_app), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.set_rules_share)) }
            }
        }

        // --- Sauvegarde -----------------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_backup), icon = R.drawable.ic_download) {
                BodyText(stringResource(R.string.set_backup_note))
                OutlinedButton(onClick = { exportSettings.launch("refusbot-reglages.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_backup_export))
                }
                OutlinedButton(
                    onClick = { importSettings.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.set_backup_restore)) }
            }
        }

        // --- Données --------------------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_data), icon = R.drawable.ic_delete) {
                BodyText(stringResource(R.string.set_data_note))
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.set_data_delete)) }
            }
        }

        // --- À propos -------------------------------------------------------------------------
        item {
            SectionCard(title = stringResource(R.string.set_about), icon = R.drawable.ic_info) {
                BodyText(
                    stringResource(
                        R.string.set_about_version,
                        SystemStatus.appVersion(context),
                        activeRules.rules.version.toString(),
                        stringResource(activeRules.source.labelRes()),
                    ),
                )
                BodyText(stringResource(R.string.set_about_local))
            }
        }
    }

    notice?.let { n ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(n.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(n.text) } },
            confirmButton = { TextButton(onClick = { notice = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.set_delete_t)) },
            text = {
                Text(stringResource(R.string.set_delete))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        // Les réglages d'abord : plus aucune appli surveillée, donc plus aucune action.
                        app.settings.clearAll()
                        app.journal.clear()
                        app.rulesRepository.deleteAll()
                        withContext(Dispatchers.IO) {
                            app.diagnostics.deleteAll()
                            File(context.cacheDir, "exports").deleteRecursively()
                        }
                        notice = Notice(res.getString(R.string.set_deleted_t), res.getString(R.string.set_deleted))
                    }
                }) { Text(stringResource(R.string.set_delete_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.apps_cancel)) } },
        )
    }
}

/** Taille maximale d'un fichier importé (réglages ou règles). */
private const val MAX_IMPORT_BYTES = 600_000

/** Lit un fichier choisi par l'utilisateur (sélecteur du système), avec une limite de taille. */
private suspend fun readText(context: Context, res: Resources, uri: Uri): String = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException(res.getString(R.string.set_file_unreachable))
    input.use { stream ->
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) break
            if (out.size() + n > MAX_IMPORT_BYTES) throw IOException(res.getString(R.string.set_file_too_big))
            out.write(chunk, 0, n)
        }
        String(out.toByteArray(), Charsets.UTF_8)
    }
}

private suspend fun writeText(context: Context, res: Resources, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException(res.getString(R.string.set_place_unreachable))
    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/** Choix de langue de l'appli (LocaleManager, Android 13+). Compose actualise les ressources sans recréer l'Activity. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageChoices(context: Context) {
    val manager = remember { context.getSystemService(android.app.LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager.applicationLocales.toLanguageTags()) }
    listOf("" to R.string.lang_system, "fr" to R.string.lang_fr, "en" to R.string.lang_en).forEach { (tag, label) ->
        RadioRow(
            label = stringResource(label),
            selected = current == tag || (tag.isNotEmpty() && current.startsWith("$tag-")),
            onClick = {
                current = tag
                manager.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
            },
        )
    }
}
