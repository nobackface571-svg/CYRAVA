package fr.refusbot.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.refusbot.R
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.data.LogEntry
import fr.refusbot.refusBot
import fr.refusbot.service.RefusBotAccessibilityService
import fr.refusbot.tile.CaptureTileService
import fr.refusbot.tile.PauseTileService
import fr.refusbot.util.SystemStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Compteurs d'une période, calculés uniquement à partir de l'historique réel. */
data class PeriodStats(
    /** Interrupteurs observés désactivés avant la validation. */
    val verified: Int = 0,
    /** Bouton de refus cliqué, fenêtre fermée : enregistrement impossible à vérifier. */
    val unverified: Int = 0,
    val partial: Int = 0,
    val impossible: Int = 0,
    val observed: Int = 0,
    val interrupted: Int = 0,
) {
    val total: Int get() = verified + unverified + partial + impossible + observed + interrupted

    operator fun plus(status: OutcomeStatus): PeriodStats = when (status) {
        OutcomeStatus.OPTIONS_SUCCESS -> copy(verified = verified + 1)
        OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> copy(unverified = unverified + 1)
        OutcomeStatus.PARTIAL, OutcomeStatus.HANDED_OVER -> copy(partial = partial + 1)
        OutcomeStatus.IMPOSSIBLE, OutcomeStatus.INCOMPLETE_CAPTURE -> copy(impossible = impossible + 1)
        OutcomeStatus.OBSERVED -> copy(observed = observed + 1)
        OutcomeStatus.INTERRUPTED -> copy(interrupted = interrupted + 1)
    }
}

data class DayStats(val date: LocalDate, val stats: PeriodStats)

data class Dashboard(val today: PeriodStats, val week: PeriodStats, val all: PeriodStats, val days: List<DayStats>) {
    companion object {
        fun from(entries: List<LogEntry>, zone: ZoneId = ZoneId.systemDefault()): Dashboard {
            val todayDate = LocalDate.now(zone)
            val first = todayDate.minusDays(6)
            val perDay = HashMap<LocalDate, PeriodStats>()
            var all = PeriodStats()
            for (e in entries) {
                all += e.status
                val d = Instant.ofEpochMilli(e.time).atZone(zone).toLocalDate()
                if (!d.isBefore(first) && !d.isAfter(todayDate)) perDay[d] = (perDay[d] ?: PeriodStats()) + e.status
            }
            val days = (0L..6L).map { first.plusDays(it) }.map { DayStats(it, perDay[it] ?: PeriodStats()) }
            val week = days.fold(PeriodStats()) { acc, d ->
                PeriodStats(
                    acc.verified + d.stats.verified,
                    acc.unverified + d.stats.unverified,
                    acc.partial + d.stats.partial,
                    acc.impossible + d.stats.impossible,
                    acc.observed + d.stats.observed,
                    acc.interrupted + d.stats.interrupted,
                )
            }
            return Dashboard(perDay[todayDate] ?: PeriodStats(), week, all, days)
        }
    }
}

