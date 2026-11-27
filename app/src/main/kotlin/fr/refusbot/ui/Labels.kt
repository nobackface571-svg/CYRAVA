package fr.refusbot.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.refusbot.R
import fr.refusbot.core.Compatibility
import fr.refusbot.core.Confidence
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.Policy
import fr.refusbot.core.ResultCategory
import fr.refusbot.data.LogEntry
import fr.refusbot.data.NotifyLevel
import fr.refusbot.data.RulesRepository
import fr.refusbot.data.ThemeMode

/**
 * Libellés traduits (values / values-en) des notions du moteur. Le moteur (`core/`) garde ses
 * libellés français pour les journaux et les tests ; l'interface passe par ces fonctions.
 */
@StringRes
fun ResultCategory.labelRes(): Int = when (this) {
    ResultCategory.VERIFIED -> R.string.cat_verified
    ResultCategory.UNVERIFIED -> R.string.cat_unverified
    ResultCategory.INCOMPLETE -> R.string.cat_incomplete
    ResultCategory.FAILED -> R.string.cat_failed
    ResultCategory.NO_ACTION -> R.string.cat_no_action
}

@Composable
fun ResultCategory.text(): String = stringResource(labelRes())

@StringRes
fun Compatibility.labelRes(): Int = when (this) {
    Compatibility.COMPATIBLE -> R.string.compat_compatible
    Compatibility.REFUS_CLIQUE -> R.string.compat_clicked
    Compatibility.PARTIELLE -> R.string.compat_partial
    Compatibility.A_VERIFIER -> R.string.compat_check
    Compatibility.NON_PRISE_EN_CHARGE -> R.string.compat_unsupported
    Compatibility.OBSERVEE -> R.string.compat_observed
    Compatibility.INCONNUE -> R.string.compat_unknown
}

@Composable
fun Compatibility.text(): String = stringResource(labelRes())

@StringRes
fun Policy.labelRes(): Int = when (this) {
    Policy.STRICT -> R.string.policy_strict
    Policy.PRUDENT -> R.string.policy_prudent
    Policy.OBSERVATION -> R.string.policy_observation
}

@Composable
fun Policy.text(): String = stringResource(labelRes())

@StringRes
fun Compatibility.detailRes(): Int = when (this) {
    Compatibility.COMPATIBLE -> R.string.compat_d_compatible
    Compatibility.REFUS_CLIQUE -> R.string.compat_d_clicked
    Compatibility.PARTIELLE -> R.string.compat_d_partial
    Compatibility.A_VERIFIER -> R.string.compat_d_check
    Compatibility.NON_PRISE_EN_CHARGE -> R.string.compat_d_unsupported
    Compatibility.OBSERVEE -> R.string.compat_d_observed
    Compatibility.INCONNUE -> R.string.compat_d_unknown
}

@StringRes
fun OutcomeStatus.labelRes(): Int = when (this) {
    OutcomeStatus.DIRECT_SUCCESS -> R.string.st_direct
    OutcomeStatus.OPTIONS_SUCCESS -> R.string.st_options
    OutcomeStatus.UNVERIFIED -> R.string.st_unverified
    OutcomeStatus.PARTIAL -> R.string.st_partial
    OutcomeStatus.INCOMPLETE_CAPTURE -> R.string.st_capture
    OutcomeStatus.IMPOSSIBLE -> R.string.st_impossible
    OutcomeStatus.INTERRUPTED -> R.string.st_interrupted
    OutcomeStatus.OBSERVED -> R.string.st_observed
    OutcomeStatus.HANDED_OVER -> R.string.st_handed
}

@Composable
fun OutcomeStatus.text(): String = stringResource(labelRes())

@StringRes
fun Confidence.labelRes(): Int = when (this) {
    Confidence.CERTAIN -> R.string.conf_certain
    Confidence.PROBABLE -> R.string.conf_probable
    Confidence.INSUFFISANT -> R.string.conf_low
}

/**
 * Résumé traduit d'une entrée d'historique (écran et notifications). Le motif (`reason`) vient du
 * moteur et reste en français, comme les exports JSON/CSV.
 */
fun LogEntry.summaryText(res: Resources): String {
    val label = res.getString(status.labelRes())
    return when (status) {
        OutcomeStatus.DIRECT_SUCCESS -> label
        OutcomeStatus.UNVERIFIED -> res.getString(R.string.sum_unverified, label)
        OutcomeStatus.OPTIONS_SUCCESS -> when (togglesOff) {
            0 -> res.getString(R.string.sum_toggles_0, label)
            1 -> res.getString(R.string.sum_toggles_1, label)
            else -> res.getString(R.string.sum_toggles_n, label, togglesOff)
        }
        else -> reason?.let { res.getString(R.string.sum_reason, label, it) } ?: label
    }
}

/** Date et heure dans la langue de l'appli (« 20/11/2026 à 22:15 » / « 20/11/2026 at 22:15 »). */
@Composable
fun formatDateTime(millis: Long): String = formatDateTime(stringResource(R.string.date_time_pattern), millis)

fun formatDateTime(res: Resources, millis: Long): String = formatDateTime(res.getString(R.string.date_time_pattern), millis)

private fun formatDateTime(pattern: String, millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern(pattern)
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

@StringRes
fun Policy.detailRes(): Int = when (this) {
    Policy.STRICT -> R.string.policy_d_strict
    Policy.PRUDENT -> R.string.policy_d_prudent
    Policy.OBSERVATION -> R.string.policy_d_observation
}

@StringRes
fun NotifyLevel.labelRes(): Int = when (this) {
    NotifyLevel.TOUTES -> R.string.notif_all
    NotifyLevel.ECHECS -> R.string.notif_fail
    NotifyLevel.INTERRUPTIONS -> R.string.notif_int
    NotifyLevel.AUCUNE -> R.string.notif_none
}

@StringRes
fun NotifyLevel.detailRes(): Int = when (this) {
    NotifyLevel.TOUTES -> R.string.notif_all_d
    NotifyLevel.ECHECS -> R.string.notif_fail_d
    NotifyLevel.INTERRUPTIONS -> R.string.notif_int_d
    NotifyLevel.AUCUNE -> R.string.notif_none_d
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEME -> R.string.theme_system
    ThemeMode.CLAIR -> R.string.theme_light
    ThemeMode.SOMBRE -> R.string.theme_dark
}

@StringRes
fun RulesRepository.Source.labelRes(): Int = when (this) {
    RulesRepository.Source.BUNDLED -> R.string.rules_bundled
    RulesRepository.Source.IMPORTED -> R.string.rules_imported
}

/** Titre et description traduits d'un scénario du laboratoire (le moteur garde les siens). */
fun labScenarioRes(id: String): Pair<Int, Int>? = when (id) {
    "bandeau" -> R.string.lab_sc_bandeau_t to R.string.lab_sc_bandeau
    "ump" -> R.string.lab_sc_ump_t to R.string.lab_sc_ump
    "accepter-seul" -> R.string.lab_sc_accept_t to R.string.lab_sc_accept
    "accordeons" -> R.string.lab_sc_accordion_t to R.string.lab_sc_accordion
    "piege" -> R.string.lab_sc_trap_t to R.string.lab_sc_trap
    "allemand" -> R.string.lab_sc_german_t to R.string.lab_sc_german
    "aria-unknown" -> R.string.lab_sc_aria_t to R.string.lab_sc_aria
    "capture-tronquee" -> R.string.lab_sc_trunc_t to R.string.lab_sc_trunc
    else -> null
}