@Composable
fun HomeScreen(padding: PaddingValues, onOpenApps: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val app = context.refusBot
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    // Écran d'information « J'accepte » avant d'ouvrir les réglages d'accessibilité (Google Play).
    val openAccessibility = rememberAccessibilityOpener()
    val running by RefusBotAccessibilityService.running.collectAsState()
    val entries by app.journal.entries.collectAsState()
    val dashboard = remember(entries) { Dashboard.from(entries) }

    // Ces états système sont relus à chaque retour dans l'appli (après un passage dans les réglages).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val a11yEnabled = remember(refresh, running) { SystemStatus.isAccessibilityEnabled(context) }
    val batteryOk = remember(refresh) { SystemStatus.isIgnoringBatteryOptimizations(context) }
    val notifOk = remember(refresh) { SystemStatus.hasNotificationPermission(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val s = settings
    val count = s?.monitored?.size ?: 0
    val enabled = s?.enabled != false
    val state = when {
        s == null -> ProtectionState.LOADING
        !running && a11yEnabled -> ProtectionState.SERVICE_STOPPED
        !running -> ProtectionState.SERVICE_OFF
        !enabled -> ProtectionState.PAUSED
        count == 0 -> ProtectionState.NO_APPS
        else -> ProtectionState.ACTIVE
    }
    val (detail, actionLabel) = when (state) {
        ProtectionState.LOADING -> "" to stringResource(R.string.home_act_wait)
        ProtectionState.SERVICE_OFF -> stringResource(R.string.home_off_detail) to stringResource(R.string.home_off_action)
        ProtectionState.SERVICE_STOPPED -> stringResource(R.string.home_stopped_detail) to stringResource(R.string.home_stopped_action)
        ProtectionState.PAUSED -> stringResource(R.string.home_paused_detail) to stringResource(R.string.home_paused_action)
        ProtectionState.NO_APPS -> stringResource(R.string.home_no_apps_detail) to stringResource(R.string.home_no_apps_action)
        ProtectionState.ACTIVE -> stringResource(R.string.home_active_detail) to stringResource(R.string.home_active_action)
    }
    val primaryAction: () -> Unit = {
        when (state) {
            ProtectionState.LOADING -> Unit
            ProtectionState.SERVICE_OFF, ProtectionState.SERVICE_STOPPED -> openAccessibility()
            ProtectionState.PAUSED -> scope.launch { app.settings.setEnabled(true) }
            ProtectionState.NO_APPS -> onOpenApps()
            ProtectionState.ACTIVE -> scope.launch { app.settings.setEnabled(false) }
        }
    }
    val pending = buildList {
        if (!running) add("service")
        if (count == 0) add("applis")
        if (!batteryOk) add("batterie")
        if (!notifOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("notifications")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // --- En-tête de marque ------------------------------------------------------------
        item(key = "entete") {
            CyravaBrandHeader {
                StatusPill(
                    text = when (state) {
                        ProtectionState.ACTIVE -> stringResource(R.string.home_pill_active)
                        ProtectionState.PAUSED -> stringResource(R.string.home_pill_paused)
                        ProtectionState.NO_APPS -> stringResource(R.string.home_pill_setup)
                        ProtectionState.LOADING -> "…"
                        else -> stringResource(R.string.home_pill_inactive)
                    },
                    color = state.accent,
                )
            }
        }

        // --- Carte principale : bouton de protection --------------------------------------
        item(key = "protection") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = CyravaColors.Navy),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyravaColors.heroBrush)
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProtectionOrb(state = state, actionLabel = actionLabel, onClick = primaryAction)
                    HeroText(stringResource(state.titleRes), big = true)
                    if (detail.isNotEmpty()) HeroText(detail, muted = true)
                    if (s != null) {
                        HeroText(
                            when (count) {
                                0 -> stringResource(R.string.home_count_0)
                                1 -> stringResource(R.string.home_count_1)
                                else -> stringResource(R.string.home_count_n, count)
                            } + stringResource(R.string.home_profile, s.policy.text()) +
                                if (s.observedApps.isNotEmpty()) stringResource(R.string.home_observed, s.observedApps.size) else "",
                            muted = true,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = primaryAction,
                        enabled = state != ProtectionState.LOADING,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state == ProtectionState.ACTIVE) CyravaColors.Deep else state.accent,
                            contentColor = if (state == ProtectionState.ACTIVE) CyravaColors.Text else CyravaColors.Night,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                    if (enabled && s != null) {
                        val emergencyToast = stringResource(R.string.home_emergency_toast)
                        OutlinedButton(
                            onClick = {
                                val service = RefusBotAccessibilityService.instance
                                if (service != null) service.emergencyStop() else scope.launch { app.settings.setEnabled(false) }
                                Toast.makeText(context, emergencyToast, Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CyravaColors.Red.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyravaColors.Red),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painterResource(R.drawable.ic_stop), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_emergency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // --- Chiffres clés (historique réel uniquement) -----------------------------------
        item(key = "chiffres") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    // Refus réussis = vérifiés + faits avec le bouton du site ; le détail reste dans
                    // « Activité » (« N refus vérifié(s) · M refusé(s) avec le bouton du site »).
                    value = "${dashboard.all.verified + dashboard.all.unverified}",
                    label = stringResource(R.string.home_tile_refusals),
                    accent = CyravaColors.Green,
                    icon = R.drawable.ic_check,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${dashboard.week.total}",
                    label = stringResource(R.string.home_tile_week),
                    accent = MaterialTheme.colorScheme.primary,
                    icon = R.drawable.ic_history,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "$count",
                    label = stringResource(R.string.home_tile_apps),
                    accent = CyravaColors.Gold,
                    icon = R.drawable.ic_apps,
                    modifier = Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.home_no_apps_action), onClick = onOpenApps),
                )
            }
        }

        // --- À régler (seulement ce qui manque) -------------------------------------------
        item(key = "reglages-systeme") {
            SectionCard(
                title = if (pending.isEmpty()) stringResource(R.string.home_ready) else stringResource(R.string.home_todo, pending.size),
                icon = if (pending.isEmpty()) R.drawable.ic_check else R.drawable.ic_warning,
                tone = if (pending.isEmpty()) CardTone.OK else CardTone.NORMAL,
            ) {
                ChecklistRow(
                    title = stringResource(R.string.home_a11y_t),
                    detail = when {
                        running -> stringResource(R.string.home_a11y_on)
                        a11yEnabled -> stringResource(R.string.home_a11y_stopped)
                        else -> stringResource(R.string.home_a11y_off)
                    },
                    ok = running,
                    actionLabel = if (running) null else stringResource(R.string.home_a11y_action),
                    onAction = { openAccessibility() },
                )
                ChecklistRow(
                    title = stringResource(R.string.home_apps_t),
                    detail = if (count == 0) stringResource(R.string.home_apps_none) else stringResource(R.string.home_apps_n, count),
                    ok = count > 0,
                    actionLabel = if (count == 0) stringResource(R.string.home_apps_choose) else stringResource(R.string.home_apps_edit),
                    onAction = onOpenApps,
                )
                ChecklistRow(
                    title = stringResource(R.string.home_battery_t),
                    detail = if (batteryOk) stringResource(R.string.home_battery_ok) else stringResource(R.string.home_battery_ko),
                    ok = batteryOk,
                    actionLabel = if (batteryOk) null else stringResource(R.string.home_battery_action),
                    onAction = { SystemStatus.requestIgnoreBatteryOptimizations(context) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ChecklistRow(
                        title = stringResource(R.string.home_notif_t),
                        detail = if (notifOk) stringResource(R.string.home_notif_ok) else stringResource(R.string.home_notif_ko),
                        ok = notifOk,
                        actionLabel = if (notifOk) null else stringResource(R.string.home_notif_action),
                        onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
        }

        // --- Paramètres restreints (Android 13+) ------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !running) {
            item(key = "parametres-restreints") { RestrictedSettingsGuide() }
        }

        // --- Activité réelle ----------------------------------------------------------------
        item(key = "activite") {
            SectionCard(title = stringResource(R.string.home_activity), icon = R.drawable.ic_history) {
                if (dashboard.all.total == 0) {
                    BodyText(stringResource(R.string.home_activity_empty))
                } else {
                    StatLine(stringResource(R.string.home_today), dashboard.today)
                    StatLine(stringResource(R.string.home_7days), dashboard.week)
                    StatLine(stringResource(R.string.home_all), dashboard.all)
                    WeekChart(dashboard.days)
                }
            }
        }

        // --- Derniers événements ------------------------------------------------------------
        if (entries.isNotEmpty()) {
            item(key = "derniers") {
                SectionCard(title = stringResource(R.string.home_latest), icon = R.drawable.ic_info) {
                    entries.take(3).forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        HistoryRow(e, horizontalPadding = 0.dp)
                    }
                    TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.home_see_all)) }
                }
            }
        }

        // --- Conseils ------------------------------------------------------------------------
        item(key = "conseils") { TipsCard() }
    }
}

/** Tuiles de réglages rapides et avertissement anti-triche, repliables pour alléger l'accueil. */
@Composable
private fun TipsCard() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.home_tips), icon = R.drawable.ic_capture) {
        BodyText(stringResource(R.string.home_tips_anticheat))
        if (expanded) {
            BodyText(stringResource(R.string.home_tips_tiles))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context is Activity) {
                val captureName = stringResource(R.string.tile_capture_name)
                OutlinedButton(
                    onClick = { SystemStatus.requestAddTile(context, PauseTileService::class.java, "CYRAVA", R.drawable.ic_shield) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_tile_add_pause)) }
                OutlinedButton(
                    onClick = { SystemStatus.requestAddTile(context, CaptureTileService::class.java, captureName, R.drawable.ic_capture) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_tile_add_capture)) }
            } else {
                BodyText(stringResource(R.string.home_tiles_manual))
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) stringResource(R.string.home_tiles_hide) else stringResource(R.string.home_tiles_show))
        }
    }
}

@Composable
private fun StatLine(title: String, s: PeriodStats) {
    val parts = buildList {
        add(stringResource(R.string.stat_verified, s.verified))
        if (s.unverified > 0) add(stringResource(R.string.stat_unverified, s.unverified))
        if (s.partial > 0) add(stringResource(R.string.stat_partial, s.partial))
        if (s.impossible > 0) add(stringResource(R.string.stat_impossible, s.impossible))
        if (s.observed > 0) add(stringResource(R.string.stat_observed, s.observed))
        if (s.interrupted > 0) add(stringResource(R.string.stat_interrupted, s.interrupted))
    }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Histogramme empilé des 7 derniers jours (refus vérifiés, non vérifiables, partiels, impossibles). */
@Composable
private fun WeekChart(days: List<DayStats>) {
    val scheme = MaterialTheme.colorScheme
    val max = days.maxOf { it.stats.verified + it.stats.unverified + it.stats.partial + it.stats.impossible }.coerceAtLeast(1)
    val barMax: Dp = 80.dp
    val locale = currentLocale()
    val dayLines = days.map { d ->
        stringResource(
            R.string.chart_day,
            dayName(d.date, locale),
            d.stats.verified,
            d.stats.unverified,
            d.stats.partial,
            d.stats.impossible,
        )
    }
    val description = stringResource(R.string.chart_desc, dayLines.joinToString(" ; "))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(barMax + 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { d ->
                val total = d.stats.verified + d.stats.unverified + d.stats.partial + d.stats.impossible
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (total > 0) Text("$total", style = MaterialTheme.typography.labelSmall)
                    Bar(barMax * (d.stats.impossible.toFloat() / max), scheme.error)
                    Bar(barMax * (d.stats.partial.toFloat() / max), scheme.tertiary)
                    Bar(barMax * (d.stats.unverified.toFloat() / max), scheme.secondary)
                    Bar(barMax * (d.stats.verified.toFloat() / max), scheme.primary)
                    if (total == 0) Bar(2.dp, scheme.outlineVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            days.forEach { d -> Text(dayName(d.date, locale), style = MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(stringResource(R.string.legend_verified), scheme.primary)
            Legend(stringResource(R.string.legend_unverified), scheme.secondary)
            Legend(stringResource(R.string.legend_partial), scheme.tertiary)
            Legend(stringResource(R.string.legend_impossible), scheme.error)
        }
    }
}

@Composable
private fun Bar(height: Dp, color: Color) {
    if (height <= 0.dp) return
    Box(
        Modifier
            .width(18.dp)
            .height(height)
            .background(color, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun dayName(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

/** Langue réellement affichée par l'appli (celle choisie dans Réglages › Langue). */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales.get(0)

@Composable
private fun RestrictedSettingsGuide() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    SectionCard(title = stringResource(R.string.restricted_title), icon = R.drawable.ic_info, tone = CardTone.WARNING) {
        BodyText(stringResource(R.string.restricted_intro))
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                NumberedStep(1, stringResource(R.string.restricted_1))
                NumberedStep(2, stringResource(R.string.restricted_2))
                NumberedStep(3, stringResource(R.string.restricted_3))
                NumberedStep(4, stringResource(R.string.restricted_4))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        Button(onClick = { SystemStatus.openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.restricted_open))
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) stringResource(R.string.restricted_hide) else stringResource(R.string.restricted_show))
        }
    }
}
